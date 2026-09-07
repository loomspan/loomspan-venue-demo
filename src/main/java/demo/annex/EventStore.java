package demo.annex;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import static org.springframework.http.HttpStatus.*;
import static demo.annex.Contracts.*;
import java.time.LocalDateTime;
import java.util.*;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
@Transactional
public class EventStore
{
    private final EventRepository events;
    private final AssessmentRepository assessments;
    private final ProposalRepository proposals;
    private final RoomRepository rooms;
    private final BookingRepository bookings;
    private final VenueService venue;
    private final WorkshopService workshop;
    private final ResourceRepository resources;
    private final ReservationRepository reservations;
    private final ObjectMapper mapper;
    private final CreditRepository credits;

    public EventStore(EventRepository events, AssessmentRepository assessments, ProposalRepository proposals, RoomRepository rooms, BookingRepository bookings, VenueService venue, WorkshopService workshop, ResourceRepository resources, ReservationRepository reservations, ObjectMapper mapper, CreditRepository credits)
    {
        this.events = events;
        this.assessments = assessments;
        this.proposals = proposals;
        this.rooms = rooms;
        this.bookings = bookings;
        this.venue = venue;
        this.workshop = workshop;
        this.resources = resources;
        this.reservations = reservations;
        this.mapper = mapper;
        this.credits = credits;
    }

    static String id()
    {
        return UUID.randomUUID().toString();
    }

    public EventView create(CreateEvent input)
    {
        EventRequest e = newRequest(input);
        e.seriesId = e.id;
        e.revisionNumber = 1;
        events.save(e);
        return view(e);
    }

    private EventRequest newRequest(CreateEvent input)
    {
        String type = input.eventType() == null ? "MEETING" : input.eventType();
        if (!Set.of("MEETING", "WORKSHOP").contains(type))
            throw new ResponseStatusException(BAD_REQUEST, "Unknown event type");
        if ("WORKSHOP".equals(type))
        {
            if (input.attendees() < 2 || input.attendees() % 2 != 0)
                throw new ResponseStatusException(BAD_REQUEST, "The workshop requires two equal breakout groups; supply an even attendance of at least two.");
            if (input.standardLunches() < 0 || input.veganLunches() < 0 || input.standardLunches() + input.veganLunches() != input.attendees())
                throw new ResponseStatusException(BAD_REQUEST, "Standard and vegan lunch counts must add up to attendance.");
            if (input.livestream() && !input.presentation())
                throw new ResponseStatusException(BAD_REQUEST, "Livestream requires a presentation kit.");
        }
        else if (input.standardLunches() != 0 || input.veganLunches() != 0 || input.presentation() || input.livestream())
            throw new ResponseStatusException(BAD_REQUEST, "Use a workshop request for catering or technical services.");
        EventRequest e = new EventRequest();
        e.id = id();
        e.title = input.title().trim();
        e.eventDate = input.eventDate();
        e.attendees = input.attendees();
        e.budgetCents = input.budgetCents();
        e.createdAt = LocalDateTime.now();
        e.eventType = type;
        e.standardLunches = input.standardLunches();
        e.veganLunches = input.veganLunches();
        e.presentation = input.presentation();
        e.livestream = input.livestream();
        return e;
    }

    public EventView revise(String eventId, CreateEvent input)
    {
        EventRequest previous = lockCurrent(eventId);
        if (!bookings.findForSeries(previous.seriesId).isEmpty())
            throw new ResponseStatusException(CONFLICT, "Booked events cannot be revised in this demo.");
        if (assessments.findByEventIdOrderByCreatedAtDesc(eventId).stream().anyMatch(a -> a.status.equals("RUNNING")))
            throw new ResponseStatusException(CONFLICT, "Wait for the running assessment before revising requirements.");
        EventRequest next = newRequest(input);
        if (!previous.eventType.equals(next.eventType))
            throw new ResponseStatusException(BAD_REQUEST, "A revision must retain the event type. Create a new event to change it.");
        next.seriesId = previous.seriesId;
        next.revisionNumber = previous.revisionNumber + 1;
        events.saveAndFlush(next);
        return view(next);
    }

