package demo.annex;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name="intake_draft")
public class IntakeDraft {
    @Id String id;
    @Lob String brief;
    LocalDate referenceDate;
    LocalDateTime createdAt;
    String status;
    @Lob String resultJson;
    String sessionId;
    String message;
    String attachmentName;
    String attachmentType;
    String eventId;
}
