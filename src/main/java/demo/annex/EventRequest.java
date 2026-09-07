package demo.annex;
import jakarta.persistence.*;
import java.time.*;
@Entity
public class EventRequest {
    @Id String id;
    String title;
    LocalDate eventDate;
    int attendees;
    int budgetCents;
    LocalDateTime createdAt;
    protected EventRequest() {}
    LocalDateTime startsAt() { return eventDate.atTime(12,30); }
    LocalDateTime endsAt() { return eventDate.atTime(18,30); }
}
