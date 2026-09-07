package demo.annex;

import jakarta.persistence.*;

@Entity
public class Proposal
{
    @Id
    String id;
    @Column(unique = true)
    String assessmentId;
    String roomId;
    long roomVersion;
    int totalCents;
    String breakoutRoomId;
    String lunchPackageId;
    @Column(length = 16000)
    String allocationJson;

    protected Proposal()
    {
    }
}
