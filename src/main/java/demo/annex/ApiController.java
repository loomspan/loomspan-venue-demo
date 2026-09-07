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

    public ApiController(VenueService venue, EventStore store, AssessmentService assessment)
    {
        this.venue = venue;
        this.store = store;
        this.assessment = assessment;
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

    @PostMapping("/proposals/{id}/accept")
    public BookingView accept(@PathVariable String id)
    {
        return store.accept(id);
    }
}
