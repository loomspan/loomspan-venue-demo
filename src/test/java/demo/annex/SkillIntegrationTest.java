package demo.annex;

import ai.loomspan.api.SkillTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDate;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:annex-skills;DB_CLOSE_DELAY=-1","loomspan.connections.venue-model.api-key=test-key"})
class SkillIntegrationTest {
    @Autowired SkillTemplate skills;
    @Autowired EventStore store;
    @Test void invokesRegisteredJavaSkillsThroughPublicFacadeWithoutModel() {
        var e=store.create(new Contracts.CreateEvent("Java integration",LocalDate.of(2026,10,15),20,50000));
        assertThat(skills.invoke("listVenueRooms",Map.of("eventId",e.id()))).contains("ROOM-C","30000");
        assertThat(skills.invoke("checkRoomAvailability",Map.of("eventId",e.id(),"roomId","ROOM-C"))).contains("\"valid\":true");
    }
}
