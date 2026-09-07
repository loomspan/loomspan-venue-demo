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
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static demo.annex.Contracts.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:annex-tests;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "loomspan.connections.venue-model.api-key=test-key"})
@Sql(statements={"DELETE FROM booking WHERE proposal_id IS NOT NULL","DELETE FROM proposal","DELETE FROM assessment","DELETE FROM event_request","UPDATE room SET price_cents=30000, version=0 WHERE id='ROOM-C'"},executionPhase=Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BookingIntegrationTest {
    @Autowired EventStore store;
    @Autowired VenueService venue;
    @Autowired AssessmentService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SkillTemplate skills;
    @LocalServerPort int port;
    EventView event(int attendees,int budget) {return store.create(new CreateEvent("Team meeting",LocalDate.of(2026,10,15),attendees,budget));}
    AssessmentView ready(EventView e) {var a=store.begin(e.id());return store.complete(a.id(),new ModelResult("ROOM-C",30000,"Cedar fits the confirmed requirements.",List.of()),"test-session");}

    @Test void migrationsBoundaryQuoteAndIdempotentBooking() {
        assertThat(venue.rooms()).hasSize(3);
        EventView e=event(20,50000);
        assertThat(venue.check(e.id(),"ROOM-C").valid()).isTrue(); // Prior booking ends exactly at 12:30.
        AssessmentView a=ready(e);
        BookingView first=store.accept(a.proposal().id());
        assertThat(first.totalCents()).isEqualTo(30000);
        assertThat(first.startsAt().toLocalTime().toString()).isEqualTo("12:30");
        assertThat(store.accept(a.proposal().id()).id()).isEqualTo(first.id());
        assertThat(store.list().getFirst().assessments().getFirst().status()).isEqualTo("BOOKED");
        assertThat(venue.venue().bookings()).hasSize(3);
    }
    @Test void capacityAndBudgetAreDeterministic() {
        assertThat(venue.check(event(41,50000).id(),"ROOM-C").valid()).isFalse();
        assertThat(venue.check(event(20,29999).id(),"ROOM-C").valid()).isFalse();
        assertThat(venue.check(event(40,30000).id(),"ROOM-C").valid()).isTrue();
    }
    @Test void rejectsFabricatedPriceAndFalseInfeasibility() {
        var a=store.begin(event(20,50000).id());
        assertThatThrownBy(()->store.complete(a.id(),new ModelResult("ROOM-C",1,"Fake cheap quote",List.of()),null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->store.complete(a.id(),new ModelResult(null,null,"No rooms",List.of()),null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.assessment(a.id()).proposal()).isNull();
    }
    @Test void supportsTrueInfeasibilityWithoutProposal() {
        var a=store.begin(event(20,10000).id());
        var done=store.complete(a.id(),new ModelResult(null,null,"No room fits this budget.",List.of("Can the budget increase?")),null);
        assertThat(done.status()).isEqualTo("NO_OPTION");assertThat(done.proposal()).isNull();
    }
    @Test void changedPricePreventsAcceptanceWithoutPartialWrite() {
        var a=ready(event(20,50000));
        jdbc.update("UPDATE room SET price_cents=31000, version=version+1 WHERE id='ROOM-C'");
        assertThatThrownBy(()->store.accept(a.proposal().id())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE proposal_id IS NOT NULL",Integer.class)).isZero();
    }
    @Test void concurrentBookingsCannotBothReserveCedar() throws Exception {
        var a=ready(event(20,50000));var b=ready(event(20,50000));
        CountDownLatch start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results=new ArrayList<>();
            for(String proposalId:List.of(a.proposal().id(),b.proposal().id())) results.add(pool.submit(()->{start.await();try{store.accept(proposalId);return true;}catch(ResponseStatusException e){assertThat(e.getStatusCode().value()).isEqualTo(409);return false;}}));
            start.countDown();int winners=0;for(var result:results) if(result.get(15,TimeUnit.SECONDS)) winners++;
            assertThat(winners).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE proposal_id IS NOT NULL",Integer.class)).isEqualTo(1);
    }
    @Test void anotherAssessmentCannotDoubleBookTheSameEvent() {
        var e=event(20,50000);var a=ready(e);var b=ready(e);store.accept(a.proposal().id());
        assertThatThrownBy(()->store.accept(b.proposal().id())).isInstanceOf(ResponseStatusException.class);
    }
    @Test void failedModelAndTamperedOutputNeverCreateProposal() {
        when(skills.invoke(eq("assessEvent"),anyMap(),any())).thenThrow(new IllegalStateException("provider unavailable"));
        assertThat(service.assess(event(20,50000).id()).status()).isEqualTo("FAILED");
        when(skills.invoke(eq("assessEvent"),anyMap(),any())).thenReturn("{\"roomId\":\"ROOM-C\",\"totalCents\":1,\"summary\":\"Wrong price\",\"openQuestions\":[]}");
        assertThat(service.assess(event(20,50000).id()).status()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM proposal",Integer.class)).isZero();
    }
    @Test void httpValidationRejectsInvalidRequirementsBeforeModelCall() throws Exception {
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/events")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"\",\"eventDate\":\"2026-10-15\",\"attendees\":0,\"budgetCents\":-1}")).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);verifyNoInteractions(skills);
    }
}
