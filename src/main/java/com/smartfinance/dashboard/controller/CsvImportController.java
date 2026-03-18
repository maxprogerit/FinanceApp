package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.service.CsvImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CsvImportController {

    private final CsvImportService csvImportService;

    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvImportService.ImportResult> importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "currency", defaultValue = "USD") String currency) {
        CsvImportService.ImportResult result = csvImportService.importFromCsv(file, currency);
        return ResponseEntity.ok(result);
    }
}
