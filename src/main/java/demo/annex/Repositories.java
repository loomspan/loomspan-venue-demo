package demo.annex;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

interface RoomRepository extends JpaRepository<Room,String> {
    List<Room> findAllByOrderByPriceCentsAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> lockById(@Param("id") String id);
}
interface EventRepository extends JpaRepository<EventRequest,String> {
    List<EventRequest> findAllByOrderByCreatedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EventRequest e where e.id = :id")
    Optional<EventRequest> lockById(@Param("id") String id);
}
interface AssessmentRepository extends JpaRepository<Assessment,String> {
    List<Assessment> findByEventIdOrderByCreatedAtDesc(String eventId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assessment a where a.id = :id")
    Optional<Assessment> lockById(@Param("id") String id);
    List<Assessment> findByStatus(String status);
}
interface ProposalRepository extends JpaRepository<Proposal,String> {
    Optional<Proposal> findByAssessmentId(String assessmentId);
}
interface BookingRepository extends JpaRepository<Booking,String> {
    Optional<Booking> findByProposalId(String proposalId);
    List<Booking> findAllByOrderByStartsAtAsc();
    @Query("select b from Booking b, Proposal p, Assessment a where b.proposalId=p.id and p.assessmentId=a.id and a.eventId=:eventId")
    List<Booking> findForEvent(@Param("eventId") String eventId);
    @Query("select count(b) from Booking b where b.roomId = :room and b.startsAt < :end and b.endsAt > :start")
    long conflicts(@Param("room") String room, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
