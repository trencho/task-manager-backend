package com.project.taskmanager.exception;

import java.util.List;
import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ControllerExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<List<String>> handleValidationException(final MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList();

        return ResponseEntity.badRequest().body(errors);
    }

    /**
     * Method-PARAMETER violations, the other half of the pair.
     *
     * <p>{@code MethodArgumentNotValidException} above covers a {@code @Valid @RequestBody}; a
     * {@code @Min}/{@code @Max} on a {@code @RequestParam} raises this instead. Only the body half
     * was handled, so once {@code @Validated} made the parameter constraints actually run, a
     * rejected value surfaced as an unmapped 500 rather than a 400. A guard that turns bad input
     * into a server error is only half a guard.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<List<String>> handleConstraintViolation(final ConstraintViolationException ex) {
        final List<String> errors = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage()).toList();

        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<String> handleTaskNotFoundException(final TaskNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