    // Revision creation, assessment admission and booking serialize on the original request.
    // Each revision's requirements stay immutable, including while model tools read them.
    private EventRequest lockCurrent(String eventId)
    {
        EventRequest requested = events.findById(eventId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Event not found"));
        events.lockById(requested.seriesId).orElseThrow();
        if (!events.findBySeriesIdOrderByRevisionNumberDesc(requested.seriesId).getFirst().id.equals(eventId))
            throw new ResponseStatusException(CONFLICT, "This requirement revision is outdated. Open the current revision.");
        return requested;
    }

    public AssessmentView begin(String eventId)
    {
        lockCurrent(eventId);
        if (!bookings.findForEvent(eventId).isEmpty())
            throw new ResponseStatusException(CONFLICT, "This event is already booked");
        if (assessments.findByEventIdOrderByCreatedAtDesc(eventId).stream().anyMatch(a -> a.status.equals("RUNNING")))
            throw new ResponseStatusException(CONFLICT, "An assessment is already running for this event");
        Assessment a = new Assessment();
        a.id = id();
        a.eventId = eventId;
        a.status = "RUNNING";
        a.summary = "Assessment in progress";
        a.questions = "";
        a.createdAt = LocalDateTime.now();
        assessments.save(a);
        return view(a);
    }

    public AssessmentView complete(String assessmentId, ModelResult result, String sessionId)
    {
        Assessment initial = assessments.findById(assessmentId).orElseThrow();
        lockCurrent(initial.eventId);
        Assessment a = assessments.lockById(assessmentId).orElseThrow();
        if (!a.status.equals("RUNNING"))
            throw new IllegalStateException("Assessment is no longer running");
        if (result.summary() == null || result.summary().isBlank() || result.summary().length() > 4000 || result.openQuestions() == null)
            throw new IllegalArgumentException("Invalid assessment output");
        String questions = String.join("\n", result.openQuestions());
        if (questions.length() > 4000)
            throw new IllegalArgumentException("Too many open questions");
        EventRequest event = events.findById(a.eventId).orElseThrow();
        if (event.workshop())
        {
            completeWorkshop(a, result);
        }
        else if (result.breakoutRoomId() != null || result.lunchPackageId() != null)
        {
            throw new IllegalArgumentException("Room-only output cannot include workshop allocations");
        }
        else if (result.roomId() == null)
        {
            if (result.totalCents() != null)
                throw new IllegalArgumentException("A price requires a selected room");
            // The catalog is bounded to three rooms, so no-option results can be checked exhaustively here.
            boolean feasible = venue.rooms().stream().anyMatch(r -> venue.check(a.eventId, r.id()).valid());
            if (feasible)
                throw new IllegalArgumentException("The model missed an available compliant room");
            a.status = "NO_OPTION";
        }
        else
        {
            var quote = venue.check(a.eventId, result.roomId());
            if (!quote.valid() || result.totalCents() == null || result.totalCents() != quote.totalCents())
                throw new IllegalArgumentException("Unvalidated or incorrect model quote");
            if (!result.openQuestions().isEmpty())
                throw new IllegalArgumentException("A bookable option cannot have unresolved requirements");
            int minimum = venue.rooms().stream().map(r -> venue.check(a.eventId, r.id())).filter(CheckResult::valid).mapToInt(CheckResult::totalCents).min().orElseThrow();
            if (quote.totalCents() != minimum)
                throw new IllegalArgumentException("The selected room violates the lowest-cost preference");
            Proposal p = new Proposal();
            p.id = id();
            p.assessmentId = a.id;
            p.roomId = quote.roomId();
            p.roomVersion = quote.roomVersion();
            p.totalCents = quote.totalCents();
            proposals.save(p);
            a.status = "READY";
        }
        a.summary = result.summary();
        a.questions = questions;
        a.sessionId = sessionId;
        assessments.saveAndFlush(a);
        return view(a);
    }

    private void completeWorkshop(Assessment a, ModelResult result)
    {
        var cheapest = workshop.cheapest(a.eventId);
        if (result.roomId() == null)
        {
            if (result.totalCents() != null || result.breakoutRoomId() != null || result.lunchPackageId() != null || cheapest.isPresent())
                throw new IllegalArgumentException("Unsupported no-option result");
            a.status = "NO_OPTION";
            return;
        }
        var quote = workshop.quote(a.eventId, result.roomId(), result.breakoutRoomId(), result.lunchPackageId());
        if (!quote.valid() || result.totalCents() == null || quote.totalCents() != result.totalCents() || !result.openQuestions().isEmpty()
                || cheapest.isEmpty() || quote.totalCents() != cheapest.get().totalCents())
            throw new IllegalArgumentException("Invalid or non-minimal workshop quote");
        Proposal p = new Proposal();
        p.id = id();
        p.assessmentId = a.id;
        p.roomId = result.roomId();
        p.breakoutRoomId = result.breakoutRoomId();
        p.lunchPackageId = result.lunchPackageId();
        p.roomVersion = rooms.findById(p.roomId).orElseThrow().version;
        p.totalCents = quote.totalCents();
        p.allocationJson = mapper.writeValueAsString(quote.allocations());
        proposals.save(p);
        a.status = "READY";
    }

    public AssessmentView fail(String id, String message, String sessionId)
    {
        Assessment a = assessments.findById(id).orElseThrow();
        a.status = "FAILED";
        a.summary = message;
        a.questions = "";
        a.sessionId = sessionId;
        assessments.save(a);
        return view(a);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedAssessments()
    {
        for (Assessment a : assessments.findByStatus("RUNNING"))
        {
            a.status = "FAILED";
            a.summary = "The application stopped during assessment. Run a fresh assessment.";
        }
    }

    @Transactional(readOnly = true)
    public List<EventView> list()
    {
        return events.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public AssessmentView assessment(String id)
    {
        return view(assessments.findById(id).orElseThrow());
    }

    public BookingView accept(String proposalId)
    {
        Proposal p = proposals.findById(proposalId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Proposal not found"));
        Assessment initial = assessments.findById(p.assessmentId).orElseThrow();
        EventRequest event = lockCurrent(initial.eventId);
        Assessment a = assessments.lockById(p.assessmentId).orElseThrow();
        var existing = bookings.findByProposalId(p.id);
        if (existing.isPresent())
            return venue.view(existing.get());
        if (!bookings.findForEvent(event.id).isEmpty())
            throw new ResponseStatusException(CONFLICT, "This event already has a booking from another proposal");
        if (!a.status.equals("READY"))
            throw new ResponseStatusException(CONFLICT, "Proposal is no longer ready for acceptance");
        List<Allocation> allocation;
        if (event.workshop())
        {
            var quoted = allocations(p);
            // All booking paths lock rooms first, then services, in stable ID order.
            quoted.stream().filter(line -> "ROOM".equals(line.kind())).map(Allocation::resourceId).sorted().forEach(id -> rooms.lockById(id).orElseThrow());
            quoted.stream().filter(line -> !"ROOM".equals(line.kind())).map(Allocation::resourceId).sorted().forEach(id -> resources.lockById(id).orElseThrow());
            var current = workshop.quote(event.id, p.roomId, p.breakoutRoomId, p.lunchPackageId);
            if (!current.valid() || current.totalCents() != p.totalCents || !current.allocations().equals(quoted))
                throw changed();
            allocation = current.allocations();
        }
        else
        {
            Room room = rooms.lockById(p.roomId).orElseThrow();
            var check = venue.check(event, room);
            if (room.version != p.roomVersion || room.priceCents != p.totalCents || !check.valid())
                throw changed();
            allocation = List.of(new Allocation(room.id, room.name, "ROOM", 1, 1, room.priceCents, room.priceCents, room.version, event.startsAt(), event.endsAt(), room.id));
        }
        Booking b = new Booking();
        b.id = id();
        b.proposalId = p.id;
        b.roomId = p.roomId;
        b.title = event.title;
        b.startsAt = event.startsAt();
        b.endsAt = event.endsAt();
        b.totalCents = p.totalCents - credits.findById(p.id).map(c -> c.amountCents).orElse(0);
        bookings.saveAndFlush(b);
        for (var line : allocation)
        {
            ResourceReservation r = new ResourceReservation();
            r.id = id();
            r.bookingId = b.id;
            r.resourceId = line.resourceId();
            r.quantity = line.quantity();
            r.startsAt = line.startsAt();
            r.endsAt = line.endsAt();
            reservations.save(r);
        }
        reservations.flush();
        a.status = "BOOKED";
        return venue.view(b);
    }

    private ResponseStatusException changed()
    {
        return new ResponseStatusException(CONFLICT, "Price or availability changed. No booking was created; run a fresh assessment.");
    }

    @RolesAllowed("MANAGER")
    public CreditView applyRoomCredit(String proposalId)
    {
        Proposal p=proposals.findById(proposalId).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Proposal not found"));
        Assessment initial=assessments.findById(p.assessmentId).orElseThrow();
        EventRequest event=lockCurrent(initial.eventId);
        Assessment a=assessments.lockById(p.assessmentId).orElseThrow();
        if(!a.status.equals("READY")||!bookings.findForSeries(event.seriesId).isEmpty())
            throw new ResponseStatusException(CONFLICT,"Only a current unbooked proposal can receive a credit.");
        int roomSubtotal=p.allocationJson==null?p.totalCents:allocations(p).stream().filter(line->line.kind().equals("ROOM")).mapToInt(Allocation::totalCents).sum();
        if(roomSubtotal<50000) throw new ResponseStatusException(CONFLICT,"The room subtotal must be at least $500 for this credit.");
        RoomCredit credit=credits.findById(p.id).orElseGet(()->{
            RoomCredit c=new RoomCredit();c.proposalId=p.id;c.amountCents=10000;c.approvedBy=SecurityContextHolder.getContext().getAuthentication().getName();c.approvedAt=LocalDateTime.now();return credits.saveAndFlush(c);
        });
        return creditView(credit);
    }

    private CreditView creditView(RoomCredit c) {return new CreditView(c.amountCents,c.approvedBy,c.approvedAt);}

    private List<Allocation> allocations(Proposal p)
    {
        return p.allocationJson == null ? List.of() : Arrays.asList(mapper.readValue(p.allocationJson, Allocation[].class));
    }

    @Transactional(readOnly = true)
    public String eventType(String eventId)
    {
        return events.findById(eventId).orElseThrow().eventType;
    }

    private EventView view(EventRequest e)
    {
        return new EventView(e.id, e.title, e.eventDate, e.attendees, e.budgetCents, assessments.findByEventIdOrderByCreatedAtDesc(e.id).stream().map(this::view).toList(), e.eventType, e.standardLunches, e.veganLunches, e.presentation, e.livestream,
            e.seriesId, e.revisionNumber, events.findBySeriesIdOrderByRevisionNumberDesc(e.seriesId).getFirst().id.equals(e.id));
    }

    private AssessmentView view(Assessment a)
    {
        ProposalView p = proposals.findByAssessmentId(a.id).map(proposal -> new ProposalView(proposal.id, proposal.roomId, rooms.findById(proposal.roomId).orElseThrow().name, proposal.totalCents, proposal.roomVersion, bookings.findByProposalId(proposal.id).map(venue::view).orElse(null), allocations(proposal), credits.findById(proposal.id).map(this::creditView).orElse(null), proposal.totalCents-credits.findById(proposal.id).map(c->c.amountCents).orElse(0))).orElse(null);
        return new AssessmentView(a.id, a.status, a.summary, a.questions.isBlank() ? List.of() : Arrays.asList(a.questions.split("\n")), a.sessionId, a.createdAt, p);
    }
}
