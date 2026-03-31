package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.dto.ParsedTransactionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Receipt OCR service — calls the Tesseract CLI via ProcessBuilder.
 * No native JAR bindings required; any installed Tesseract version works.
 *
 * <h3>Setup</h3>
 * Install Tesseract OCR (adds {@code tesseract} to PATH automatically):
 * <ul>
 *   <li>Windows: <a href="https://github.com/UB-Mannheim/tesseract/wiki">UB-Mannheim installer</a></li>
 *   <li>macOS: {@code brew install tesseract}</li>
 *   <li>Linux: {@code sudo apt-get install tesseract-ocr}</li>
 * </ul>
 * Optional: override {@code app.ocr.tesseract-path} in {@code application.properties}
 * if the executable is not on PATH.
 */
@Service
@RequiredArgsConstructor
public class OcrService {

    private final TextParserService textParserService;

    /**
     * Path to the Tesseract executable.
     * Default {@code "tesseract"} works when it is on the system PATH.
     * Override with the full path (e.g. {@code C:/Program Files/Tesseract-OCR/tesseract.exe})
     * if needed.
     */
    @Value("${app.ocr.tesseract-path:tesseract}")
    private String tesseractPath;

    /** Tesseract language code(s). Use {@code eng+srp} for Serbian+English receipts. */
    @Value("${app.ocr.language:eng}")
    private String ocrLanguage;

    /**
     * Processes an uploaded receipt image with Tesseract OCR and returns the
     * best-guess transaction details.
     *
     * @param image           Uploaded image (JPEG, PNG, BMP).
     * @param defaultCurrency User's base currency — used as fallback.
     * @return Extracted transaction data (amount, category, description, currency).
     */
    public ParsedTransactionDTO processReceipt(MultipartFile image, String defaultCurrency) {
        if (image == null || image.isEmpty()) {
            throw new RuntimeException("No image provided.");
        }

        Path tempInput  = null;
        Path tempOutBase = null;
        try {
            // Write uploaded bytes to a temp file with the original extension
            String suffix = getExtension(image.getOriginalFilename());
            tempInput   = Files.createTempFile("receipt_in_", suffix);
            tempOutBase = Files.createTempFile("receipt_out_", "");
            Files.write(tempInput, image.getBytes());

            // Run: tesseract <input> <outputBase> --psm 4 -l <lang>
            // Tesseract appends ".txt" to the output base path automatically.
            ProcessBuilder pb = new ProcessBuilder(List.of(
                    tesseractPath,
                    tempInput.toString(),
                    tempOutBase.toString(),
                    "--psm", "4",
                    "-l", ocrLanguage
            ));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String processLog = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException(
                    "Tesseract exited with code " + exitCode + ". " +
                    (processLog.isBlank() ? "Is Tesseract installed?" : processLog.trim()));
            }

            // Read the text output file
            Path outputTxt = Path.of(tempOutBase + ".txt");
            String rawText = Files.exists(outputTxt)
                    ? Files.readString(outputTxt, StandardCharsets.UTF_8)
                    : "";
            Files.deleteIfExists(outputTxt);

            return textParserService.parseReceiptText(rawText, defaultCurrency);

        } catch (IOException e) {
            // Executable not found → give a helpful message
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("No such file") || msg.contains("cannot find") || msg.contains("error=2")) {
                throw new RuntimeException(
                    "Tesseract is not installed or not on PATH. " +
                    "Install it from https://github.com/UB-Mannheim/tesseract/wiki " +
                    "or set app.ocr.tesseract-path in application.properties.");
            }
            throw new RuntimeException("Failed to process receipt: " + msg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OCR processing was interrupted.");
        } finally {
            try { if (tempInput  != null) Files.deleteIfExists(tempInput);  } catch (IOException ignored) {}
            try { if (tempOutBase != null) Files.deleteIfExists(tempOutBase); } catch (IOException ignored) {}
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf('.'));
    }
}
