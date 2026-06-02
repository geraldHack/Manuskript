package com.manuskript;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Führt Makro-Schritte auf einem {@link ChapterEditorHost} aus (unabhängig vom Text-Widget).
 */
public final class MacroExecutor {

    private MacroExecutor() {
    }

    public static void execute(Macro macro, ChapterEditorHost host, Consumer<String> statusUpdater) {
        if (macro == null || host == null || macro.getSteps().isEmpty()) {
            return;
        }
        host.requestEditorFocus();

        String originalText = host.getText();
        String content = originalText == null ? "" : originalText.replace("¶", "");

        List<MacroStep> enabledSteps = macro.getSteps().stream()
                .filter(MacroStep::isEnabled)
                .collect(Collectors.toList());
        if (enabledSteps.isEmpty()) {
            status("Keine aktivierten Schritte im Makro", statusUpdater);
            return;
        }

        for (MacroStep step : macro.getSteps()) {
            step.resetReplacementStats();
        }

        int totalSteps = enabledSteps.size();
        int processedSteps = 0;
        status("Führe Makro aus: " + macro.getName() + " (" + totalSteps + " aktivierte Schritte)", statusUpdater);

        for (MacroStep step : enabledSteps) {
            processedSteps++;
            status("Schritt " + processedSteps + "/" + totalSteps + ": " + step.getSearchText(), statusUpdater);
            step.setRunning();
            try {
                Pattern pattern = createPatternFromStep(step);
                if (pattern == null) {
                    step.setError("Ungültiges Pattern");
                    continue;
                }
                String replacement = step.getReplaceText() != null ? step.getReplaceText() : "";
                replacement = replacement.replace("\\n", "\n");

                Matcher matcher = pattern.matcher(content);
                int replacements = 0;
                StringBuffer sb = new StringBuffer();
                while (matcher.find()) {
                    replacements++;
                    matcher.appendReplacement(sb, replacement);
                }
                matcher.appendTail(sb);
                content = sb.toString();
                step.addReplacements(replacements);
                step.setCompleted();
            } catch (Exception e) {
                step.setError(e.getMessage());
                status("Fehler in Schritt " + processedSteps + ": " + e.getMessage(), statusUpdater);
            }
        }

        if (!content.equals(originalText)) {
            if (host.getEditorKind() == ChapterEditorHost.EditorKind.CANVAS
                    && host.asCanvasChapterEditor() != null) {
                host.asCanvasChapterEditor().getTextEditor()
                        .replaceAllTextPreservingCaretAndViewport(content);
            } else {
                host.setText(content);
            }
            status("Makro abgeschlossen: " + macro.getName(), statusUpdater);
        } else {
            status("Makro ohne Textänderung abgeschlossen", statusUpdater);
        }
    }

    private static void status(String message, Consumer<String> statusUpdater) {
        if (statusUpdater != null) {
            statusUpdater.accept(message);
        }
    }

    static Pattern createPatternFromStep(MacroStep step) {
        String searchText = step.getSearchText();
        if (searchText == null || searchText.trim().isEmpty()) {
            return null;
        }
        int flags = Pattern.MULTILINE;
        if (!step.isCaseSensitive()) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        if (step.isUseRegex()) {
            String processedSearch = searchText
                    .replace("\\1", "$1")
                    .replace("\\2", "$2")
                    .replace("\\3", "$3");
            return Pattern.compile(processedSearch, flags);
        }
        String escapedSearch = Pattern.quote(searchText);
        if (step.isWholeWord()) {
            escapedSearch = "\\b" + escapedSearch + "\\b";
        }
        return Pattern.compile(escapedSearch, flags);
    }
}
