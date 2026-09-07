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

@Service
@Transactional
public class EventStore {
    private final EventRepository events;
    private final AssessmentRepository assessments;
    private final ProposalRepository proposals;
    private final RoomRepository rooms;
    private final BookingRepository bookings;
    private final VenueService venue;
    public EventStore(EventRepository events,AssessmentRepository assessments,ProposalRepository proposals,RoomRepository rooms,BookingRepository bookings,VenueService venue) {
        this.events=events;this.assessments=assessments;this.proposals=proposals;this.rooms=rooms;this.bookings=bookings;this.venue=venue;
    }
    static String id() { return UUID.randomUUID().toString(); }
    public EventView create(CreateEvent input) {
        EventRequest e=new EventRequest(); e.id=id();e.title=input.title().trim();e.eventDate=input.eventDate();e.attendees=input.attendees();e.budgetCents=input.budgetCents();e.createdAt=LocalDateTime.now();
        events.save(e); return view(e);
    }
    public AssessmentView begin(String eventId) {
        events.lockById(eventId).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Event not found"));
        if(!bookings.findForEvent(eventId).isEmpty()) throw new ResponseStatusException(CONFLICT,"This event is already booked");
        if(assessments.findByEventIdOrderByCreatedAtDesc(eventId).stream().anyMatch(a->a.status.equals("RUNNING"))) throw new ResponseStatusException(CONFLICT,"An assessment is already running for this event");
        Assessment a=new Assessment();a.id=id();a.eventId=eventId;a.status="RUNNING";a.summary="Assessment in progress";a.questions="";a.createdAt=LocalDateTime.now();
        assessments.save(a);return view(a);
    }
    public AssessmentView complete(String assessmentId,ModelResult result,String sessionId) {
        Assessment a=assessments.lockById(assessmentId).orElseThrow();
        if(!a.status.equals("RUNNING")) throw new IllegalStateException("Assessment is no longer running");
        if(result.summary()==null||result.summary().isBlank()||result.summary().length()>4000||result.openQuestions()==null)
            throw new IllegalArgumentException("Invalid assessment output");
        String questions=String.join("\n",result.openQuestions());
        if(questions.length()>4000) throw new IllegalArgumentException("Too many open questions");
        if(result.roomId()==null) {
            if(result.totalCents()!=null) throw new IllegalArgumentException("A price requires a selected room");
            // The catalog is bounded to three rooms, so no-option results can be checked exhaustively here.
            boolean feasible=venue.rooms().stream().anyMatch(r->venue.check(a.eventId,r.id()).valid());
            if(feasible) throw new IllegalArgumentException("The model missed an available compliant room");
            a.status="NO_OPTION";
        } else {
            var quote=venue.check(a.eventId,result.roomId());
            if(!quote.valid()||result.totalCents()==null||result.totalCents()!=quote.totalCents()) throw new IllegalArgumentException("Unvalidated or incorrect model quote");
            if(!result.openQuestions().isEmpty()) throw new IllegalArgumentException("A bookable option cannot have unresolved requirements");
            int minimum=venue.rooms().stream().map(r->venue.check(a.eventId,r.id())).filter(CheckResult::valid).mapToInt(CheckResult::totalCents).min().orElseThrow();
            if(quote.totalCents()!=minimum) throw new IllegalArgumentException("The selected room violates the lowest-cost preference");
            Proposal p=new Proposal();p.id=id();p.assessmentId=a.id;p.roomId=quote.roomId();p.roomVersion=quote.roomVersion();p.totalCents=quote.totalCents();proposals.save(p);
            a.status="READY";
        }
        a.summary=result.summary();a.questions=questions;a.sessionId=sessionId;assessments.saveAndFlush(a);return view(a);
    }
    public AssessmentView fail(String id,String message) {
        Assessment a=assessments.findById(id).orElseThrow();a.status="FAILED";a.summary=message;a.questions="";assessments.save(a);return view(a);
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedAssessments() {
        for(Assessment a:assessments.findByStatus("RUNNING")){a.status="FAILED";a.summary="The application stopped during assessment. Run a fresh assessment.";}
    }
    @Transactional(readOnly=true)
    public List<EventView> list() { return events.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList(); }
    @Transactional(readOnly=true)
    public AssessmentView assessment(String id) {return view(assessments.findById(id).orElseThrow());}
    public BookingView accept(String proposalId) {
        Proposal p=proposals.findById(proposalId).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Proposal not found"));
        Assessment initial=assessments.findById(p.assessmentId).orElseThrow();
        EventRequest event=events.lockById(initial.eventId).orElseThrow();
        Assessment a=assessments.lockById(p.assessmentId).orElseThrow();
        var existing=bookings.findByProposalId(p.id);
        if(existing.isPresent()) return VenueService.view(existing.get());
        if(!bookings.findForEvent(event.id).isEmpty()) throw new ResponseStatusException(CONFLICT,"This event already has a booking from another proposal");
        if(!a.status.equals("READY")) throw new ResponseStatusException(CONFLICT,"Proposal is no longer ready for acceptance");
        Room room=rooms.lockById(p.roomId).orElseThrow();
        var check=venue.check(event,room);
        if(room.version!=p.roomVersion||room.priceCents!=p.totalCents||!check.valid()) throw new ResponseStatusException(CONFLICT,"Price or availability changed. No booking was created; run a fresh assessment.");
        Booking b=new Booking();b.id=id();b.proposalId=p.id;b.roomId=p.roomId;b.title=event.title;b.startsAt=event.startsAt();b.endsAt=event.endsAt();b.totalCents=p.totalCents;
        bookings.saveAndFlush(b);a.status="BOOKED";return VenueService.view(b);
    }
    private EventView view(EventRequest e) {return new EventView(e.id,e.title,e.eventDate,e.attendees,e.budgetCents,assessments.findByEventIdOrderByCreatedAtDesc(e.id).stream().map(this::view).toList());}
    private AssessmentView view(Assessment a) {
        ProposalView p=proposals.findByAssessmentId(a.id).map(proposal->new ProposalView(proposal.id,proposal.roomId,rooms.findById(proposal.roomId).orElseThrow().name,proposal.totalCents,proposal.roomVersion,bookings.findByProposalId(proposal.id).map(VenueService::view).orElse(null))).orElse(null);
        return new AssessmentView(a.id,a.status,a.summary,a.questions.isBlank()?List.of():Arrays.asList(a.questions.split("\n")),a.sessionId,a.createdAt,p);
    }
}
