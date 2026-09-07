package demo.annex;

import ai.loomspan.api.SkillMethod;
import ai.loomspan.api.SkillParam;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class VenueSkills
{
    private final VenueService venue;
    private final WorkshopService workshop;

    public VenueSkills(VenueService venue, WorkshopService workshop)
    {
        this.venue = venue;
        this.workshop = workshop;
    }

    @SkillMethod(name = "listVenueRooms", description = "Read the immutable event requirements and all three venue room capacities and exact block prices. No booking is made.")
    public Map<String, Object> list(@SkillParam(description = "Application-issued event ID") String eventId)
    {
        EventRequest e = venue.event(eventId);
        return Map.of("eventId", e.id, "eventType", e.eventType, "attendees", e.attendees, "budgetCents", e.budgetCents, "startsAt", e.startsAt().toString(), "endsAt", e.endsAt().toString(), "preference", "lowest compliant cost", "rooms", venue.rooms());
    }

    @SkillMethod(name = "checkRoomAvailability", description = "Check a room against the stored event attendance, budget and buffered interval. Returns exact price, room version and any violations; never reserves anything.")
    public Contracts.CheckResult check(@SkillParam String eventId, @SkillParam String roomId)
    {
        return venue.check(eventId, roomId);
    }

    @SkillMethod(name = "validateRoomQuote", description = "Validate and calculate an exact room quote against the stored request. If no room was selected, omit roomId; this returns no candidate, not a made-up quote.")
    public Map<String, Object> quote(@SkillParam String eventId, @SkillParam(required = false) String roomId)
    {
        venue.event(eventId);
        if (roomId == null || roomId.isBlank())
            return Map.of("hasCandidate", false);
        return Map.of("hasCandidate", true, "check", venue.check(eventId, roomId));
    }

    @SkillMethod(name = "checkWorkshopSpaceCandidates", description = "Check all six distinct two-room assignments against the stored workshop: plenary capacity, two equal breakout groups, exact room prices and buffered availability. Read-only.")
    public List<Contracts.SpaceCandidate> workshopSpace(@SkillParam String eventId)
    {
        return workshop.space(eventId);
    }

    @SkillMethod(name = "checkEventCatering", description = "Check lunch packages against immutable dietary counts, delivery capacity and catering attendant availability. Drinks are not lunch. Returns exact allocations and violations for each package; no reservations.")
    public Map<String, Contracts.ServiceCheck> catering(@SkillParam String eventId)
    {
        return workshop.catering(eventId);
    }

    @SkillMethod(name = "checkEventTechnicalServices", description = "Check equipment and qualified operator requirements from the saved request for the selected plenary room. Returns exact costs, stock/working-hours/availability violations and assignments. Omit roomId if space is infeasible.")
    public Contracts.ServiceCheck technical(@SkillParam String eventId, @SkillParam(required = false) String roomId)
    {
        return workshop.technical(eventId, roomId);
    }

    @SkillMethod(name = "validateEventQuote", description = "Validate an exact workshop candidate using saved requirements and selected plenary room, second breakout room and lunch package. Returns exact integer total, versioned allocations and violations. Omit missing IDs if no candidate exists. Read-only.")
    public Contracts.WorkshopQuote workshopQuote(@SkillParam String eventId, @SkillParam(required = false) String roomId, @SkillParam(required = false) String breakoutRoomId, @SkillParam(required = false) String lunchPackageId)
    {
        return workshop.quote(eventId, roomId, breakoutRoomId, lunchPackageId);
    }
}
