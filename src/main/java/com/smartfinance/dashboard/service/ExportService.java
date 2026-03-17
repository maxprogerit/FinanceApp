package com.smartfinance.dashboard.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.smartfinance.dashboard.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExportService {
    
    private final TransactionService transactionService;
    private final AnalyticsService analyticsService;
    
    public String exportTransactionsToCSV(LocalDateTime startDate, LocalDateTime endDate) throws IOException {
        List<Transaction> transactions = transactionService.getTransactionsByDateRange(startDate, endDate);
        
        StringWriter writer = new StringWriter();
        CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                .withHeader("ID", "Type", "Amount", "Currency", "Category", "Description", "Date"));
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        for (Transaction transaction : transactions) {
            csvPrinter.printRecord(
                    transaction.getId(),
                    transaction.getType(),
                    transaction.getAmount(),
                    transaction.getCurrency(),
                    transaction.getCategory(),
                    transaction.getDescription(),
                    transaction.getTransactionDate().format(formatter)
            );
        }
        
        csvPrinter.flush();
        return writer.toString();
    }
    
    public byte[] generateMonthlyReport(int year, int month) throws DocumentException {
        LocalDateTime startDate = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime endDate = startDate.withDayOfMonth(startDate.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59);
        
        Document document = new Document();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try {
            PdfWriter.getInstance(document, baos);
            document.open();
            
            // Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.BLACK);
            Paragraph title = new Paragraph("Smart Finance Dashboard - Monthly Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);
            
            // Period
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.BLACK);
            Paragraph period = new Paragraph(String.format("Period: %d-%02d", year, month), normalFont);
            period.setSpacingAfter(20);
            document.add(period);
            
            // Summary
            BigDecimal totalIncome = transactionService.getTotalIncomeForPeriod(startDate, endDate);
            BigDecimal totalExpenses = transactionService.getTotalExpensesForPeriod(startDate, endDate);
            BigDecimal netSavings = totalIncome.subtract(totalExpenses);
            
            document.add(new Paragraph("Financial Summary", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            document.add(new Paragraph(String.format("Total Income: %s", totalIncome), normalFont));
            document.add(new Paragraph(String.format("Total Expenses: %s", totalExpenses), normalFont));
            document.add(new Paragraph(String.format("Net Savings: %s", netSavings), normalFont));
            document.add(new Paragraph(" "));
            
            // Expenses by Category
            document.add(new Paragraph("Expenses by Category", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            Map<String, BigDecimal> expensesByCategory = transactionService.getExpensesByCategory(startDate, endDate);
            
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            table.setSpacingAfter(10);
            
            PdfPCell cell1 = new PdfPCell(new Phrase("Category", FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
            PdfPCell cell2 = new PdfPCell(new Phrase("Amount", FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
            table.addCell(cell1);
            table.addCell(cell2);
            
            expensesByCategory.forEach((category, amount) -> {
                table.addCell(category);
                table.addCell(amount.toString());
            });
            
            document.add(table);
            
            // Insights
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Financial Insights", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            List<String> insights = analyticsService.generateFinancialInsights();
            for (String insight : insights) {
                document.add(new Paragraph("• " + insight, normalFont));
            }
            
        } finally {
            document.close();
        }
        
        return baos.toByteArray();
    }
}
