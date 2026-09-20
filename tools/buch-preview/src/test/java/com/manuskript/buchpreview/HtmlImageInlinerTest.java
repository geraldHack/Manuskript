package com.manuskript.buchpreview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlImageInlinerTest {

    private static final byte[] PIXEL = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @TempDir
    Path folder;

    @Test
    void countsImgTags() {
        assertTrue(HtmlImageInliner.countImgTags("<p><img src=\"a.png\"></p><img src='b.jpg'>") == 2);
        assertTrue(HtmlImageInliner.countImgTags("<p>kein bild</p>") == 0);
    }

    @Test
    void convertsRelativePngToFileUrl() throws Exception {
        Files.write(folder.resolve("familie.png"), PIXEL);
        String html = "<p><img src=\"familie.png\" style=\"width:30.0%\" /></p>";
        String out = HtmlImageInliner.toFileUrls(html, folder);
        assertTrue(out.contains("file:"));
        assertTrue(out.contains("familie.png"));
        assertTrue(out.contains("width:30.0%"));
        assertFalse(out.contains("src=\"familie.png\""));
    }

    @Test
    void inlinesRelativePng() throws Exception {
        Files.write(folder.resolve("familie.png"), PIXEL);
        String html = "<p><img src=\"familie.png\" style=\"width:30.0%\" /></p>";
        String out = HtmlImageInliner.inline(html, folder);
        assertTrue(out.contains("data:image/"));
        assertTrue(out.contains("class=\"bp-image\""));
        assertFalse(out.contains("src=\"familie.png\""));
    }

    @Test
    void inlinesGottExportImagesIfPresent() throws Exception {
        Path export = Path.of(System.getProperty("user.home", ""), "export", "Der_Gott_von_Demirantha_html");
        Path htmlFile = export.resolve("Der_Gott_von_Demirantha.html");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(htmlFile));
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(export.resolve("familie.png")));
        String html = Files.readString(htmlFile, java.nio.charset.StandardCharsets.UTF_8);
        String out = HtmlImageInliner.inline(HtmlBodyExtractor.displayInnerHtml(html), export);
        assertTrue(HtmlImageInliner.countDataImages(out) >= 3, "expected chapter images, got HTML length " + out.length());
        assertFalse(out.contains("src=\"familie.png\""));
        assertFalse(out.contains("src=\"kampf.png\""));
        assertFalse(out.contains("src=\"Luftschiff.png\""));
    }

    @Test
    void leavesHttpAndMissingFiles() {
        String html = "<img src=\"https://example.com/a.png\"><img src=\"fehlt.png\">";
        String out = HtmlImageInliner.inline(html, folder);
        assertTrue(out.contains("https://example.com/a.png"));
        assertFalse(out.contains("fehlt.png"));
    }

    @Test
    void rejectsPathTraversal() throws Exception {
        Files.write(folder.resolve("ok.png"), PIXEL);
        Path outside = folder.getParent().resolve("secret.png");
        Files.write(outside, PIXEL);
        String html = "<img src=\"../secret.png\">";
        String out = HtmlImageInliner.inline(html, folder);
        assertFalse(out.contains("data:image"));
        assertFalse(out.contains("../secret.png"));
        assertFalse(out.contains("<img"));
    }
}
