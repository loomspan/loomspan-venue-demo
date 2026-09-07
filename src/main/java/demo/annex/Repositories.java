package demo.annex;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

interface RoomRepository extends JpaRepository<Room, String>
{
    List<Room> findAllByOrderByPriceCentsAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> lockById(@Param("id") String id);
}

interface EventRepository extends JpaRepository<EventRequest, String>
{
    List<EventRequest> findAllByOrderByCreatedAtDesc();
    List<EventRequest> findBySeriesIdOrderByRevisionNumberDesc(String seriesId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EventRequest e where e.id = :id")
    Optional<EventRequest> lockById(@Param("id") String id);
}

interface AssessmentRepository extends JpaRepository<Assessment, String>
{
    List<Assessment> findByEventIdOrderByCreatedAtDesc(String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assessment a where a.id = :id")
    Optional<Assessment> lockById(@Param("id") String id);

    List<Assessment> findByStatus(String status);
}

interface ProposalRepository extends JpaRepository<Proposal, String>
{
    Optional<Proposal> findByAssessmentId(String assessmentId);
}

interface CreditRepository extends JpaRepository<RoomCredit,String> {}

interface IntakeRepository extends JpaRepository<IntakeDraft,String> {
    List<IntakeDraft> findAllByOrderByCreatedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IntakeDraft i where i.id = :id")
    Optional<IntakeDraft> lockById(@Param("id") String id);
}

interface BookingRepository extends JpaRepository<Booking, String>
{
    Optional<Booking> findByProposalId(String proposalId);

    List<Booking> findAllByOrderByStartsAtAsc();

    @Query("select b from Booking b, Proposal p, Assessment a where b.proposalId=p.id and p.assessmentId=a.id and a.eventId=:eventId")
    List<Booking> findForEvent(@Param("eventId") String eventId);
    @Query("select b from Booking b, Proposal p, Assessment a, EventRequest e where b.proposalId=p.id and p.assessmentId=a.id and a.eventId=e.id and e.seriesId=:seriesId")
    List<Booking> findForSeries(@Param("seriesId") String seriesId);

    @Query("select count(b) from ResourceReservation b where b.resourceId = :room and b.startsAt < :end and b.endsAt > :start")
    long conflicts(@Param("room") String room, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}

interface ResourceRepository extends JpaRepository<VenueResource, String>
{
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from VenueResource r where r.id = :id")
    Optional<VenueResource> lockById(@Param("id") String id);
}

interface ReservationRepository extends JpaRepository<ResourceReservation, String>
{
    List<ResourceReservation> findByBookingIdOrderByResourceId(String bookingId);

    @Query("select coalesce(sum(r.quantity),0) from ResourceReservation r where r.resourceId=:id and r.startsAt<:end and r.endsAt>:start")
    long reserved(@Param("id") String id, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
