package com.manuskript.buchpreview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewShellTest {

    @Test
    void writesBootAndBaseHrefWithoutRawBodyScript(@TempDir Path folder) throws Exception {
        String template = "<!DOCTYPE html><html><head></head><body>\n<script>\n/*BP_BOOT*/\n</script>\n</body></html>";
        String boot = "window.bpBodyHtml=\"<p><img src=\\\"familie.png\\\"></p>\";";
        Path file = PreviewShell.write(folder, template, boot);
        String html = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(html.contains("<base href=\""));
        assertTrue(html.contains(PreviewShell.baseHref(folder)));
        assertTrue(html.contains("familie.png"));
        assertFalse(html.contains("id=\"bp-source\""));
        assertFalse(html.contains("type=\"text/plain\""));
    }
}
