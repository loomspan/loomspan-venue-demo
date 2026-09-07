package demo.annex;

import ai.loomspan.api.SkillMethod;
import ai.loomspan.api.SkillParam;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static demo.annex.Contracts.*;

@Component
public class CreditSkills {
    private final EventStore store;
    public CreditSkills(EventStore store){this.store=store;}
    public record Decision(int status,String message,CreditView credit) {}
    @RolesAllowed("MANAGER")
    @SkillMethod(name="applyRoomCredit",description="Apply one fixed $100 manager room credit to a current unbooked proposal with room subtotal at least $500. Uses authenticated approver, never model-supplied identity or amount. Duplicate requests never stack credits. Returns an explicit decision; no booking is made.")
    public Decision apply(@SkillParam String proposalId) {
        try {return new Decision(200,"Room credit approved",store.applyRoomCredit(proposalId));}
        catch(ResponseStatusException e){return new Decision(e.getStatusCode().value(),e.getReason(),null);}
    }
}
