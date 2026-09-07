package demo.annex;

import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import static demo.annex.Contracts.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController
{
    private final VenueService venue;
    private final EventStore store;
    private final AssessmentService assessment;
    private final ai.loomspan.api.SkillTemplate skills;
    private final tools.jackson.databind.ObjectMapper mapper;

    public ApiController(VenueService venue, EventStore store, AssessmentService assessment, ai.loomspan.api.SkillTemplate skills, tools.jackson.databind.ObjectMapper mapper)
    {
        this.venue = venue;
        this.store = store;
        this.assessment = assessment;
        this.skills=skills;this.mapper=mapper;
    }

    @GetMapping("/venue")
    public VenueView venue()
    {
        return venue.venue();
    }

    @GetMapping("/events")
    public List<EventView> events()
    {
        return store.list();
    }

    @PostMapping("/events")
    public EventView create(@Valid @RequestBody CreateEvent input)
    {
        return store.create(input);
    }

    @PostMapping("/events/{id}/assessments")
    public AssessmentView assess(@PathVariable String id)
    {
        return assessment.assess(id);
    }

    @PostMapping("/events/{id}/revisions")
    public EventView revise(@PathVariable String id, @Valid @RequestBody CreateEvent input)
    {
        return store.revise(id, input);
    }

    @PostMapping("/proposals/{id}/accept")
    public BookingView accept(@PathVariable String id)
    {
        return store.accept(id);
    }

    public record CreditResult(CreditView credit,String sessionId) {}
    @PostMapping("/proposals/{id}/room-credit")
    public CreditResult credit(@PathVariable String id)
    {
        var session=new java.util.concurrent.atomic.AtomicReference<String>();
        String result=skills.invoke("applyRoomCredit",java.util.Map.of("proposalId",id),view->session.set(view.sessionId()));
        var decision=mapper.readValue(result,CreditSkills.Decision.class);
        if(decision.status()!=200) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(decision.status()),decision.message());
        return new CreditResult(decision.credit(),session.get());
    }
}
