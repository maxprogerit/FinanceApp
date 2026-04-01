package com.smartfinance.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/**
 * Extracts raw text from a receipt image using Tesseract OCR.
 *
 * <h3>Engine selection:</h3>
 * <ol>
 *   <li>Tess4J (JAR-based Java binding) — preferred; requires {@link #tessdataPath}
 *       to point to the directory that <em>contains</em> a {@code tessdata/} sub-folder,
 *       e.g. {@code C:/Program Files/Tesseract-OCR}.</li>
 *   <li>System CLI fallback — used automatically when Tess4J cannot initialise
 *       (missing native library or missing tessdata). Uses {@link #tesseractPath}.</li>
 * </ol>
 *
 * <h3>PSM strategy:</h3>
 * <ul>
 *   <li>First attempt: PSM 4 (single-column, variable-size text) — good for most receipts.</li>
 *   <li>Auto-fallback to PSM 6 (single uniform block) if the first attempt returns fewer
 *       than {@value #MIN_QUALITY_CHARS} alphabetic characters.</li>
 * </ul>
 *
 * <h3>Required application.properties:</h3>
 * <pre>
 * app.ocr.tesseract-path=tesseract          # path to cli exe, or "tesseract" if on PATH
 * app.ocr.tessdata-path=                    # parent of tessdata/ dir; blank = auto-detect
 * app.ocr.language=eng                      # Tesseract lang code(s), e.g. "eng+srp"
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptOcrService {

    /** Minimum alphabetic characters in OCR output before quality is considered acceptable. */
    private static final int MIN_QUALITY_CHARS = 20;

    private final ImagePreprocessingService preprocessingService;

    @Value("${app.ocr.tesseract-path:tesseract}")
    private String tesseractPath;

    /** Parent directory of the {@code tessdata/} folder. Blank → auto-detected. */
    @Value("${app.ocr.tessdata-path:}")
    private String tessdataPath;

    @Value("${app.ocr.language:eng}")
    private String ocrLanguage;

    // ── Init ──────────────────────────────────────────────────────────────────

    /**
     * Called once at bean creation.
     *
     * <h3>Critical: JNA charset fix</h3>
     * Tess4J calls the native {@code TessBaseAPIGetUTF8Text()} via JNA.
     * On Windows, JNA defaults to the system charset (CP1250/CP1252) for
     * all string conversions. Tesseract always outputs UTF-8 bytes for
     * Cyrillic/non-ASCII text. Without this property, JNA misinterprets those
     * bytes → mojibake like {@code ╧ЁхфєчхЮх} instead of {@code Предузеће}.
     */
    @PostConstruct
    private void init() {
        System.setProperty("jna.encoding", StandardCharsets.UTF_8.name());
        log.info("OCR service ready — language='{}' tessdata='{}'", ocrLanguage, resolveDatapath());
        validateTessdata();
    }

    private void validateTessdata() {
        String datapath = resolveDatapath();
        if (datapath == null) {
            log.warn("Tessdata directory not found — OCR will likely fail. "
                    + "Set app.ocr.tessdata-path in application.properties.");
            return;
        }
        for (String lang : ocrLanguage.split("\\+")) {
            lang = lang.trim();
            File f = new File(datapath, lang + ".traineddata");
            if (f.exists()) {
                log.info("  tessdata OK : {}", f.getName());
            } else {
                log.warn("  tessdata MISSING: {} — OCR quality will be degraded for '{}' language. "
                        + "Download from https://github.com/tesseract-ocr/tessdata", f.getAbsolutePath(), lang);
            }
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Preprocesses the uploaded image and runs Tesseract OCR.
     *
     * @param image Uploaded receipt image (JPEG, PNG, BMP, TIFF, …).
     * @return Raw OCR text (may be empty if nothing was detected).
     * @throws RuntimeException on unrecoverable errors.
     */
    public String extractText(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new RuntimeException("No image provided.");
        }

        try {
            BufferedImage raw = ImageIO.read(image.getInputStream());
            if (raw == null) {
                throw new RuntimeException("Cannot decode image — unsupported format or corrupted file.");
            }

            // Cap huge images before preprocessing
            raw = preprocessingService.capSize(raw);

            BufferedImage processed = preprocessingService.preprocess(raw);

            // Try Tess4J first; fall back to CLI on failure
            try {
                return runTess4J(processed);
            } catch (Throwable e) {
                // Catches both Exception and native JNA errors (java.lang.Error: Invalid memory access)
                log.warn("Tess4J failed ({}), falling back to CLI: {}", e.getClass().getSimpleName(), e.getMessage());
                return runCli(image);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded image: " + e.getMessage(), e);
        }
    }

    // ── Tess4J engine ─────────────────────────────────────────────────────────

    private String runTess4J(BufferedImage processed) throws TesseractException {
        String datapath = resolveDatapath();

        // PSM 4 — single column (best for receipts with narrow column layout)
        String result = doOcr(processed, datapath, 4);

        if (isLowQuality(result)) {
            log.debug("PSM 4 quality low ({}), retrying with PSM 6", qualityScore(result));
            String fallback = doOcr(processed, datapath, 6);
            result = qualityScore(fallback) >= qualityScore(result) ? fallback : result;
        }

        log.debug("Tess4J OCR output ({} chars):\n{}",
                result.length(), result.length() > 500 ? result.substring(0, 500) + "…" : result);
        return result;
    }

    private String doOcr(BufferedImage image, String datapath, int psm) throws TesseractException {
        ITesseract tess = new Tesseract();

        if (datapath != null && !datapath.isBlank()) {
            tess.setDatapath(datapath);
        }

        tess.setLanguage(ocrLanguage);
        tess.setPageSegMode(psm);

        // Force 300 DPI; preserve word spacing so multi-word merchants survive
        tess.setVariable("user_defined_dpi", "300");
        tess.setVariable("preserve_interword_spaces", "1");
        // No char whitelist — allow Cyrillic so Serbian receipts are fully OCR'd

        return tess.doOCR(image);
    }

    // ── CLI fallback ──────────────────────────────────────────────────────────

    private String runCli(MultipartFile image) {
        Path tempInput  = null;
        Path tempOutBase = null;
        try {
            String suffix = getExtension(image.getOriginalFilename());
            tempInput   = Files.createTempFile("receipt_in_", suffix);
            tempOutBase = Files.createTempFile("receipt_out_", "");
            Files.write(tempInput, image.getBytes());

            // PSM 4 first
            String result = execCli(tempInput, tempOutBase, 4);
            if (isLowQuality(result)) {
                Files.deleteIfExists(Path.of(tempOutBase + ".txt"));
                String fallback = execCli(tempInput, tempOutBase, 6);
                if (qualityScore(fallback) >= qualityScore(result)) result = fallback;
            }

            log.debug("CLI OCR output ({} chars)", result.length());
            return result;

        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("No such file") || msg.contains("cannot find") || msg.contains("error=2")) {
                throw new RuntimeException(
                    "Tesseract is not installed or not on PATH. " +
                    "Install it from https://github.com/UB-Mannheim/tesseract/wiki " +
                    "or set app.ocr.tesseract-path in application.properties.");
            }
            throw new RuntimeException("Failed to process receipt via CLI: " + msg, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OCR processing was interrupted.");
        } finally {
            silentDelete(tempInput);
            silentDelete(tempOutBase);
        }
    }

    private String execCli(Path input, Path outBase, int psm)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(List.of(
                tesseractPath,
                input.toString(),
                outBase.toString(),
                "--psm", String.valueOf(psm),
                "-l", ocrLanguage
        ));
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String log = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = proc.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Tesseract CLI exit code " + exitCode +
                    (log.isBlank() ? "" : ": " + log.trim()));
        }

        Path txt = Path.of(outBase + ".txt");
        String text = Files.exists(txt) ? Files.readString(txt, StandardCharsets.UTF_8) : "";
        Files.deleteIfExists(txt);
        return text;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves the Tess4J datapath — the directory that directly contains {@code .traineddata} files.
     * Tess4J's {@code setDatapath()} maps directly to Tesseract's {@code datapath} argument,
     * which expects the folder holding {@code eng.traineddata} etc. (i.e. the {@code tessdata/} dir).
     *
     * Priority: explicit {@code app.ocr.tessdata-path} → derived from exe path → Windows default.
     */
    private String resolveDatapath() {
        if (tessdataPath != null && !tessdataPath.isBlank()) {
            return tessdataPath;
        }
        // Derive tessdata/ from the exe path (exe lives next to tessdata/)
        if (tesseractPath != null && !tesseractPath.equals("tesseract")) {
            File exe = new File(tesseractPath);
            File parent = exe.getParentFile();
            if (parent != null && parent.isDirectory()) {
                File derived = new File(parent, "tessdata");
                if (derived.isDirectory()) return derived.getAbsolutePath();
                return parent.getAbsolutePath(); // fallback: hope it's already tessdata dir
            }
        }
        // Windows UB-Mannheim default install
        File win = new File("C:/Program Files/Tesseract-OCR/tessdata");
        if (win.isDirectory()) {
            log.debug("Using Windows default tessdata path: {}", win.getAbsolutePath());
            return win.getAbsolutePath();
        }
        return null;
    }

    private boolean isLowQuality(String text) {
        return qualityScore(text) < MIN_QUALITY_CHARS;
    }

    private long qualityScore(String text) {
        if (text == null) return 0;
        return text.chars().filter(Character::isLetter).count();
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf('.'));
    }

    private void silentDelete(Path p) {
        try { if (p != null) Files.deleteIfExists(p); } catch (IOException ignored) {}
    }
}
