package demo.annex;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class Contracts {
    private Contracts() {}
    public record CreateEvent(@NotBlank @Size(max=120) String title, @NotNull LocalDate eventDate,
                              @Min(1) @Max(120) int attendees, @Min(1) @Max(10000000) int budgetCents) {}
    public record RoomView(String id,String name,int capacity,int priceCents,long version) {}
    public record CheckResult(String roomId,String roomName,int capacity,int totalCents,long roomVersion,
                              boolean valid,List<String> violations,LocalDateTime startsAt,LocalDateTime endsAt) {}
    public record ModelResult(String roomId,Integer totalCents,String summary,List<String> openQuestions) {}
    public record BookingView(String id,String proposalId,String roomId,String title,LocalDateTime startsAt,LocalDateTime endsAt,int totalCents) {}
    public record ProposalView(String id,String roomId,String roomName,int totalCents,long roomVersion,BookingView booking) {}
    public record AssessmentView(String id,String status,String summary,List<String> openQuestions,String sessionId,LocalDateTime createdAt,ProposalView proposal) {}
    public record EventView(String id,String title,LocalDate eventDate,int attendees,int budgetCents,List<AssessmentView> assessments) {}
    public record VenueView(String name,String timezone,String eventBlock,String reservationBlock,List<RoomView> rooms,List<BookingView> bookings) {}
}
