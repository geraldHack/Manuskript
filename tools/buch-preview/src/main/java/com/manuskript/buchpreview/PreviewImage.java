package com.manuskript.buchpreview;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Iterator;

/**
 * Kleine JPEG-Data-URIs für die JavaFX-WebView ({@code loadContent}).
 * Volle PNG-Dateien (mehrere MB) passen nicht in das Vorschau-HTML.
 */
final class PreviewImage {

    static final int MAX_EDGE = 900;

    private PreviewImage() {
    }

    static String dataUri(Path file) {
        Embedded embedded = embed(file);
        return embedded == null ? "" : embedded.dataUri;
    }

    static Embedded embed(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            BufferedImage source = ImageIO.read(file.toFile());
            if (source == null) {
                return null;
            }
            BufferedImage rgb = toJpegRgb(scale(source, MAX_EDGE));
            byte[] jpeg = writeJpeg(rgb, 0.72f);
            if (jpeg.length == 0) {
                return null;
            }
            return new Embedded(
                    "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg),
                    rgb.getWidth(),
                    rgb.getHeight());
        } catch (Exception e) {
            return null;
        }
    }

    record Embedded(String dataUri, int width, int height) {
    }

    static BufferedImage scale(BufferedImage source, int maxEdge) {
        int width = source.getWidth();
        int height = source.getHeight();
        int edge = Math.max(width, height);
        if (edge <= maxEdge || maxEdge < 1) {
            return source;
        }
        double factor = maxEdge / (double) edge;
        int w = Math.max(1, (int) Math.round(width * factor));
        int h = Math.max(1, (int) Math.round(height * factor));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(source, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static BufferedImage toJpegRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static byte[] writeJpeg(BufferedImage image, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            ByteArrayOutputStream fallback = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", fallback);
            return fallback.toByteArray();
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
