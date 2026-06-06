package ai.corporatedroneagent.controller;

import ai.corporatedroneagent.dto.ApiErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDto> handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        LOGGER.error("Unhandled API exception for {}", request.getRequestURI(), exception);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorDto(
                        Instant.now(),
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                        "An unexpected error occurred.",
                        request.getRequestURI()
                ));
    }
}
