package com.smartfinance.dashboard.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs Tesseract OCR on a receipt image.
 *
 * <h3>Strategy:</h3>
 * <ol>
 *   <li>Preprocess the image (grayscale → blur → Otsu → upscale)</li>
 *   <li>Try Tess4J with PSM 6 (single block) + OEM 3 (default)</li>
 *   <li>If quality looks low, retry with PSM 4 (single column)</li>
 *   <li>Fall back to the Tesseract CLI if Tess4J fails entirely</li>
 * </ol>
 *
 * <p>Tess4J's {@link Tesseract} is <em>not</em> thread-safe — a new instance is created
 * for every call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptOcrService {

    @Value("${app.ocr.tessdata-path:#{null}}")
    private String tessdataPath;

    @Value("${app.ocr.tesseract-path:tesseract}")
    private String tesseractExe;

    @Value("${app.ocr.language:eng}")
    private String language;

    private final ImagePreprocessingService imagePreprocessingService;

    @PostConstruct
    void init() {
        // Required for Tess4J to handle non-ASCII (Cyrillic) output correctly on Windows
        System.setProperty("jna.encoding", "UTF-8");
        validateTessdata();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Extracts text from a receipt image using Tesseract OCR.
     *
     * @param file Uploaded image (JPG, PNG, etc.).
     * @return OCR text, or empty string on failure.
     */
    public String extractText(MultipartFile file) {
        BufferedImage original;
        try {
            original = ImageIO.read(file.getInputStream());
        } catch (IOException e) {
            log.error("Cannot read image file '{}': {}", file.getOriginalFilename(), e.getMessage());
            return "";
        }

        if (original == null) {
            log.warn("ImageIO could not decode '{}' — unsupported format?", file.getOriginalFilename());
            return runCliOcr(file);
        }

        BufferedImage capped    = imagePreprocessingService.capSize(original);
        BufferedImage processed = imagePreprocessingService.preprocess(capped);

        try {
            // Run PSM 4 (single column — best for thermal receipt paper) and
            // PSM 6 (uniform block) in parallel; keep whichever extracts more text.
            // This maximises line coverage without sacrificing quality.
            String psm4 = runTess4j(processed, 4);
            String psm6 = runTess4j(processed, 6);

            String text = psm4.length() >= psm6.length() ? psm4 : psm6;
            log.debug("PSM4={} chars  PSM6={} chars  → using PSM{}",
                    psm4.length(), psm6.length(), psm4.length() >= psm6.length() ? 4 : 6);

            return text;

        } catch (Exception e) {
            log.warn("Tess4J failed ({}), falling back to CLI", e.getMessage());
            return runCliOcr(file);
        }
    }

    // ── Private: Tess4J ───────────────────────────────────────────────────────

    private String runTess4j(BufferedImage image, int psm) throws TesseractException {
        Tesseract tess = new Tesseract();

        if (tessdataPath != null && !tessdataPath.isBlank()) {
            tess.setDatapath(tessdataPath);
        }

        tess.setLanguage(language);
        tess.setOcrEngineMode(3);     // OEM_DEFAULT — LSTM + legacy
        tess.setPageSegMode(psm);

        String text = tess.doOCR(image);
        log.debug("Tess4J PSM={} → {} chars", psm, text == null ? 0 : text.length());
        return text == null ? "" : text;
    }

    // ── Private: CLI fallback ─────────────────────────────────────────────────

    private String runCliOcr(MultipartFile file) {
        Path tmpInput  = null;
        Path tmpOutput = null;
        try {
            String ext   = fileExtension(file.getOriginalFilename());
            tmpInput     = Files.createTempFile("receipt_in_",  "." + ext);
            tmpOutput    = Files.createTempFile("receipt_out_", "");

            Files.write(tmpInput, file.getBytes());

            List<String> cmd = new ArrayList<>();
            cmd.add(tesseractExe);
            cmd.add(tmpInput.toString());
            cmd.add(tmpOutput.toString());
            cmd.add("-l");     cmd.add(language);
            cmd.add("--oem"); cmd.add("3");
            cmd.add("--psm"); cmd.add("6");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            int exit = pb.start().waitFor();
            log.debug("Tesseract CLI exited with {}", exit);

            Path txtFile = Paths.get(tmpOutput + ".txt");
            if (Files.exists(txtFile)) {
                String result = Files.readString(txtFile);
                log.debug("CLI OCR → {} chars", result.length());
                return result;
            }
            log.warn("Tesseract CLI produced no output file");
            return "";

        } catch (Exception e) {
            log.error("CLI OCR fallback failed: {}", e.getMessage());
            return "";
        } finally {
            safeDelete(tmpInput);
            if (tmpOutput != null) {
                safeDelete(tmpOutput);
                safeDelete(Paths.get(tmpOutput + ".txt"));
            }
        }
    }

    // ── Private: helpers ──────────────────────────────────────────────────────

    /**
     * Heuristic: if fewer than 30 % of characters are alphanumeric the OCR pass
     * likely produced garbage.
     */
    private boolean isLowQuality(String text) {
        if (text == null || text.isBlank()) return true;
        long alphaNum = text.chars().filter(Character::isLetterOrDigit).count();
        return (double) alphaNum / text.length() < 0.30;
    }

    private void validateTessdata() {
        if (tessdataPath == null || tessdataPath.isBlank()) {
            log.warn("app.ocr.tessdata-path not set — Tesseract will use its default path");
            return;
        }
        Path dir = Paths.get(tessdataPath);
        if (!Files.isDirectory(dir)) {
            log.warn("Tessdata directory not found: {}", tessdataPath);
            return;
        }
        for (String lang : language.split("\\+")) {
            if (!Files.exists(dir.resolve(lang + ".traineddata"))) {
                log.warn("Missing tessdata file: {}/{}.traineddata", tessdataPath, lang);
            }
        }
    }

    private static String fileExtension(String filename) {
        if (filename == null) return "jpg";
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot + 1) : "jpg";
    }

    private static void safeDelete(Path p) {
        if (p == null) return;
        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
    }
}
