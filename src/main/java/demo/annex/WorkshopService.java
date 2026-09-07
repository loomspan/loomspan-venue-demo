package demo.annex;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static demo.annex.Contracts.*;

/** Deterministic rules for the deliberately bounded two-room workshop. */
@Service
@Transactional(readOnly = true)
public class WorkshopService
{
    private final VenueService venue;
    private final RoomRepository rooms;
    private final ResourceRepository resources;
    private final ReservationRepository reservations;

    public WorkshopService(VenueService venue, RoomRepository rooms, ResourceRepository resources, ReservationRepository reservations)
    {
        this.venue = venue;
        this.rooms = rooms;
        this.resources = resources;
        this.reservations = reservations;
    }

    public List<SpaceCandidate> space(String eventId)
    {
        EventRequest e = venue.event(eventId);
        if (!e.workshop())
            throw new IllegalArgumentException("Workshop request required");
        List<SpaceCandidate> candidates = new ArrayList<>();
        for (Room plenary : rooms.findAllByOrderByPriceCentsAsc())
            for (Room breakout : rooms.findAllByOrderByPriceCentsAsc())
            {
                if (plenary.id.equals(breakout.id))
                    continue;
                List<String> violations = new ArrayList<>();
                if (plenary.capacity < e.attendees)
                    violations.add("Plenary room cannot seat all attendees");
                if (breakout.capacity < e.attendees / 2)
                    violations.add("Second room cannot seat one equal breakout group");
                for (Room room : List.of(plenary, breakout))
                    if (reservations.reserved(room.id, e.startsAt(), e.endsAt()) > 0)
                        violations.add(room.name + " is unavailable");
                int total = plenary.priceCents + breakout.priceCents;
                if (total > e.budgetCents)
                    violations.add("Rooms alone exceed budget");
                candidates.add(new SpaceCandidate(plenary.id, breakout.id, total, violations.isEmpty(), List.copyOf(violations)));
            }
        return candidates.stream().sorted(Comparator.comparingInt(SpaceCandidate::totalCents).thenComparing(SpaceCandidate::roomId)).toList();
    }

    public Map<String, ServiceCheck> catering(String eventId)
    {
        EventRequest e = venue.event(eventId);
        Map<String, ServiceCheck> checks = new LinkedHashMap<>();
        resources.findAll().stream().filter(r -> "LUNCH".equals(r.kind) || "DRINKS".equals(r.kind))
                .sorted(Comparator.comparingInt(r -> r.priceCents)).forEach(r -> checks.put(r.id, catering(e, r.id)));
        return checks;
    }

    ServiceCheck catering(EventRequest e, String packageId)
    {
        List<String> violations = new ArrayList<>();
        List<Allocation> lines = new ArrayList<>();
        VenueResource food = resources.findById(packageId == null ? "" : packageId).orElse(null);
        if (food == null || !"LUNCH".equals(food.kind))
            violations.add("Select a lunch package; drinks do not satisfy lunch");
        else
        {
            if (e.standardLunches < 0 || e.veganLunches < 0 || e.standardLunches + e.veganLunches != e.attendees)
                violations.add("Meal counts must equal attendance");
            if (e.veganLunches > 0 && !food.veganSupported)
                violations.add("Package does not support confirmed vegan portions");
            if (e.attendees > 100)
                violations.add("Lunch delivery limit is 100 people");
            addResource(e, food.id, "LUNCH", e.attendees, e.attendees, e.eventDate.atTime(13, 30), null, lines, violations);
        }
        addResource(e, "STAFF-SAM", "CATERING_ATTENDANT", 1, 1, e.eventDate.atTime(13, 30), null, lines, violations);
        return serviceCheck(lines, violations);
    }

