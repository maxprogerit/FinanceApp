package com.smartfinance.dashboard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

/**
 * Pure Java2D image preprocessing pipeline for receipt OCR.
 *
 * <p>Pipeline: grayscale → 5×5 Gaussian blur → Otsu binarisation → 2× bicubic upscale.
 * Handles orientation-neutral images; for deskewing use ReceiptOcrService's Tess4J PSM modes.
 */
@Slf4j
@Service
public class ImagePreprocessingService {

    private static final int UPSCALE_FACTOR = 2;
    private static final int MAX_DEFAULT_DIM = 4000;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Caps the longer image dimension to {@code maxDim}, preserving aspect ratio.
     * Returns {@code src} unchanged if already within limits.
     */
    public BufferedImage capSize(BufferedImage src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return src;

        double scale = (double) maxDim / Math.max(w, h);
        int nw = (int) (w * scale);
        int nh = (int) (h * scale);

        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();

        log.debug("Image capped: {}x{} → {}x{}", w, h, nw, nh);
        return dst;
    }

    /** Convenience overload using the default 4000-pixel cap. */
    public BufferedImage capSize(BufferedImage src) {
        return capSize(src, MAX_DEFAULT_DIM);
    }

    /**
     * Full preprocessing pipeline:
     * <ol>
     *   <li>Convert to grayscale</li>
     *   <li>5×5 Gaussian blur (noise reduction)</li>
     *   <li>Otsu global binarisation (adaptive contrast)</li>
     *   <li>2× bicubic upscale (improves Tesseract accuracy on small fonts)</li>
     * </ol>
     */
    public BufferedImage preprocess(BufferedImage src) {
        BufferedImage gray    = toGrayscale(src);
        BufferedImage blurred = gaussianBlur(gray);
        BufferedImage binary  = otsuBinarize(blurred);
        return upscale(binary, UPSCALE_FACTOR);
    }

    // ── Pipeline steps ────────────────────────────────────────────────────────

    private BufferedImage toGrayscale(BufferedImage src) {
        BufferedImage gray = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return gray;
    }

    private BufferedImage gaussianBlur(BufferedImage src) {
        // Normalised 5×5 Gaussian kernel (σ ≈ 1.0)
        float[] data = {
            0.00296902f, 0.01330621f, 0.02193823f, 0.01330621f, 0.00296902f,
            0.01330621f, 0.05963430f, 0.09832033f, 0.05963430f, 0.01330621f,
            0.02193823f, 0.09832033f, 0.16210283f, 0.09832033f, 0.02193823f,
            0.01330621f, 0.05963430f, 0.09832033f, 0.05963430f, 0.01330621f,
            0.00296902f, 0.01330621f, 0.02193823f, 0.01330621f, 0.00296902f
        };
        ConvolveOp op = new ConvolveOp(new Kernel(5, 5, data), ConvolveOp.EDGE_NO_OP, null);
        return op.filter(src, null);
    }

    private BufferedImage otsuBinarize(BufferedImage gray) {
        int threshold = otsuThreshold(gray);
        int w = gray.getWidth();
        int h = gray.getHeight();

        BufferedImage binary = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int luma = gray.getRaster().getSample(x, y, 0);
                binary.getRaster().setSample(x, y, 0, luma > threshold ? 255 : 0);
            }
        }
        log.debug("Otsu binarised at threshold {}", threshold);
        return binary;
    }

    /**
     * Otsu's method: finds the threshold that maximises between-class variance.
     */
    private int otsuThreshold(BufferedImage gray) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        int total = w * h;

        int[] hist = new int[256];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                hist[gray.getRaster().getSample(x, y, 0)]++;

        double sum = 0;
        for (int i = 0; i < 256; i++) sum += (double) i * hist[i];

        double sumB = 0;
        int wB = 0;
        double maxVar = 0.0;
        int threshold = 127;

        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) continue;
            int wF = total - wB;
            if (wF == 0) break;

            sumB += (double) t * hist[t];
            double mB  = sumB / wB;
            double mF  = (sum - sumB) / wF;
            double var = (double) wB * wF * (mB - mF) * (mB - mF);

            if (var > maxVar) {
                maxVar    = var;
                threshold = t;
            }
        }
        return threshold;
    }

    private BufferedImage upscale(BufferedImage src, int factor) {
        int nw = src.getWidth()  * factor;
        int nh = src.getHeight() * factor;

        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }
}
