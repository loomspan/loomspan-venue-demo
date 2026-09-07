package demo.annex;
import jakarta.persistence.*;
@Entity
public class Proposal {
    @Id String id;
    @Column(unique=true) String assessmentId;
    String roomId;
    long roomVersion;
    int totalCents;
    protected Proposal() {}
}