    public ServiceCheck technical(String eventId, String roomId)
    {
        EventRequest e = venue.event(eventId);
        List<String> violations = new ArrayList<>();
        List<Allocation> lines = new ArrayList<>();
        Room room = rooms.findById(roomId == null ? "" : roomId).orElse(null);
        if (room == null || room.capacity < e.attendees)
            violations.add("A suitable plenary assignment is required");
        if (e.livestream && !e.presentation)
            violations.add("Livestream requires presentation equipment");
        if (e.presentation)
            addResource(e, "EQ-PRESENT", "EQUIPMENT", 1, 1, e.endsAt(), roomId, lines, violations);
        if (e.livestream)
        {
            addResource(e, "EQ-STREAM", "EQUIPMENT", 1, 1, e.endsAt(), roomId, lines, violations);
            addResource(e, "STAFF-LEE", "STREAM_OPERATOR", 1, 6, e.endsAt(), roomId, lines, violations);
        }
        return serviceCheck(lines, violations);
    }

    private ServiceCheck serviceCheck(List<Allocation> lines, List<String> violations)
    {
        return new ServiceCheck(violations.isEmpty(), lines.stream().mapToInt(Allocation::totalCents).sum(), List.copyOf(lines), List.copyOf(violations));
    }

    private void addResource(EventRequest e, String id, String kind, int quantity, int units, LocalDateTime end, String roomId, List<Allocation> lines, List<String> violations)
    {
        VenueResource r = resources.findById(id).orElse(null);
        if (r == null)
        {
            violations.add("Missing resource " + id);
            return;
        }
        if (!kind.equals(r.kind))
            violations.add(r.name + " lacks the required resource type or qualification");
        if (e.startsAt().toLocalTime().isBefore(r.availableFrom) || end.toLocalTime().isAfter(r.availableUntil))
            violations.add(r.name + " is outside working hours");
        if (quantity + reservations.reserved(id, e.startsAt(), end) > r.stock)
            violations.add(r.name + " has insufficient availability");
        lines.add(new Allocation(id, r.name, r.kind, quantity, units, r.priceCents, units * r.priceCents, r.version, e.startsAt(), end, roomId));
    }

    public WorkshopQuote quote(String eventId, String roomId, String breakoutRoomId, String packageId)
    {
        EventRequest e = venue.event(eventId);
        List<String> violations = new ArrayList<>();
        List<Allocation> lines = new ArrayList<>();
        SpaceCandidate selected = space(eventId).stream().filter(c -> Objects.equals(c.roomId(), roomId) && Objects.equals(c.breakoutRoomId(), breakoutRoomId)).findFirst().orElse(null);
        if (selected == null)
            violations.add("Select two distinct known rooms");
        else
        {
            violations.addAll(selected.violations());
            for (String id : List.of(roomId, breakoutRoomId))
            {
                Room r = rooms.findById(id).orElseThrow();
                lines.add(new Allocation(id, r.name, "ROOM", 1, 1, r.priceCents, r.priceCents, r.version, e.startsAt(), e.endsAt(), id));
            }
        }
        ServiceCheck food = catering(e, packageId), tech = technical(eventId, roomId);
        lines.addAll(food.allocations());
        lines.addAll(tech.allocations());
        violations.addAll(food.violations());
        violations.addAll(tech.violations());
        int total = lines.stream().mapToInt(Allocation::totalCents).sum();
        if (total > e.budgetCents)
            violations.add("Complete quote exceeds the budget");
        return new WorkshopQuote(roomId, breakoutRoomId, packageId, violations.isEmpty(), total, List.copyOf(lines), List.copyOf(violations));
    }

    public Optional<WorkshopQuote> cheapest(String eventId)
    {
        // Six possible room assignments and two lunch packages keep exhaustive validation bounded.
        return space(eventId).stream().filter(SpaceCandidate::valid)
                .flatMap(c -> resources.findAll().stream().filter(r -> "LUNCH".equals(r.kind))
                        .map(r -> quote(eventId, c.roomId(), c.breakoutRoomId(), r.id)))
                .filter(WorkshopQuote::valid).min(Comparator.comparingInt(WorkshopQuote::totalCents));
    }
}
