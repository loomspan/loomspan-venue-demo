package demo.annex;

import ai.loomspan.api.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static demo.annex.Contracts.*;
import static demo.annex.IntakeContracts.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:annex-intake;DB_CLOSE_DELAY=-1",
    "loomspan.connections.venue-model.api-key=test-key"})
@Sql(statements={"DELETE FROM intake_draft","DELETE FROM event_request"},executionPhase=Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class IntakeIntegrationTest {
    static final Path storage;
    static {try{storage=Files.createTempDirectory("annex-intake-test-");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {registry.add("annex.attachments-directory",storage::toString);}
    @Autowired IntakeService service;
    @Autowired IntakeRecords records;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SkillTemplate skills;
    @LocalServerPort int port;
    static final LocalDate REFERENCE=LocalDate.of(2026,10,8);
    static final Interpretation COMPLETE=new Interpretation("Northstar","WORKSHOP","2026-10-15",60,400000,50,10,true,true,"Product roadmap presentation and Customer Q&A",List.of());
    static final CreateEvent CONFIRMED=new CreateEvent("Northstar",LocalDate.of(2026,10,15),60,400000,"WORKSHOP",50,10,true,true);
    void result(Interpretation output) {
        when(skills.invoke(eq("interpretEventBrief"),anyMap(),any())).thenAnswer(call->{
            Consumer<SkillExecutionView> observer=call.getArgument(2);
            observer.accept(new SkillExecutionView("intake-test-session",List.of()));
            return mapper.writeValueAsString(output);
        });
    }
    @Test void preservesUnknownsAndCreatesNothingUntilEditedConfirmation() throws Exception {
        result(new Interpretation("Northstar","WORKSHOP","2026-10-15",60,400000,null,null,true,true,"No agenda supplied",List.of()));
        var draft=service.interpret("60 people next Thursday with vegan options",REFERENCE,null);
        assertThat(draft.status()).isEqualTo("REVIEW");
        assertThat(draft.interpretation().standardLunches()).isNull();
        assertThat(draft.interpretation().veganLunches()).isNull();
        assertThat(draft.interpretation().questions()).anyMatch(q->q.contains("both current"));
        assertThat(jdbc.queryForObject("select count(*) from event_request",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from assessment",Integer.class)).isZero();
        assertThat(records.get(draft.id()).sessionId()).isEqualTo("intake-test-session");
        var invalid=new CreateEvent("Northstar",LocalDate.of(2026,10,15),60,400000,"WORKSHOP",40,10,true,true);
        assertThatThrownBy(()->records.confirm(draft.id(),invalid)).isInstanceOf(ResponseStatusException.class);
        var saved=records.confirm(draft.id(),CONFIRMED);
        assertThat(saved.standardLunches()).isEqualTo(50);
        assertThat(records.confirm(draft.id(),CONFIRMED).id()).isEqualTo(saved.id());
        assertThat(records.get(draft.id()).eventId()).isEqualTo(saved.id());
        assertThat(jdbc.queryForObject("select count(*) from event_request",Integer.class)).isEqualTo(1);
    }
    @Test void storesRepeatableAttachmentWithServerOwnedPathAndServesIt() throws Exception {
        result(COMPLETE);
        byte[] png=Files.readAllBytes(Path.of("src/main/resources/static/examples/northstar-agenda.png"));
        var draft=service.interpret("Current workshop: 60 attendees",REFERENCE,new MockMultipartFile("agenda","../../outside.png","image/png",png));
        assertThat(draft.status()).isEqualTo("REVIEW");assertThat(draft.attachmentName()).isEqualTo("outside.png");
        assertThat(service.attachment(draft.id()).getContentAsByteArray()).isEqualTo(png);
        verify(skills).invoke(eq("interpretEventBrief"),argThat((Map<String,Object> input)->{
            Resource resource=(Resource)input.get("agenda");
            return input.get("referenceDate").equals("2026-10-08")&&!resource.isOpen()&&resource.getFilename().equals(draft.id()+".png");
        }),any());
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/intakes/"+draft.id()+"/agenda")).build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).isEqualTo(png);
        assertThat(response.headers().firstValue("content-type")).contains("image/png");
    }
    @Test void rejectsBadEmptyOversizeAndMismatchedUploadsBeforeModelCall() throws Exception {
        for(var upload:List.of(new MockMultipartFile("agenda","agenda.pdf","application/pdf","fake".getBytes()),
            new MockMultipartFile("agenda","agenda.png","image/png","bad image".getBytes()),
            new MockMultipartFile("agenda","agenda.png","image/png",new byte[0]),
            new MockMultipartFile("agenda","agenda.png","image/png",new byte[2*1024*1024+1]),
            new MockMultipartFile("agenda","agenda.jpg","image/jpeg",Files.readAllBytes(Path.of("src/main/resources/static/examples/northstar-agenda.png"))))) {
            assertThatThrownBy(()->service.interpret("Workshop",REFERENCE,upload)).isInstanceOf(ResponseStatusException.class);
        }
        verifyNoInteractions(skills);assertThat(records.list()).isEmpty();
    }
    @Test void providerFailureAndInvalidOutputRemainFailedWithoutAnEvent() {
        when(skills.invoke(eq("interpretEventBrief"),anyMap(),any())).thenThrow(new RuntimeException("private provider detail"));
        var failed=service.interpret("Workshop",REFERENCE,null);
        assertThat(failed.status()).isEqualTo("FAILED");assertThat(failed.message()).doesNotContain("private provider detail");
        assertThatThrownBy(()->records.confirm(failed.id(),CONFIRMED)).isInstanceOf(ResponseStatusException.class);
        reset(skills);result(new Interpretation("Northstar","WORKSHOP","2026-02-30",60,400000,50,10,true,true,"Agenda",List.of()));
        assertThat(service.interpret("Workshop",REFERENCE,null).status()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select count(*) from event_request",Integer.class)).isZero();
    }
    @Test void validatesBriefAndRecoversAnInterruptedDraft() {
        assertThatThrownBy(()->service.interpret(" ",REFERENCE,null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.interpret("x".repeat(6001),REFERENCE,null)).isInstanceOf(ResponseStatusException.class);
        var pending=records.begin(UUID.randomUUID().toString(),"Workshop",REFERENCE,null,null);
        records.recoverInterrupted();assertThat(records.get(pending.id()).status()).isEqualTo("FAILED");
        verifyNoInteractions(skills);
    }
    @Test void rejectsInventedDietaryCountsEvenWhenTheModelReturnsValidNumbers() {
        result(new Interpretation("Northstar","WORKSHOP","2026-10-15",60,400000,55,5,true,true,"Historical image",List.of()));
        var ambiguous=service.interpret("60 attendees with vegan options",REFERENCE,null);
        assertThat(ambiguous.interpretation().standardLunches()).isNull();
        assertThat(ambiguous.interpretation().veganLunches()).isNull();
        result(COMPLETE);
        var explicit=service.interpret("60 attendees: 50 standard lunches and 10 vegan lunches",REFERENCE,null);
        assertThat(explicit.interpretation().standardLunches()).isEqualTo(50);
        assertThat(explicit.interpretation().veganLunches()).isEqualTo(10);
    }
    @Test void httpConfirmationValidatesRequiredFieldsAndDoesNotTrustExtraction() throws Exception {
        result(COMPLETE);var draft=service.interpret("Workshop",REFERENCE,null);
        var client=HttpClient.newHttpClient();
        var uri=URI.create("http://localhost:"+port+"/api/intakes/"+draft.id()+"/confirm");
        var invalid=client.send(HttpRequest.newBuilder(uri).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}" )).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(records.get(draft.id()).eventId()).isNull();
        var valid=client.send(HttpRequest.newBuilder(uri).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(CONFIRMED))).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(valid.statusCode()).isEqualTo(200);
        assertThat(mapper.readValue(valid.body(),EventView.class).assessments()).isEmpty();
    }
    @AfterAll static void cleanupAttachments() throws Exception {
        try(var files=Files.list(storage)){for(Path file:files.toList())Files.delete(file);}Files.delete(storage);
    }
}
