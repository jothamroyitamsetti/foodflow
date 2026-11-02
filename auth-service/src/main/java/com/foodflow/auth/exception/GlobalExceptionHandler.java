package com.foodflow.auth.exception;
import jakarta.servlet.http.HttpServletRequest; import org.springframework.http.HttpStatus; import org.springframework.http.ResponseEntity; import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.ExceptionHandler; import org.springframework.web.bind.annotation.RestControllerAdvice; import java.time.Instant;
@RestControllerAdvice
public class GlobalExceptionHandler {
 @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<ApiError> badRequest(IllegalArgumentException e,HttpServletRequest r){return response(HttpStatus.BAD_REQUEST,e.getMessage(),r);}
 @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class) public ResponseEntity<ApiError> status(org.springframework.web.server.ResponseStatusException e,HttpServletRequest r){return response(HttpStatus.valueOf(e.getStatusCode().value()),e.getReason()==null?"Request failed":e.getReason(),r);}
 @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e,HttpServletRequest r){String m=e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+": "+x.getDefaultMessage()).findFirst().orElse("Validation failed");return response(HttpStatus.BAD_REQUEST,m,r);}
 @ExceptionHandler(Exception.class) public ResponseEntity<ApiError> unexpected(Exception e,HttpServletRequest r){e.printStackTrace();return response(HttpStatus.INTERNAL_SERVER_ERROR,e.getMessage() != null ? e.getMessage() : "Unexpected server error",r);}
 private ResponseEntity<ApiError> response(HttpStatus s,String m,HttpServletRequest r){return ResponseEntity.status(s).body(new ApiError(Instant.now(),s.value(),s.getReasonPhrase(),m,r.getRequestURI()));}
}
