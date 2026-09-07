package demo.annex;

import ai.loomspan.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static demo.annex.Contracts.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:annex-live-workshop;DB_CLOSE_DELAY=-1")
@EnabledIfEnvironmentVariable(named="ANNEX_LIVE_TEST",matches="true")
class LiveWorkshopTest {
    @Autowired SkillTemplate skills;
    @Autowired EventStore store;
    @Autowired ObjectMapper mapper;

    @Test void realSpecialistsOverlapQuoteAndBookWorkshop() {
        var event=store.create(new CreateEvent("Live workshop smoke",LocalDate.of(2026,10,15),60,400000,"WORKSHOP",50,10,true,true));
        var pending=store.begin(event.id());
        AtomicReference<SkillExecutionView> observation=new AtomicReference<>();
        String output=skills.invoke("assessEvent",Map.of("eventId",event.id(),"eventType","WORKSHOP"),observation::set);
        var result=mapper.readValue(output,ModelResult.class);
        assertThat(result.roomId()).isEqualTo("ROOM-B");assertThat(result.breakoutRoomId()).isEqualTo("ROOM-C");
        assertThat(result.lunchPackageId()).isEqualTo("FOOD-BOX");assertThat(result.totalCents()).isEqualTo(278000);
        var events=observation.get().events();
        for(String skill:List.of("planEventSpace","assessEventCatering","planEventTechnicalServices","validateEventQuote","checkWorkshopSpaceCandidates","checkEventCatering","checkEventTechnicalServices"))
            assertThat(events).anyMatch(e->skill.equals(e.route())&&"SKILL_FINISHED".equals(e.type()));
        var spaceStart=time(events,"planEventSpace","SKILL_STARTED");
        var spaceEnd=time(events,"planEventSpace","SKILL_FINISHED");
        var foodStart=time(events,"assessEventCatering","SKILL_STARTED");
        var foodEnd=time(events,"assessEventCatering","SKILL_FINISHED");
        assertThat(spaceStart).isBefore(foodEnd);assertThat(foodStart).isBefore(spaceEnd);
        assertThat(time(events,"planEventTechnicalServices","SKILL_STARTED")).isAfterOrEqualTo(spaceEnd);
        assertThat(time(events,"validateEventQuote","SKILL_STARTED")).isAfterOrEqualTo(time(events,"planEventTechnicalServices","SKILL_FINISHED"));
        System.out.println("Workshop live session: "+observation.get().sessionId()+"; space="+spaceStart+".."+spaceEnd+"; catering="+foodStart+".."+foodEnd);
        var ready=store.complete(pending.id(),result,observation.get().sessionId());
        var booked=store.accept(ready.proposal().id());
        assertThat(booked.totalCents()).isEqualTo(278000);assertThat(booked.reservations()).hasSize(7);
    }
    private Instant time(List<SkillExecutionEvent> events,String skill,String type) {
        return events.stream().filter(e->skill.equals(e.route())&&type.equals(e.type())).findFirst().orElseThrow().timestamp();
    }
}
