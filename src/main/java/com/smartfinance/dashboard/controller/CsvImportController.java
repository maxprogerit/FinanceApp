package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import com.smartfinance.dashboard.service.CsvImportService;
import com.smartfinance.dashboard.service.PlanAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * CSV import — Pro feature requiring an active Pro subscription.
 */
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class CsvImportController {

    private final CsvImportService csvImportService;
    private final PlanAccessService planAccessService;
    private final SecurityUtils securityUtils;

    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "currency", defaultValue = "USD") String currency) {

        User user = securityUtils.getCurrentUser();

        if (!planAccessService.hasAccess(user, "csv_import")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "CSV import requires an active Pro or Premium subscription.",
                                 "upgradeUrl", "/settings"));
        }

        CsvImportService.ImportResult result = csvImportService.importFromCsv(file, currency, user);
        return ResponseEntity.ok(result);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleError(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
    }
}
