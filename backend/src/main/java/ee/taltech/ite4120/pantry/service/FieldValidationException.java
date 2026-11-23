package ee.taltech.ite4120.pantry.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.helex.commons.exception.ApiClientException;

/**
 * A 400 that says which fields need attention. Still an {@link ApiClientException}
 * (the caller's fault); the pantry error handler adds the field list to the problem
 * document as {@code errors: [{field, message}]} so the form can place each message
 * beside its input.
 */
public class FieldValidationException extends ApiClientException {

    private final Map<String, String> fieldErrors;

    public FieldValidationException(Map<String, String> fieldErrors) {
        super(fieldErrors.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("; ")));
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public static FieldValidationException of(String field, String message) {
        return new FieldValidationException(Map.of(field, message));
    }

    public Map<String, String> getFieldErrors() { return fieldErrors; }
}
