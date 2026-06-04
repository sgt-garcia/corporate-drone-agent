package ai.corporatedroneagent.controller;

import ai.corporatedroneagent.dto.ApiErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorDto> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        int statusCode = exception.getStatusCode().value();
        HttpStatus status = HttpStatus.resolve(statusCode);
        String error = status == null ? exception.getStatusCode().toString() : status.getReasonPhrase();
        String message = exception.getReason() == null || exception.getReason().isBlank()
                ? error
                : exception.getReason();

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(new ApiErrorDto(
                        Instant.now(),
                        statusCode,
                        error,
                        message,
                        request.getRequestURI()
                ));
    }
}
