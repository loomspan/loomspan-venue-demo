package demo.annex;

import ai.loomspan.api.SkillTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.springframework.http.HttpStatus.*;
import static demo.annex.IntakeContracts.*;

@Service
public class IntakeService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(IntakeService.class);
    private final SkillTemplate skills;
    private final ObjectMapper mapper;
    private final IntakeRecords records;
    private final Path storage;
    public IntakeService(SkillTemplate skills,ObjectMapper mapper,IntakeRecords records,
        @Value("${annex.attachments-directory:./data/attachments}") String directory) {
        this.skills=skills;this.mapper=mapper;this.records=records;storage=Path.of(directory).toAbsolutePath().normalize();
    }
    public IntakeView interpret(String brief,LocalDate reference,MultipartFile agenda) {
        if(brief==null||brief.isBlank()||brief.length()>6000||reference==null)
            throw new ResponseStatusException(BAD_REQUEST,"Supply a brief of 1–6,000 characters and an explicit reference date.");
        String id=UUID.randomUUID().toString(), name=null,type=null;
        Path file=null;
        if(agenda!=null) {
            byte[] bytes=validateImage(agenda);
            type=agenda.getContentType();
            name=Optional.ofNullable(agenda.getOriginalFilename()).orElse("agenda").replace('\\','/');
            name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","");
            if(name.isBlank()) name="agenda";
            if(name.length()>160) name=name.substring(name.length()-160);
            file=path(id,type);
            try {Files.createDirectories(storage);Files.write(file,bytes,StandardOpenOption.CREATE_NEW);}
            catch(IOException e){throw new ResponseStatusException(INTERNAL_SERVER_ERROR,"The agenda could not be saved.");}
        }
        try {records.begin(id,brief.trim(),reference,name,type);}
        catch(RuntimeException e){if(file!=null)try{Files.deleteIfExists(file);}catch(IOException ignored){} throw e;}
        var input=new LinkedHashMap<String,Object>();input.put("brief",brief.trim());input.put("referenceDate",reference.toString());
        if(file!=null) input.put("agenda",new FileSystemResource(file));
        var session=new AtomicReference<String>();
        try {
            String json=skills.invoke("interpretEventBrief",input,view->session.set(view.sessionId()));
            var result=validate(mapper.readValue(json,Interpretation.class),brief);
            return records.finish(id,result,session.get(),null);
        } catch(RuntimeException e) {
            log.warn("Intake {} failed ({})",id,e.getClass().getSimpleName());
            log.debug("Intake failure details",e);
            return records.finish(id,null,session.get(),"Intake could not produce a valid draft. Check model configuration or Console diagnostics, then try again. No event was created.");
        }
    }
    public Resource attachment(String id) {
        var draft=records.get(id);
        if(draft.attachmentType()==null) throw new ResponseStatusException(NOT_FOUND,"No agenda attached");
        var resource=new FileSystemResource(path(draft.id(),draft.attachmentType()));
        if(!resource.exists()) throw new ResponseStatusException(NOT_FOUND,"The stored agenda file is missing.");
        return resource;
    }
    private Path path(String id,String type) {return storage.resolve(UUID.fromString(id)+(type.equals("image/png")?".png":".jpg"));}
    private byte[] validateImage(MultipartFile file) {
        if(file.isEmpty()||file.getSize()>2*1024*1024)
            throw new ResponseStatusException(BAD_REQUEST,"Upload one nonempty PNG or JPEG agenda, at most 2 MB.");
        if(!Set.of("image/png","image/jpeg").contains(Objects.toString(file.getContentType(),"")))
            throw new ResponseStatusException(BAD_REQUEST,"This demo accepts PNG and JPEG agendas only.");
        try {
            byte[] bytes=file.getBytes();
            try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(stream);
                if(!readers.hasNext()) throw new IOException("Unrecognized image");
                var reader=readers.next();
                try {
                    reader.setInput(stream);
                    String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                    if(!(format.equals("png")&&file.getContentType().equals("image/png")||format.equals("jpeg")&&file.getContentType().equals("image/jpeg"))) throw new IOException("Type mismatch");
                    if((long)reader.getWidth(0)*reader.getHeight(0)>16_000_000) throw new IOException("Image too large");
                    reader.read(0);
                } finally {reader.dispose();}
            }
            return bytes;
        } catch(IOException|RuntimeException e) {throw new ResponseStatusException(BAD_REQUEST,"The agenda must be a readable PNG or JPEG image of at most 16 megapixels.");}
    }
    // The schema guarantees shape; Java independently checks domain ranges and date syntax.
    static Interpretation validate(Interpretation r,String brief) {
        if(r==null||r.questions()==null||r.questions().size()>20||r.questions().stream().anyMatch(q->q==null||q.length()>1000)
            ||r.agendaSummary()==null||r.agendaSummary().length()>4000) throw new IllegalArgumentException("Invalid intake output");
        if(r.title()!=null&&(r.title().isBlank()||r.title().length()>120)) throw new IllegalArgumentException("Invalid title");
        if(r.eventType()!=null&&!Set.of("MEETING","WORKSHOP").contains(r.eventType())) throw new IllegalArgumentException("Invalid type");
        if(r.eventDate()!=null) LocalDate.parse(r.eventDate());
        range(r.attendees(),1,120);range(r.budgetCents(),1,10000000);range(r.standardLunches(),0,120);range(r.veganLunches(),0,120);
        // Historical images must never supply current dietary counts. Require a literal
        // numeric count in the current brief; ambiguous wording stays for human review.
        if(!"MEETING".equals(r.eventType())) r=new Interpretation(r.title(),r.eventType(),r.eventDate(),r.attendees(),r.budgetCents(),
            groundedCount(r.standardLunches(),brief,"standard"),groundedCount(r.veganLunches(),brief,"vegan"),r.presentation(),r.livestream(),r.agendaSummary(),r.questions());
        var questions=new ArrayList<>(r.questions());
        if(r.title()==null) questions.add("Supply an event title.");
        if(r.eventType()==null) questions.add("Choose a supported event type.");
        if(r.eventDate()==null) questions.add("Confirm the exact event date.");
        if(r.attendees()==null) questions.add("Supply current attendance.");
        if(r.budgetCents()==null) questions.add("Supply the current budget in USD.");
        if("WORKSHOP".equals(r.eventType())) {
            if(r.standardLunches()==null||r.veganLunches()==null) questions.add("Supply both current standard and vegan lunch counts; do not copy historical attendance.");
            else if(r.attendees()!=null&&r.standardLunches()+r.veganLunches()!=r.attendees()) questions.add("Correct lunch counts so they add up to current attendance.");
            if(r.attendees()!=null&&r.attendees()%2!=0) questions.add("Use even attendance for two equal workshop groups.");
        }
        return new Interpretation(r.title(),r.eventType(),r.eventDate(),r.attendees(),r.budgetCents(),r.standardLunches(),r.veganLunches(),r.presentation(),r.livestream(),r.agendaSummary(),List.copyOf(new LinkedHashSet<>(questions)));
    }
    private static void range(Integer value,int min,int max) {if(value!=null&&(value<min||value>max))throw new IllegalArgumentException("Out-of-range extraction");}
    private static Integer groundedCount(Integer count,String brief,String label) {
        if(count==null) return null;
        String pattern="(?i)(?:\\b"+count+"\\s+"+label+"\\b|\\b"+label+"(?:\\s+(?:lunches|meals|portions))?\\s*[:=]?\\s*"+count+"\\b)";
        return java.util.regex.Pattern.compile(pattern).matcher(brief).find()?count:null;
    }
}
