package demo.annex;

import ai.loomspan.api.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static demo.annex.Contracts.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:annex-credits;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","loomspan.connections.venue-model.api-key=test-key"})
@Sql(statements={"DELETE FROM booking WHERE proposal_id IS NOT NULL","DELETE FROM proposal","DELETE FROM assessment","DELETE FROM event_request",
    "UPDATE venue_resource SET price_cents=20000,version=0 WHERE id='EQ-PRESENT'"})
class CreditIntegrationTest {
    @Autowired EventStore store;
    @Autowired WorkshopService workshop;
    @Autowired SkillTemplate skills;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;
    @AfterEach void clearIdentity(){SecurityContextHolder.clearContext();}
    void identity(String name,String role) {
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(name,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
        SecurityContextHolder.setContext(context);
    }
    EventView event() {return store.create(new CreateEvent("Credit workshop",LocalDate.of(2026,10,15),90,400000,"WORKSHOP",75,15,true,false));}
    AssessmentView ready(EventView e) {
        var q=workshop.cheapest(e.id()).orElseThrow();
        return store.complete(store.begin(e.id()).id(),new ModelResult(q.roomId(),q.totalCents(),"Validated workshop",List.of(),q.breakoutRoomId(),q.lunchPackageId()),"assessment-session");
    }
    HttpResponse<String> credit(String id,String user) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/proposals/"+id+"/room-credit"))
            .header("X-Annex-Demo-User",user).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"amountCents\":999999,\"role\":\"MANAGER\"}")).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void coordinatorDeniedAtBothFacadeAndSpringProxyBeforeAnyWrite() {
        var p=ready(event()).proposal();identity("alex","COORDINATOR");
        assertThatThrownBy(()->store.applyRoomCredit(p.id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->skills.invoke("applyRoomCredit",Map.of("proposalId",p.id()))).isInstanceOf(AccessDeniedException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM room_credit",Integer.class)).isZero();
    }
    @Test void managerCreditThroughRealLoomspanPreservesQuoteAndBooks3220() {
        var a=ready(event());identity("morgan","MANAGER");
        var auth=SecurityContextHolder.getContext().getAuthentication();var execution=new AtomicReference<SkillExecutionView>();
        assertThat(skills.invoke("applyRoomCredit",Map.of("proposalId",a.proposal().id()),execution::set)).contains("10000","morgan");
        assertThat(execution.get().events()).anyMatch(e->e.type().equals("SKILL_FINISHED")&&"applyRoomCredit".equals(e.route()));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(auth);
        var p=store.assessment(a.id()).proposal();assertThat(p.totalCents()).isEqualTo(332000);assertThat(p.payableTotalCents()).isEqualTo(322000);
        assertThat(p.allocations()).isEqualTo(a.proposal().allocations());
        store.applyRoomCredit(p.id());assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM room_credit",Integer.class)).isEqualTo(1);
        assertThat(store.accept(p.id()).totalCents()).isEqualTo(322000);
    }
    @Test void httpDenialApprovalAndUnknownIdentityAreExplicit() throws Exception {
        var a=ready(event());
        assertThat(credit(a.proposal().id(),"alex").statusCode()).isEqualTo(403);
        assertThat(credit(a.proposal().id(),"ROLE_MANAGER").statusCode()).isEqualTo(401);
        var approved=credit(a.proposal().id(),"morgan");assertThat(approved.statusCode()).isEqualTo(200);assertThat(approved.body()).contains("sessionId","10000","morgan");
        assertThat(store.assessment(a.id()).proposal().credit().amountCents()).isEqualTo(10000);
    }
    @Test void ineligibleBookedAndHistoricalProposalsAreRejected() throws Exception {
        var meeting=store.create(new CreateEvent("Cedar meeting",LocalDate.of(2026,10,16),20,50000));
        var cedar=store.complete(store.begin(meeting.id()).id(),new ModelResult("ROOM-C",30000,"Cedar",List.of()),null);
        assertThat(credit(cedar.proposal().id(),"morgan").statusCode()).isEqualTo(409);
        var e=event();var a=ready(e);
        store.revise(e.id(),new CreateEvent("Revised",LocalDate.of(2026,10,15),90,400000,"WORKSHOP",75,15,true,false));
        assertThat(credit(a.proposal().id(),"morgan").statusCode()).isEqualTo(409);
        var current=store.list().stream().filter(v->v.seriesId().equals(e.seriesId())&&v.current()).findFirst().orElseThrow();var next=ready(current);store.accept(next.proposal().id());
        assertThat(credit(next.proposal().id(),"morgan").statusCode()).isEqualTo(409);
    }
    @Test void creditedProposalStillRejectsChangedResourcePrice() throws Exception {
        var a=ready(event());assertThat(credit(a.proposal().id(),"morgan").statusCode()).isEqualTo(200);
        jdbc.update("UPDATE venue_resource SET price_cents=21000,version=version+1 WHERE id='EQ-PRESENT'");
        assertThatThrownBy(()->store.accept(a.proposal().id())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE proposal_id IS NOT NULL",Integer.class)).isZero();
    }
    @Test void concurrentManagerRequestsNeverStackCredits() throws Exception {
        var a=ready(event());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(()->credit(a.proposal().id(),"morgan"));var two=pool.submit(()->credit(a.proposal().id(),"morgan"));
            assertThat(one.get(15,TimeUnit.SECONDS).statusCode()).isEqualTo(200);assertThat(two.get(15,TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM room_credit",Integer.class)).isEqualTo(1);
        assertThat(store.assessment(a.id()).proposal().payableTotalCents()).isEqualTo(322000);
    }
}
