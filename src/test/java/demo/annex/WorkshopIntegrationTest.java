package demo.annex;

import ai.loomspan.api.SkillTemplate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static demo.annex.Contracts.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:annex-workshop;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "loomspan.connections.venue-model.api-key=test-key"})
@Sql(statements={"DELETE FROM booking WHERE id NOT LIKE 'SEED-%'","DELETE FROM proposal","DELETE FROM assessment","DELETE FROM event_request",
    "UPDATE venue_resource SET price_cents=5000,version=0 WHERE id='STAFF-LEE'"},executionPhase=Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class WorkshopIntegrationTest {
    @Autowired EventStore store;
    @Autowired WorkshopService workshop;
    @Autowired VenueService venue;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SkillTemplate skills;
    @LocalServerPort int port;

    EventView event(int people,int budget,boolean stream) {
        return store.create(new CreateEvent("Northstar customer workshop",LocalDate.of(2026,10,15),people,budget,"WORKSHOP",people-10,10,true,stream));
    }
    ModelResult model() {return new ModelResult("ROOM-B",278000,"Birch and Cedar with lunch and plenary streaming.",List.of(),"ROOM-C","FOOD-BOX");}
    AssessmentView ready(EventView e) {return store.complete(store.begin(e.id()).id(),model(),"test-session");}
    void occupy(String resource,int quantity,String start,String end) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO booking(id,room_id,title,starts_at,ends_at,total_cents) VALUES (?,'ROOM-A','Existing service assignment',?,?,0)",id,start,end);
        jdbc.update("INSERT INTO resource_reservation(id,booking_id,resource_id,quantity,starts_at,ends_at) VALUES (?,?,?,?,?,?)",id,id,resource,quantity,start,end);
    }
    @Test void quotesAndAtomicallyBooksAllSevenResourcesAt2780() {
        var e=event(60,400000,true);
        var quote=workshop.cheapest(e.id()).orElseThrow();
        assertThat(quote.roomId()).isEqualTo("ROOM-B");assertThat(quote.breakoutRoomId()).isEqualTo("ROOM-C");
        assertThat(quote.totalCents()).isEqualTo(278000);
        assertThat(quote.allocations()).hasSize(7);
        assertThat(quote.allocations().stream().mapToInt(Allocation::totalCents).sum()).isEqualTo(278000);
        var a=ready(e);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resource_reservation",Integer.class)).isEqualTo(2);
        var booking=store.accept(a.proposal().id());
        assertThat(booking.reservations()).hasSize(7);
        assertThat(booking.reservations()).filteredOn(r->r.resourceId().equals("STAFF-SAM"))
            .allSatisfy(r->assertThat(r.endsAt().toLocalTime()).isEqualTo(LocalTime.of(13,30)));
        assertThat(store.accept(a.proposal().id()).id()).isEqualTo(booking.id());
        assertThat(venue.venue().bookings()).hasSize(3);
        assertThat(store.list().getFirst().assessments().getFirst().proposal().allocations()).isEqualTo(quote.allocations());
    }
    @Test void validatesBreakoutCapacityDeliveryAndFullBudget() {
        var ninety=workshop.cheapest(event(90,400000,false).id()).orElseThrow();
        assertThat(ninety.roomId()).isEqualTo("ROOM-A");assertThat(ninety.breakoutRoomId()).isEqualTo("ROOM-B");
        assertThat(ninety.totalCents()).isEqualTo(332000);
        assertThat(ninety.allocations()).noneMatch(a->Set.of("EQ-STREAM","STAFF-LEE").contains(a.resourceId()));
        assertThat(workshop.cheapest(event(102,1000000,true).id())).isEmpty();
        assertThat(workshop.cheapest(event(60,277999,true).id())).isEmpty();
    }
    @Test void rejectsWrongCountsAndMissingPresentationBeforeModelWork() throws Exception {
        for(String body:List.of(
            "{\"title\":\"Workshop\",\"eventDate\":\"2026-10-15\",\"attendees\":60,\"budgetCents\":400000,\"eventType\":\"WORKSHOP\",\"standardLunches\":49,\"veganLunches\":10}",
            "{\"title\":\"Workshop\",\"eventDate\":\"2026-10-15\",\"attendees\":60,\"budgetCents\":400000,\"eventType\":\"WORKSHOP\",\"standardLunches\":50,\"veganLunches\":10,\"livestream\":true}")) {
            var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/events"))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(400);
        }
        verifyNoInteractions(skills);
    }
    @Test void rejectsTamperedPriceRoomsLunchAndFalseInfeasibility() {
        var a=store.begin(event(60,400000,true).id());
        for(ModelResult invalid:List.of(new ModelResult(null,null,"No option",List.of()),
            new ModelResult("ROOM-B",1,"Wrong price",List.of(),"ROOM-C","FOOD-BOX"),
            new ModelResult("ROOM-B",278000,"Same room twice",List.of(),"ROOM-B","FOOD-BOX"),
            new ModelResult("ROOM-B",278000,"Drinks instead of lunch",List.of(),"ROOM-C","FOOD-DRINK"),
            new ModelResult("ROOM-B",314000,"Unnecessary expense",List.of(),"ROOM-C","FOOD-PLUS")))
            assertThatThrownBy(()->store.complete(a.id(),invalid,null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.assessment(a.id()).proposal()).isNull();
    }
    @Test void unavailableStreamYieldsTrueNoOptionWithoutDroppingRequirement() {
        occupy("EQ-STREAM",1,"2026-10-15 12:30:00","2026-10-15 18:30:00");
        var e=event(60,400000,true);
        assertThat(workshop.cheapest(e.id())).isEmpty();
        assertThat(store.complete(store.begin(e.id()).id(),new ModelResult(null,null,"Livestream kit unavailable",List.of("Change date or explicitly remove streaming")),null).status()).isEqualTo("NO_OPTION");
        assertThat(workshop.cheapest(event(60,400000,false).id()).orElseThrow().totalCents()).isEqualTo(218000);
    }
    @Test void equipmentStockAndHalfOpenStaffBoundariesAreChecked() {
        occupy("EQ-PRESENT",1,"2026-10-15 12:30:00","2026-10-15 18:30:00");
        occupy("STAFF-LEE",1,"2026-10-15 09:00:00","2026-10-15 12:30:00");
        var e=event(60,400000,true);
        assertThat(workshop.cheapest(e.id())).isPresent();
        occupy("EQ-PRESENT",1,"2026-10-15 12:30:00","2026-10-15 18:30:00");
        assertThat(workshop.cheapest(e.id())).isEmpty();
    }
    @Test void staleOperatorPriceOrVersionPreventsAnyReservation() {
        var a=ready(event(60,400000,true));
        jdbc.update("UPDATE venue_resource SET version=version+1 WHERE id='STAFF-LEE'");
        assertThatThrownBy(()->store.accept(a.proposal().id())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resource_reservation",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE proposal_id IS NOT NULL",Integer.class)).isZero();
    }
    @Test void staleCateringAvailabilityPreventsPartialRoomWrites() {
        var a=ready(event(60,400000,true));
        occupy("STAFF-SAM",1,"2026-10-15 12:30:00","2026-10-15 13:30:00");
        assertThatThrownBy(()->store.accept(a.proposal().id())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE proposal_id IS NOT NULL",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resource_reservation WHERE resource_id='ROOM-B'",Integer.class)).isZero();
    }
    @Test void racingWorkshopBookingsHaveExactlyOneCompleteWinner() throws Exception {
        var a=ready(event(60,400000,true));var b=ready(event(60,400000,true));
        CountDownLatch start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results=new ArrayList<>();
            for(String id:List.of(a.proposal().id(),b.proposal().id())) results.add(pool.submit(()->{start.await();try{store.accept(id);return true;}catch(ResponseStatusException ex){assertThat(ex.getStatusCode().value()).isEqualTo(409);return false;}}));
            start.countDown();int winners=0;for(var r:results) if(r.get(15,TimeUnit.SECONDS)) winners++;
            assertThat(winners).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE proposal_id IS NOT NULL",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resource_reservation",Integer.class)).isEqualTo(9);
    }
    @Test void meetingCannotBookWorkshopBreakoutRoom() {
        store.accept(ready(event(60,400000,true)).proposal().id());
        var meeting=store.create(new CreateEvent("Meeting",LocalDate.of(2026,10,15),20,50000));
        assertThat(venue.check(meeting.id(),"ROOM-C").valid()).isFalse();
    }
}
