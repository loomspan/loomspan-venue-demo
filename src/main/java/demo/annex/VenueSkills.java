package demo.annex;

import ai.loomspan.api.SkillMethod;
import ai.loomspan.api.SkillParam;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class VenueSkills {
    private final VenueService venue;
    public VenueSkills(VenueService venue) { this.venue=venue; }
    @SkillMethod(name="listVenueRooms",description="Read the immutable event requirements and all three venue room capacities and exact block prices. No booking is made.")
    public Map<String,Object> list(@SkillParam(description="Application-issued event ID") String eventId) {
        EventRequest e=venue.event(eventId);
        return Map.of("eventId",e.id,"attendees",e.attendees,"budgetCents",e.budgetCents,"startsAt",e.startsAt().toString(),"endsAt",e.endsAt().toString(),"preference","lowest compliant cost","rooms",venue.rooms());
    }
    @SkillMethod(name="checkRoomAvailability",description="Check a room against the stored event attendance, budget and buffered interval. Returns exact price, room version and any violations; never reserves anything.")
    public Contracts.CheckResult check(@SkillParam String eventId,@SkillParam String roomId) { return venue.check(eventId,roomId); }
    @SkillMethod(name="validateRoomQuote",description="Validate and calculate an exact room quote against the stored request. If no room was selected, omit roomId; this returns no candidate, not a made-up quote.")
    public Map<String,Object> quote(@SkillParam String eventId,@SkillParam(required=false) String roomId) {
        venue.event(eventId);
        if(roomId==null||roomId.isBlank()) return Map.of("hasCandidate",false);
        return Map.of("hasCandidate",true,"check",venue.check(eventId,roomId));
    }
}
