package demo.annex;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static demo.annex.Contracts.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:annex-live-revision;DB_CLOSE_DELAY=-1")
@EnabledIfEnvironmentVariable(named = "ANNEX_LIVE_TEST", matches = "true")
class LiveRevisionTest
{
    @Autowired
    EventStore store;
    @Autowired
    AssessmentService service;

    @Test
    void reassessesChangedRequirementsAndBooksOnlyCurrentRevision()
    {
        var first = store.create(new CreateEvent("Northstar revision demo", LocalDate.of(2026, 10, 15), 60, 400000, "WORKSHOP", 50, 10, true, true));
        var original = service.assess(first.id());
        assertThat(original.status()).isEqualTo("READY");
        assertThat(original.proposal().totalCents()).isEqualTo(278000);
        var next = store.revise(first.id(), new CreateEvent("Northstar revision demo", LocalDate.of(2026, 10, 15), 90, 400000, "WORKSHOP", 75, 15, true, false));
        var revised = service.assess(next.id());
        assertThat(revised.status()).isEqualTo("READY");
        assertThat(revised.proposal().totalCents()).isEqualTo(332000);
        assertThat(revised.proposal().roomId()).isEqualTo("ROOM-A");
        assertThat(revised.proposal().allocations()).noneMatch(a -> a.resourceId().equals("EQ-STREAM") || a.resourceId().equals("STAFF-LEE"));
        assertThatThrownBy(() -> store.accept(original.proposal().id())).isInstanceOf(ResponseStatusException.class);
        assertThat(store.accept(revised.proposal().id()).reservations()).hasSize(5);
        assertThat(store.assessment(original.id()).proposal().totalCents()).isEqualTo(278000);
    }
}
