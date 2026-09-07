package demo.annex;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class RoomCredit {
    @Id String proposalId;
    int amountCents;
    String approvedBy;
    LocalDateTime approvedAt;
    protected RoomCredit() {}
}
