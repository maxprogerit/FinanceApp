package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import com.smartfinance.dashboard.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final SecurityUtils securityUtils;

    @GetMapping("/transactions/csv")
    public ResponseEntity<String> exportTransactionsToCSV(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        try {
            User user = securityUtils.getCurrentUser();
            String csv = exportService.exportTransactionsToCSV(startDate, endDate, user);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentDispositionFormData("attachment", "transactions.csv");

            return ResponseEntity.ok().headers(headers).body(csv);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/report/pdf")
    public ResponseEntity<byte[]> generateMonthlyReport(
            @RequestParam int year,
            @RequestParam int month) {

        try {
            User user = securityUtils.getCurrentUser();
            byte[] pdfData = exportService.generateMonthlyReport(year, month, user);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    String.format("financial-report-%d-%02d.pdf", year, month));

            return ResponseEntity.ok().headers(headers).body(pdfData);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
