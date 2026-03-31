package com.smartfinance.dashboard.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/**
 * Global exception handler — converts common infrastructure errors into
 * machine-readable JSON so the frontend always receives a consistent shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Returned when a multipart upload exceeds the configured limit
     * ({@code spring.servlet.multipart.max-file-size} /
     * {@code spring.servlet.multipart.max-request-size}).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of(
                        "error", "File is too large. Maximum allowed size is 15 MB.",
                        "hint",  "Compress the image or split the CSV into smaller batches."
                ));
    }
}
