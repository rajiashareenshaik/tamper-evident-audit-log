package com.audit.log.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({org.springframework.dao.CannotAcquireLockException.class,
            org.springframework.dao.QueryTimeoutException.class,
            org.springframework.jdbc.CannotGetJdbcConnectionException.class})
    public org.springframework.http.ResponseEntity<Map<String, String>> busy(RuntimeException error) {
        return org.springframework.http.ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "1")
                .body(Map.of("error", "The database is busy. Try again shortly."));
    }

    @ExceptionHandler(org.springframework.jdbc.UncategorizedSQLException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> databaseFailure(
            org.springframework.jdbc.UncategorizedSQLException error) {
        String state = error.getSQLException().getSQLState();
        if ("55P03".equals(state) || "57014".equals(state)) return busy(error);
        return org.springframework.http.ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "The database operation failed."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> invalidArgument(IllegalArgumentException error) {
        return Map.of("error", error.getMessage());
    }
}
