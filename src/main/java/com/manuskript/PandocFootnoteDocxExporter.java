package com.manuskript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Kleiner Pandoc-Pfad für „DOCX mitspeichern“, wenn das Kapitel echte Fußnoten enthält.
 * Das vorhandene DOCX dient als Referenz, damit seine Formatvorlagen erhalten bleiben.
 */
public final class PandocFootnoteDocxExporter {

    private static final Logger logger = LoggerFactory.getLogger(PandocFootnoteDocxExporter.class);

    private PandocFootnoteDocxExporter() {
    }

    public static void export(String markdown, File targetDocx) throws IOException {
        if (targetDocx == null) {
            throw new IOException("DOCX-Zieldatei fehlt");
        }
        String setupError = ToolSetupSupport.ensurePandoc(
                message -> logger.debug("Pandoc für Kapitel-DOCX: {}", message));
        if (setupError != null) {
            throw new IOException(setupError);
        }

        File binary = ToolSetupSupport.resolvePandocBinary();
        String executable = binary != null ? binary.getAbsolutePath() : "pandoc";
        Path target = targetDocx.toPath().toAbsolutePath();
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("DOCX-Zielordner fehlt");
        }
        Files.createDirectories(parent);

        Path input = Files.createTempFile(parent, ".manuskript-footnotes-", ".md");
        Path output = Files.createTempFile(parent, ".manuskript-footnotes-", ".docx");
        Files.deleteIfExists(output);
        try {
            String pandocMarkdown = MarkdownFootnoteSupport.toReferenceMarkdown(
                    markdown == null ? "" : markdown);
            Files.writeString(input, pandocMarkdown, StandardCharsets.UTF_8);
            List<String> command = new ArrayList<>();
            command.add(executable);
            command.add("--from=" + PandocExportWindow.pandocMarkdownReaderFormat());
            command.add("--to=docx");
            if (Files.isRegularFile(target) && Files.size(target) > 0) {
                command.add("--reference-doc=" + target);
            }
            command.add("--output=" + output);
            command.add(input.toString());

            Process process = new ProcessBuilder(command)
                    .directory(parent.toFile())
                    .redirectErrorStream(true)
                    .start();
            String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished;
            try {
                finished = process.waitFor(120, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                throw new IOException("Pandoc wurde unterbrochen", e);
            }
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Pandoc-Timeout beim DOCX-Mitspeichern");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(output)) {
                throw new IOException("Pandoc-DOCX fehlgeschlagen"
                        + (processOutput.isBlank() ? "" : ": " + processOutput.trim()));
            }
            try {
                Files.move(output, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailed) {
                Files.move(output, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
        }
    }
}
