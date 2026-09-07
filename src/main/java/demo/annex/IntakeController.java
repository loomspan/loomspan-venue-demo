package demo.annex;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import static demo.annex.IntakeContracts.*;
import static demo.annex.Contracts.*;

@RestController
@RequestMapping("/api/intakes")
public class IntakeController {
    private final IntakeService service;
    private final IntakeRecords records;
    public IntakeController(IntakeService service,IntakeRecords records) {this.service=service;this.records=records;}
    @GetMapping public List<IntakeView> list() {return records.list();}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public IntakeView interpret(@RequestParam String brief,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate referenceDate,
        @RequestParam(required=false) MultipartFile agenda) {return service.interpret(brief,referenceDate,agenda);}
    @GetMapping("/{id}/agenda") public ResponseEntity<Resource> agenda(@PathVariable String id) {
        Resource resource=service.attachment(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(records.get(id).attachmentType()))
            .header(HttpHeaders.CACHE_CONTROL,"no-store").body(resource);
    }
    @PostMapping("/{id}/confirm") public EventView confirm(@PathVariable String id,@Valid @RequestBody CreateEvent input) {return records.confirm(id,input);}
}
