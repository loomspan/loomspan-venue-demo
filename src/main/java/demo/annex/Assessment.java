package demo.annex;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
public class Assessment {
    @Id String id;
    String eventId;
    String status;
    @Column(length=4000) String summary;
    @Column(length=4000) String questions;
    String sessionId;
    LocalDateTime createdAt;
    protected Assessment() {}
}
