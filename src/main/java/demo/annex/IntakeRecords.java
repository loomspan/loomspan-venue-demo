package demo.annex;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.http.HttpStatus.*;
import static demo.annex.IntakeContracts.*;
import static demo.annex.Contracts.*;

@Service
public class IntakeRecords {
    private final IntakeRepository drafts;
    private final ObjectMapper mapper;
    private final EventStore events;
    public IntakeRecords(IntakeRepository drafts, ObjectMapper mapper, EventStore events) {
        this.drafts=drafts;this.mapper=mapper;this.events=events;
    }
    @Transactional public IntakeView begin(String id,String brief,LocalDate reference,String filename,String type) {
        var d=new IntakeDraft();d.id=id;d.brief=brief;d.referenceDate=reference;d.createdAt=LocalDateTime.now();
        d.status="RUNNING";d.attachmentName=filename;d.attachmentType=type;
        return view(drafts.save(d));
    }
    @Transactional public IntakeView finish(String id,Interpretation result,String session,String failure) {
        var d=find(id);d.resultJson=result==null?null:mapper.writeValueAsString(result);d.sessionId=session;
        d.status=result==null?"FAILED":"REVIEW";d.message=failure;return view(d);
    }
    @Transactional(readOnly=true) public List<IntakeView> list() {
        return drafts.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList();
    }
    @Transactional(readOnly=true) public IntakeView get(String id) { return view(find(id)); }
    @Transactional public EventView confirm(String id,CreateEvent input) {
        var d=drafts.lockById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Intake not found"));
        if(d.eventId!=null) return events.list().stream().filter(e->e.id().equals(d.eventId)).findFirst().orElseThrow();
        if(!"REVIEW".equals(d.status)) throw new ResponseStatusException(CONFLICT,"Interpret the brief successfully before confirming it.");
        if(input.eventType()==null) throw new ResponseStatusException(BAD_REQUEST,"Choose an explicit event type.");
        var saved=events.create(input);d.eventId=saved.id();d.status="CONFIRMED";return saved;
    }
    private IntakeDraft find(String id) {return drafts.findById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Intake not found"));}
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional public void recoverInterrupted() {
        drafts.findAllByOrderByCreatedAtDesc().stream().filter(d->"RUNNING".equals(d.status)).forEach(d->{
            d.status="FAILED";d.message="Intake was interrupted by a restart. Submit the brief again; no event was created.";
        });
    }
    private IntakeView view(IntakeDraft d) {
        return new IntakeView(d.id,d.brief,d.referenceDate,d.createdAt,d.status,
            d.resultJson==null?null:mapper.readValue(d.resultJson,Interpretation.class),d.sessionId,d.message,d.attachmentName,d.attachmentType,d.eventId);
    }
}
