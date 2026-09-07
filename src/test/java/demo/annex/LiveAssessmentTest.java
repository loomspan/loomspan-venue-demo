package demo.annex;

import ai.loomspan.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:annex-live;DB_CLOSE_DELAY=-1")
@EnabledIfEnvironmentVariable(named="ANNEX_LIVE_TEST",matches="true")
class LiveAssessmentTest {
    @Autowired SkillTemplate skills;
    @Autowired EventStore store;
    @Autowired ObjectMapper mapper;
    @Test void actualModelPlansPricesAndBooksCedar() {
        var event=store.create(new Contracts.CreateEvent("Live smoke",LocalDate.of(2026,10,15),20,50000));
        var pending=store.begin(event.id());
        AtomicReference<SkillExecutionView> observation=new AtomicReference<>();
        String output=skills.invoke("assessEvent",Map.of("eventId",event.id()),observation::set);
        var result=mapper.readValue(output,Contracts.ModelResult.class);
        assertThat(result.roomId()).isEqualTo("ROOM-C");assertThat(result.totalCents()).isEqualTo(30000);
        assertThat(observation.get()).isNotNull();
        assertThat(observation.get().events().toString()).contains("planEventSpace","validateRoomQuote","listVenueRooms","checkRoomAvailability");
        var ready=store.complete(pending.id(),result,observation.get().sessionId());
        assertThat(store.accept(ready.proposal().id()).totalCents()).isEqualTo(30000);
    }
}
