package demo.annex;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class Contracts
{
    private Contracts()
    {
    }

    public record CreateEvent(@NotBlank @Size(max = 120) String title, @NotNull LocalDate eventDate,
            @Min(1) @Max(120) int attendees, @Min(1) @Max(10000000) int budgetCents,
            @Pattern(regexp = "MEETING|WORKSHOP") String eventType,
            @Min(0) @Max(120) int standardLunches, @Min(0) @Max(120) int veganLunches,
            boolean presentation, boolean livestream)
    {
        public CreateEvent(String title, LocalDate eventDate, int attendees, int budgetCents)
        {
            this(title, eventDate, attendees, budgetCents, "MEETING", 0, 0, false, false);
        }
    }

    public record RoomView(String id, String name, int capacity, int priceCents, long version)
    {
    }

    public record CheckResult(String roomId, String roomName, int capacity, int totalCents, long roomVersion,
            boolean valid, List<String> violations, LocalDateTime startsAt, LocalDateTime endsAt)
    {
    }

    public record ModelResult(String roomId, Integer totalCents, String summary, List<String> openQuestions,
            String breakoutRoomId, String lunchPackageId)
    {
        public ModelResult(String roomId, Integer totalCents, String summary, List<String> openQuestions)
        {
            this(roomId, totalCents, summary, openQuestions, null, null);
        }
    }

    public record Allocation(String resourceId, String name, String kind, int quantity, int priceUnits, int unitPriceCents,
            int totalCents, long version, LocalDateTime startsAt, LocalDateTime endsAt, String assignedRoomId)
    {
    }

    public record SpaceCandidate(String roomId, String breakoutRoomId, int totalCents, boolean valid, List<String> violations)
    {
    }

    public record ResourceView(String id, String name, String kind, int stock, int priceCents, long version)
    {
    }

    public record ServiceCheck(boolean valid, int totalCents, List<Allocation> allocations, List<String> violations)
    {
    }

    public record WorkshopQuote(String roomId, String breakoutRoomId, String lunchPackageId, boolean valid, int totalCents,
            List<Allocation> allocations, List<String> violations)
    {
    }

    public record ReservationView(String resourceId, String name, int quantity, LocalDateTime startsAt, LocalDateTime endsAt)
    {
    }

    public record BookingView(String id, String proposalId, String roomId, String title, LocalDateTime startsAt, LocalDateTime endsAt, int totalCents, List<ReservationView> reservations)
    {
    }

    public record CreditView(int amountCents,String approvedBy,LocalDateTime approvedAt) {}

    public record ProposalView(String id, String roomId, String roomName, int totalCents, long roomVersion, BookingView booking, List<Allocation> allocations, CreditView credit, int payableTotalCents)
    {
    }

    public record AssessmentView(String id, String status, String summary, List<String> openQuestions, String sessionId, LocalDateTime createdAt, ProposalView proposal)
    {
    }

    public record EventView(String id, String title, LocalDate eventDate, int attendees, int budgetCents, List<AssessmentView> assessments,
            String eventType, int standardLunches, int veganLunches, boolean presentation, boolean livestream,
            String seriesId, int revisionNumber, boolean current)
    {
    }

    public record VenueView(String name, String timezone, String eventBlock, String reservationBlock, List<RoomView> rooms, List<BookingView> bookings, List<ResourceView> resources)
    {
    }
}
