package demo.annex;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class ResourceReservation
{
    @Id
    String id;
    String bookingId;
    String resourceId;
    int quantity;
    LocalDateTime startsAt;
    LocalDateTime endsAt;

    protected ResourceReservation()
    {
    }
}
