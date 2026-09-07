package demo.annex;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.PessimisticLockingFailureException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors
{
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> domain(ResponseStatusException e)
    {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "Request failed" : e.getReason()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<?> invalid(Exception e)
    {
        return ResponseEntity.badRequest().body(Map.of("message", "Supply a title, a valid date, 1–120 attendees and a positive budget in whole cents."));
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<?> conflict(Exception e)
    {
        return ResponseEntity.status(409).body(Map.of("message", "A competing booking is being processed. Refresh and retry; no partial booking was saved."));
    }
}
