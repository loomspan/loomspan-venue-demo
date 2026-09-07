package demo.annex;

import java.time.*;
import java.util.List;

public final class IntakeContracts {
    private IntakeContracts() {}
    public record Interpretation(String title, String eventType, String eventDate, Integer attendees,
        Integer budgetCents, Integer standardLunches, Integer veganLunches, Boolean presentation,
        Boolean livestream, String agendaSummary, List<String> questions) {}
    public record IntakeView(String id, String brief, LocalDate referenceDate, LocalDateTime createdAt,
        String status, Interpretation interpretation, String sessionId, String message,
        String attachmentName, String attachmentType, String eventId) {}
}
