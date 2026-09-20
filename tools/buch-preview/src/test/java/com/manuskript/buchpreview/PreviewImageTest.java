package com.manuskript.buchpreview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewImageTest {

    private static final byte[] PIXEL = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @TempDir
    Path folder;

    @Test
    void encodesJpegDataUri() throws Exception {
        Path png = folder.resolve("familie.png");
        Files.write(png, PIXEL);
        String uri = PreviewImage.dataUri(png);
        assertTrue(uri.startsWith("data:image/jpeg;base64,"));
        assertTrue(uri.length() > "data:image/jpeg;base64,".length());
    }

    @Test
    void scalesDownLargeImage() {
        BufferedImage big = new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB);
        BufferedImage small = PreviewImage.scale(big, 900);
        assertTrue(small.getWidth() <= 900);
        assertTrue(small.getHeight() <= 450);
    }
}
