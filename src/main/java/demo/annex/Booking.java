package demo.annex;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
public class Booking {
    @Id String id;
    @Column(unique=true) String proposalId;
    String roomId;
    String title;
    LocalDateTime startsAt;
    LocalDateTime endsAt;
    int totalCents;
    protected Booking() {}
}
