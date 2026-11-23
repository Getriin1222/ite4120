package ee.taltech.ite4120.pantry.api;

import ee.taltech.ite4120.animals.api.ApiErrorHandler;
import ee.taltech.ite4120.pantry.service.DuplicateProductException;
import ee.taltech.ite4120.pantry.service.FieldValidationException;
import java.util.List;
import java.util.Map;
import org.helex.commons.exception.ApiClientException;
import org.helex.commons.exception.ConflictException;
import org.helex.commons.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * The pantry API's errors, in the repository's one shape: RFC 9457
 * {@code application/problem+json}. The commons exceptions are rendered by the shared
 * {@link ApiErrorHandler} (this class delegates to it); what is added here is only what
 * the pantry specification asks for on top: {@code errors: [{field, message}]} on a
 * field-validation 400, {@code existingProductId} on a duplicate 409, 400 (not a
 * framework page) for malformed JSON or a malformed id/query value, and a generic,
 * logged 500 that never exposes SQL or a stack trace.
 *
 * <p>Scoped to the pantry controllers and ordered first, so the animals component's
 * behaviour is unchanged.
 */
@RestControllerAdvice(basePackageClasses = PantryController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PantryErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(PantryErrorHandler.class);

    private final ApiErrorHandler common;

    public PantryErrorHandler(ApiErrorHandler common) {
        this.common = common;
    }

    @ExceptionHandler(FieldValidationException.class)
    public ProblemDetail fieldValidation(FieldValidationException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation failed");
        pd.setDetail(e.getFieldErrors().entrySet().stream()
                .map(f -> f.getKey() + ": " + f.getValue())
                .reduce((a, b) -> a + "; " + b)
                .orElse("request is invalid"));
        pd.setProperty("errors", e.getFieldErrors().entrySet().stream()
                .map(f -> Map.of("field", f.getKey(), "message", f.getValue()))
                .toList());
        return pd;
    }

    /** Bean Validation on a body, or a query value of the wrong type (frozen=maybe). */
    @ExceptionHandler(BindException.class)
    public ProblemDetail binding(BindException e) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(),
                        "message", f.isBindingFailure() ? "has a value of the wrong type" : String.valueOf(f.getDefaultMessage())))
                .toList();
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation failed");
        pd.setDetail(errors.stream().map(f -> f.get("field") + ": " + f.get("message")).sorted()
                .reduce((a, b) -> a + "; " + b).orElse("request is invalid"));
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Invalid request");
        pd.setDetail("the request body is not valid JSON, or a value has the wrong type");
        return pd;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail typeMismatch(MethodArgumentTypeMismatchException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Invalid request");
        pd.setDetail(e.getName() + " has a value of the wrong type");
        return pd;
    }

    @ExceptionHandler(DuplicateProductException.class)
    public ProblemDetail duplicate(DuplicateProductException e) {
        ProblemDetail pd = common.conflict(e);
        pd.setProperty("existingProductId", e.getExistingProductId());
        return pd;
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail conflict(ConflictException e) {
        return common.conflict(e);
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e) {
        return common.notFound(e);
    }

    @ExceptionHandler(ApiClientException.class)
    public ProblemDetail clientError(ApiClientException e) {
        return common.clientError(e);
    }

    /**
     * Everything else. Spring's own web exceptions (405, 415, …) already carry their
     * problem document; anything unexpected is ours: 500, a generic message, and the
     * details in the server log only.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception e) {
        if (e instanceof ErrorResponse er) {
            return ResponseEntity.status(er.getStatusCode()).body(er.getBody());
        }
        log.error("pantry request failed unexpectedly", e);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle("Internal error");
        pd.setDetail("the request could not be completed because of an unexpected server error; it has been logged");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(pd);
    }
}
