package com.smartfinance.dashboard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * Image preprocessing pipeline for receipt OCR accuracy.
 *
 * <p>Pipeline (applied in order):
 * <ol>
 *   <li>Grayscale conversion</li>
 *   <li>5×5 Gaussian blur (noise reduction)</li>
 *   <li>Otsu binarisation (adaptive threshold)</li>
 *   <li>2× upscale (Tesseract prefers ≥300 DPI)</li>
 * </ol>
 *
 * <p>Pure Java2D implementation — no native library required.
 * Achieves the same preprocessing quality as OpenCV for this use-case.
 */
@Slf4j
@Service
public class ImagePreprocessingService {

    /**
     * Applies the full preprocessing pipeline to an input image and returns
     * a greyscale {@link BufferedImage} ready for Tesseract OCR.
     *
     * @param src Source image (any colour mode, any size).
     * @return Preprocessed {@link BufferedImage} (TYPE_BYTE_GRAY).
     */
    public BufferedImage preprocess(BufferedImage src) {
        if (src == null) throw new IllegalArgumentException("Source image must not be null");

        BufferedImage gray    = toGrayscale(src);
        BufferedImage blurred = gaussianBlur(gray);
        BufferedImage binary  = otsuThreshold(blurred);
        BufferedImage scaled  = upscale2x(binary);

        log.debug("Preprocessed image: {}x{} → {}x{}",
                src.getWidth(), src.getHeight(), scaled.getWidth(), scaled.getHeight());
        return scaled;
    }

    // ── Step 1 — Grayscale ────────────────────────────────────────────────────

    private BufferedImage toGrayscale(BufferedImage src) {
        BufferedImage gray = new BufferedImage(src.getWidth(), src.getHeight(),
                                               BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = gray.getGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return gray;
    }

    // ── Step 2 — Gaussian blur (5×5 kernel, σ≈1) ─────────────────────────────

    private BufferedImage gaussianBlur(BufferedImage gray) {
        // Normalised 5×5 Gaussian kernel (approximation to σ=1)
        float[] kernel = {
            1f/256f,  4f/256f,  6f/256f,  4f/256f,  1f/256f,
            4f/256f, 16f/256f, 24f/256f, 16f/256f,  4f/256f,
            6f/256f, 24f/256f, 36f/256f, 24f/256f,  6f/256f,
            4f/256f, 16f/256f, 24f/256f, 16f/256f,  4f/256f,
            1f/256f,  4f/256f,  6f/256f,  4f/256f,  1f/256f
        };
        ConvolveOp blur = new ConvolveOp(new Kernel(5, 5, kernel),
                                         ConvolveOp.EDGE_NO_OP, null);
        return blur.filter(gray, null);
    }

    // ── Step 3 — Otsu binarisation ────────────────────────────────────────────

    /**
     * Computes the optimal global threshold using Otsu's method (maximises
     * inter-class variance) then applies binary thresholding.
     */
    private BufferedImage otsuThreshold(BufferedImage gray) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        Raster raster = gray.getRaster();

        // Build intensity histogram
        int[] hist = new int[256];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                hist[raster.getSample(x, y, 0)]++;

        int total = w * h;

        // Compute weighted sum of all intensities
        float sum = 0f;
        for (int t = 0; t < 256; t++) sum += t * hist[t];

        float sumB = 0f, wB = 0f;
        float maxVar = 0f;
        int threshold = 128;

        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0f) continue;
            float wF = total - wB;
            if (wF == 0f) break;

            sumB += (float)(t * hist[t]);
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;
            float var = wB * wF * (mB - mF) * (mB - mF);

            if (var > maxVar) {
                maxVar    = var;
                threshold = t;
            }
        }

        log.debug("Otsu threshold: {}", threshold);

        // Apply binary threshold — result stays TYPE_BYTE_GRAY (0 or 255)
        BufferedImage binary = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster wr = binary.getRaster();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                wr.setSample(x, y, 0, raster.getSample(x, y, 0) >= threshold ? 255 : 0);

        return binary;
    }

    // ── Step 4 — 2× upscale ──────────────────────────────────────────────────

    private BufferedImage upscale2x(BufferedImage src) {
        int newW = src.getWidth()  * 2;
        int newH = src.getHeight() * 2;

        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_BYTE_GRAY);
        AffineTransformOp op = new AffineTransformOp(
                AffineTransform.getScaleInstance(2.0, 2.0),
                AffineTransformOp.TYPE_BICUBIC);
        return op.filter(src, scaled);
    }

    /**
     * Caps very large images to a max dimension of 4000px on the longer side
     * (prevents excessive memory + processing time) before the main pipeline.
     *
     * @param src Original image.
     * @return Possibly down-sampled image.
     */
    public BufferedImage capSize(BufferedImage src) {
        int maxDim = 4000;
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return src;

        double scale = (double) maxDim / Math.max(w, h);
        int nw = (int)(w * scale);
        int nh = (int)(h * scale);

        BufferedImage down = new BufferedImage(nw, nh, src.getType());
        AffineTransformOp op = new AffineTransformOp(
                AffineTransform.getScaleInstance(scale, scale),
                AffineTransformOp.TYPE_BILINEAR);
        op.filter(src, down);
        log.debug("Capped image from {}x{} to {}x{}", w, h, nw, nh);
        return down;
    }
}
