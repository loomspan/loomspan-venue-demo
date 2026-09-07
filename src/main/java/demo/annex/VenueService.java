package demo.annex;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
import static demo.annex.Contracts.*;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class VenueService {
    private final RoomRepository rooms;
    private final EventRepository events;
    private final BookingRepository bookings;
    public VenueService(RoomRepository rooms,EventRepository events,BookingRepository bookings) {
        this.rooms=rooms; this.events=events; this.bookings=bookings;
    }
    EventRequest event(String id) { return events.findById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Event not found")); }
    public List<RoomView> rooms() { return rooms.findAllByOrderByPriceCentsAsc().stream().map(this::view).toList(); }
    RoomView view(Room r) { return new RoomView(r.id,r.name,r.capacity,r.priceCents,r.version); }
    static BookingView view(Booking b) { return new BookingView(b.id,b.proposalId,b.roomId,b.title,b.startsAt,b.endsAt,b.totalCents); }
    public VenueView venue() { return new VenueView("The Annex","America/Los_Angeles","13:00–18:00","12:30–18:30",rooms(),bookings.findAllByOrderByStartsAtAsc().stream().map(VenueService::view).toList()); }
    public CheckResult check(String eventId,String roomId) {
        Room room=rooms.findById(roomId).orElseThrow(()->new ResponseStatusException(BAD_REQUEST,"Unknown room"));
        return check(event(eventId),room);
    }
    CheckResult check(EventRequest request,Room room) {
        List<String> violations=new ArrayList<>();
        if(request.attendees>room.capacity) violations.add("Room capacity is below attendance");
        if(room.priceCents>request.budgetCents) violations.add("Room price exceeds the budget");
        if(bookings.conflicts(room.id,request.startsAt(),request.endsAt())>0) violations.add("Room is unavailable for the buffered interval");
        return new CheckResult(room.id,room.name,room.capacity,room.priceCents,room.version,violations.isEmpty(),violations,request.startsAt(),request.endsAt());
    }
}
