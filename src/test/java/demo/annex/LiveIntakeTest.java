package demo.annex;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:annex-live-intake;DB_CLOSE_DELAY=-1","annex.attachments-directory=./target/live-intake-attachments"})
@EnabledIfEnvironmentVariable(named="ANNEX_LIVE_TEST",matches="true")
class LiveIntakeTest {
    @Autowired IntakeService service;
    @Autowired EventStore events;
    @Test void readsActualAgendaAndDoesNotImportHistoricalCounts() throws Exception {
        var agenda=new MockMultipartFile("agenda","northstar-agenda.png","image/png",Files.readAllBytes(Path.of("src/main/resources/static/examples/northstar-agenda.png")));
        var draft=service.interpret("Prepare a 60-person Northstar workshop next Thursday, 1–6 p.m. Two equal breakout groups, presentation, lunch with vegan options, and livestream. Budget $4,000. Last year's agenda is reference only.",LocalDate.of(2026,10,8),agenda);
        assertThat(draft.status()).isEqualTo("REVIEW");
        var r=draft.interpretation();
        assertThat(r.eventDate()).isEqualTo("2026-10-15");assertThat(r.attendees()).isEqualTo(60);
        assertThat(r.standardLunches()).isNull();assertThat(r.veganLunches()).isNull();
        assertThat(r.budgetCents()).isEqualTo(400000);assertThat(r.presentation()).isTrue();assertThat(r.livestream()).isTrue();
        assertThat(r.agendaSummary().toLowerCase()).contains("roadmap");assertThat(r.questions()).isNotEmpty();
        assertThat(draft.sessionId()).isNotBlank();assertThat(events.list()).isEmpty();
    }
    @Test void interpretsWithoutAnAttachment() {
        var draft=service.interpret("Team meeting for 20 attendees on October 16, 2026, 1–6 p.m. Room only, no catering or AV. Budget $500.",LocalDate.of(2026,10,8),null);
        assertThat(draft.status()).isEqualTo("REVIEW");assertThat(draft.attachmentName()).isNull();
        assertThat(draft.interpretation().eventType()).isEqualTo("MEETING");
        assertThat(draft.interpretation().attendees()).isEqualTo(20);
        assertThat(draft.interpretation().standardLunches()).isZero();
        assertThat(draft.interpretation().budgetCents()).isEqualTo(50000);
    }
}
