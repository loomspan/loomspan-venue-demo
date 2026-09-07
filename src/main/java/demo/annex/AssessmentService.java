package demo.annex;

import ai.loomspan.api.SkillTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import static demo.annex.Contracts.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AssessmentService
{
    private static final Logger log = LoggerFactory.getLogger(AssessmentService.class);
    private final SkillTemplate skills;
    private final EventStore store;
    private final ObjectMapper mapper;

    public AssessmentService(SkillTemplate skills, EventStore store, ObjectMapper mapper)
    {
        this.skills = skills;
        this.store = store;
        this.mapper = mapper;
    }

    public AssessmentView assess(String eventId)
    {
        AssessmentView pending = store.begin(eventId);
        AtomicReference<String> session = new AtomicReference<>();
        try
        {
            String json = skills.invoke("assessEvent", Map.of("eventId", eventId, "eventType", store.eventType(eventId)), view ->
            {
                session.set(view.sessionId());
                log.debug("Assessment {} execution events: {}", pending.id(), view.events());
            });
            log.debug("Assessment {} model result: {}", pending.id(), json);
            return store.complete(pending.id(), mapper.readValue(json, ModelResult.class), session.get());
        }
        catch (RuntimeException ex)
        {
            log.warn("Assessment {} failed ({})", pending.id(), ex.getClass().getSimpleName());
            log.debug("Assessment failure details", ex);
            // Do not expose provider payloads, credentials, or claim a quote on failure.
            return store.fail(pending.id(), "Assessment could not produce a validated option. Check model configuration or Console diagnostics, then retry. No resources were reserved.", session.get());
        }
    }
}
