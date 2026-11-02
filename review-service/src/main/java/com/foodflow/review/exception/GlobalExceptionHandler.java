package com.foodflow.review.exception;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> accessDenied(AccessDeniedException e, HttpServletRequest r) {
    return response(HttpStatus.FORBIDDEN, "Access denied: insufficient permissions", r);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> badRequest(IllegalArgumentException e, HttpServletRequest r) {
    return response(HttpStatus.BAD_REQUEST, e.getMessage(), r);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiError> status(ResponseStatusException e, HttpServletRequest r) {
    return response(HttpStatus.valueOf(e.getStatusCode().value()), e.getReason() == null ? "Request failed" : e.getReason(), r);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
    String m = e.getBindingResult().getFieldErrors().stream().map(x -> x.getField() + ": " + x.getDefaultMessage()).findFirst().orElse("Validation failed");
    return response(HttpStatus.BAD_REQUEST, m, r);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest r) {
    log.error("Unexpected error handling {}: {}", r.getRequestURI(), e.getMessage(), e);
    return response(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error: " + e.getMessage(), r);
  }

  private ResponseEntity<ApiError> response(HttpStatus s, String m, HttpServletRequest r) {
    return ResponseEntity.status(s).body(new ApiError(Instant.now(), s.value(), s.getReasonPhrase(), m, r.getRequestURI()));
  }
}
