package demo.annex;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static demo.annex.Contracts.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:annex-revisions;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "loomspan.connections.venue-model.api-key=test-key"})
@Sql(statements = {"DELETE FROM booking WHERE proposal_id IS NOT NULL", "DELETE FROM proposal", "DELETE FROM assessment", "DELETE FROM event_request"})
class RevisionIntegrationTest
{
    @Autowired
    EventStore store;
    @Autowired
    WorkshopService workshop;

    CreateEvent request(int people, boolean stream)
    {
        return new CreateEvent("Northstar", LocalDate.of(2026, 10, 15), people, 400000, "WORKSHOP", people == 60 ? 50 : 75, people == 60 ? 10 : 15, true, stream);
    }

    AssessmentView ready(EventView event)
    {
        var quote = workshop.cheapest(event.id()).orElseThrow();
        return store.complete(store.begin(event.id()).id(), new ModelResult(quote.roomId(), quote.totalCents(), "Validated workshop", List.of(), quote.breakoutRoomId(), quote.lunchPackageId()), "test-session");
    }

    @Test
    void revisedWorkshopPreserves2780AndBooks3320WithoutStreaming()
    {
        var first = store.create(request(60, true));
        var old = ready(first);
        var second = store.revise(first.id(), request(90, false));
        assertThat(second.seriesId()).isEqualTo(first.id());
        assertThat(second.revisionNumber()).isEqualTo(2);
        assertThat(second.current()).isTrue();
        var historical = store.list().stream().filter(e -> e.id().equals(first.id())).findFirst().orElseThrow();
        assertThat(historical.current()).isFalse();
        assertThat(historical.attendees()).isEqualTo(60);
        assertThat(historical.assessments().getFirst().proposal().totalCents()).isEqualTo(278000);
        assertThatThrownBy(() -> store.accept(old.proposal().id())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("outdated");
        assertThatThrownBy(() -> store.begin(first.id())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("outdated");
        var revised = ready(second);
        assertThat(revised.proposal().totalCents()).isEqualTo(332000);
        assertThat(revised.proposal().totalCents() - old.proposal().totalCents()).isEqualTo(54000);
        assertThat(revised.proposal().allocations()).noneMatch(a -> Set.of("EQ-STREAM", "STAFF-LEE").contains(a.resourceId()));
        var booking = store.accept(revised.proposal().id());
        assertThat(booking.reservations()).hasSize(5);
        assertThat(store.accept(revised.proposal().id()).id()).isEqualTo(booking.id());
        assertThatThrownBy(() -> store.revise(second.id(), request(60, true))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Booked");
    }

    @Test
    void staleRevisionAndInvalidMealCountsDoNotCreateNewVersions()
    {
        var first = store.create(request(60, true));
        assertThatThrownBy(() -> store.revise(first.id(), new CreateEvent("Northstar", LocalDate.of(2026, 10, 15), 90, 400000, "WORKSHOP", 50, 10, true, false))).isInstanceOf(ResponseStatusException.class);
        assertThat(store.list()).hasSize(1);
        assertThat(store.list().getFirst().current()).isTrue();
        store.revise(first.id(), request(90, false));
        assertThatThrownBy(() -> store.revise(first.id(), request(90, false))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("outdated");
        assertThat(store.list()).hasSize(2);
    }

    @Test
    void runningAssessmentBlocksRevisionUntilItFinishes()
    {
        var first = store.create(request(60, true));
        var pending = store.begin(first.id());
        assertThatThrownBy(() -> store.revise(first.id(), request(90, false))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("running assessment");
        store.fail(pending.id(), "Provider unavailable", null);
        assertThat(store.revise(first.id(), request(90, false)).revisionNumber()).isEqualTo(2);
    }

    @Test
    void simultaneousRevisionsCannotForkHistory() throws Exception
    {
        var first = store.create(request(60, true));
        var results = race(() -> store.revise(first.id(), request(90, false)), () -> store.revise(first.id(), request(90, false)));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(store.list()).hasSize(2);
        assertThat(store.list()).filteredOn(EventView::current).hasSize(1);
    }

    @Test
    void infeasibleRevisionNeverReactivatesEarlierProposal()
    {
        var first = store.create(request(60, true));
        var old = ready(first);
        var next = store.revise(first.id(), new CreateEvent("Northstar", LocalDate.of(2026, 10, 15), 90, 100, "WORKSHOP", 75, 15, true, false));
        var result = store.complete(store.begin(next.id()).id(), new ModelResult(null, null, "Budget is insufficient", List.of("Increase budget")), null);
        assertThat(result.status()).isEqualTo("NO_OPTION");
        assertThatThrownBy(() -> store.accept(old.proposal().id())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("outdated");
    }

    @Test
    void bookingAndRevisionRaceHasExactlyOneWinner() throws Exception
    {
        var first = store.create(request(60, true));
        var old = ready(first);
        var results = race(() -> store.accept(old.proposal().id()), () -> store.revise(first.id(), request(90, false)));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        if (store.list().size() == 2)
            assertThat(store.assessment(old.id()).status()).isEqualTo("READY");
        else
            assertThat(store.assessment(old.id()).status()).isEqualTo("BOOKED");
    }

    private List<Boolean> race(Runnable a, Runnable b) throws Exception
    {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2))
        {
            List<Future<Boolean>> results = new ArrayList<>();
            for (var action : List.of(a, b))
                results.add(pool.submit(() ->
                {
                    start.await();
                    try
                    {
                        action.run();
                        return true;
                    }
                    catch (ResponseStatusException ex)
                    {
                        assertThat(ex.getStatusCode().value()).isEqualTo(409);
                        return false;
                    }
                }));
            start.countDown();
            return List.of(results.get(0).get(15, TimeUnit.SECONDS), results.get(1).get(15, TimeUnit.SECONDS));
        }
    }
}
