package com.manuskript;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Erkennung von Markdown-Blockstrukturen für den Canvas-Editor (Listen, Tabellen, Code-Fences).
 */
public final class MarkdownBlockSupport {

    private static final Pattern TASK_LIST = Pattern.compile("^(\\s*)([-*+])\\s+\\[([ xX])\\]\\s+(.*)$");
    private static final Pattern ORDERED_LIST = Pattern.compile("^(\\s*)(\\d+)\\.\\s+(.*)$");
    private static final Pattern UNORDERED_LIST = Pattern.compile("^(\\s*)([-*+])\\s+(.*)$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\s*\\|(.+)\\|\\s*$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$");
    private static final Pattern FENCE_OPEN = Pattern.compile("^```(\\w*)\\s*$");
    private static final Pattern FENCE_CLOSE = Pattern.compile("^```\\s*$");

    private MarkdownBlockSupport() {
    }

    public enum ListKind {
        UNORDERED, ORDERED, TASK
    }

    public record ListLineInfo(
            int lineStart,
            int prefixEnd,
            int contentStart,
            int nestLevel,
            ListKind kind,
            int orderNumber,
            boolean taskChecked,
            char bulletChar) {
    }

    public record TableLineInfo(int lineStart, int lineEnd, boolean separator) {
    }

    public record TableCell(String text, int sourceStart, int sourceEnd) {
    }

    public record TableRow(int lineStart, int lineEnd, boolean separator, List<TableCell> cells) {
    }

    public record TableBlock(int startLine, int endLine, List<TableRow> rows) {
    }

    public record CodeFenceBlock(int start, int end, int contentStart, int contentEnd) {
    }

    public static List<ListLineInfo> parseListLines(String text) {
        List<ListLineInfo> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && text.charAt(i) != '\n') {
                continue;
            }
            int lineEnd = i;
            String line = normalizeTableLine(text.substring(lineStart, lineEnd));
            ListLineInfo info = parseListLine(lineStart, line);
            if (info != null) {
                result.add(info);
            }
            lineStart = i + 1;
        }
        return result;
    }

    private static ListLineInfo parseListLine(int lineStart, String line) {
        line = normalizeTableLine(line);
        Matcher task = TASK_LIST.matcher(line);
        if (task.matches()) {
            int nestLevel = nestLevelFromSpaces(task.group(1));
            int prefixEnd = lineStart + task.start(4);
            int contentStart = lineStart + task.start(4);
            boolean checked = task.group(3) != null && !task.group(3).isBlank()
                    && Character.toLowerCase(task.group(3).charAt(0)) == 'x';
            char bullet = task.group(2).charAt(0);
            return new ListLineInfo(lineStart, prefixEnd, contentStart, nestLevel,
                    ListKind.TASK, 0, checked, bullet);
        }
        Matcher ordered = ORDERED_LIST.matcher(line);
        if (ordered.matches()) {
            int nestLevel = nestLevelFromSpaces(ordered.group(1));
            int prefixEnd = lineStart + ordered.start(3);
            int contentStart = lineStart + ordered.start(3);
            int orderNumber;
            try {
                orderNumber = Integer.parseInt(ordered.group(2));
            } catch (NumberFormatException e) {
                orderNumber = 1;
            }
            return new ListLineInfo(lineStart, prefixEnd, contentStart, nestLevel,
                    ListKind.ORDERED, orderNumber, false, '.');
        }
        Matcher unordered = UNORDERED_LIST.matcher(line);
        if (unordered.matches()) {
            int nestLevel = nestLevelFromSpaces(unordered.group(1));
            int prefixEnd = lineStart + unordered.start(3);
            int contentStart = lineStart + unordered.start(3);
            char bullet = unordered.group(2).charAt(0);
            return new ListLineInfo(lineStart, prefixEnd, contentStart, nestLevel,
                    ListKind.UNORDERED, 0, false, bullet);
        }
        return null;
    }

    /** 2 Leerzeichen oder 1 Tab (= 4 Spalten) = eine Verschachtelungsebene. */
    private static int nestLevelFromSpaces(String spaces) {
        return leadingIndentWidth(spaces) / 2;
    }

    static int leadingIndentWidth(String spaces) {
        if (spaces == null || spaces.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < spaces.length(); i++) {
            char c = spaces.charAt(i);
            if (c == ' ') {
                width++;
            } else if (c == '\t') {
                width += 4;
            } else {
                break;
            }
        }
        return width;
    }

    /**
     * Fortlaufende Nummern für nummerierte Listen (1., 2., 3. …) pro Verschachtelungsebene.
     * Lazy-Nummerierung in der Quelle ({@code 1.} auf jeder Zeile) wird für die Anzeige korrigiert.
     */
    public static Map<Integer, Integer> computeOrderedDisplayNumbers(String text, List<ListLineInfo> listLines) {
        Map<Integer, Integer> result = new HashMap<>();
        if (text == null || text.isEmpty() || listLines == null || listLines.isEmpty()) {
            return result;
        }
        List<ListLineInfo> ordered = listLines.stream()
                .filter(info -> info.kind() == ListKind.ORDERED)
                .sorted(Comparator.comparingInt(ListLineInfo::lineStart))
                .toList();
        if (ordered.isEmpty()) {
            return result;
        }
        int[] counters = new int[16];
        int lastLineEnd = -1;
        for (ListLineInfo info : ordered) {
            if (lastLineEnd >= 0 && breaksOrderedListContinuity(text, lastLineEnd, info.lineStart())) {
                Arrays.fill(counters, 0);
            }
            int level = Math.min(info.nestLevel(), counters.length - 1);
            for (int lv = level + 1; lv < counters.length; lv++) {
                counters[lv] = 0;
            }
            counters[level]++;
            result.put(info.lineStart(), counters[level]);
            lastLineEnd = lineEndIndex(text, info.lineStart());
        }
        return result;
    }

    private static int lineEndIndex(String text, int lineStart) {
        int lineEnd = text.indexOf('\n', lineStart);
        return lineEnd < 0 ? text.length() : lineEnd;
    }

    /** Nicht-Listen-Zeilen zwischen zwei nummerierten Einträgen beenden die laufende Liste. */
    private static boolean breaksOrderedListContinuity(String text, int afterPrevLineEnd, int nextLineStart) {
        int pos = afterPrevLineEnd;
        if (pos < text.length() && text.charAt(pos) == '\n') {
            pos++;
        }
        while (pos < nextLineStart) {
            int lineEnd = text.indexOf('\n', pos);
            if (lineEnd < 0 || lineEnd > nextLineStart) {
                lineEnd = nextLineStart;
            }
            String line = normalizeTableLine(text.substring(pos, lineEnd));
            if (!line.trim().isEmpty() && parseListLine(pos, line) == null) {
                return true;
            }
            pos = lineEnd + 1;
        }
        return false;
    }

    /**
     * Schreibt fortlaufende Nummern ({@code 1.}, {@code 2.}, …) pro Ebene in die Markdown-Quelle.
     */
    public static String renumberOrderedListMarkers(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        List<ListLineInfo> listLines = parseListLines(text);
        Map<Integer, Integer> display = computeOrderedDisplayNumbers(text, listLines);
        if (display.isEmpty()) {
            return text;
        }
        Map<Integer, ListLineInfo> orderedByStart = new HashMap<>();
        for (ListLineInfo info : listLines) {
            if (info.kind() == ListKind.ORDERED) {
                orderedByStart.put(info.lineStart(), info);
            }
        }
        StringBuilder out = new StringBuilder(text.length());
        int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && text.charAt(i) != '\n') {
                continue;
            }
            int lineEnd = i;
            ListLineInfo info = orderedByStart.get(lineStart);
            boolean rewritten = false;
            if (info != null) {
                Integer newNum = display.get(lineStart);
                if (newNum != null && newNum != info.orderNumber()) {
                    String line = normalizeTableLine(text.substring(lineStart, lineEnd));
                    Matcher matcher = ORDERED_LIST.matcher(line);
                    if (matcher.matches()) {
                        out.append(matcher.group(1)).append(newNum).append(". ").append(matcher.group(3));
                        rewritten = true;
                    }
                }
            }
            if (!rewritten) {
                out.append(text, lineStart, lineEnd);
            }
            if (lineEnd < text.length()) {
                out.append('\n');
            }
            lineStart = i + 1;
        }
        return out.toString();
    }

    public static List<TableLineInfo> parseTableLines(String text) {
        List<TableLineInfo> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && text.charAt(i) != '\n') {
                continue;
            }
            int lineEnd = i;
            String line = normalizeTableLine(text.substring(lineStart, lineEnd));
            if (TABLE_SEPARATOR.matcher(line).matches()) {
                result.add(new TableLineInfo(lineStart, lineEnd, true));
            } else if (TABLE_ROW.matcher(line).matches()) {
                result.add(new TableLineInfo(lineStart, lineEnd, false));
            }
            lineStart = i + 1;
        }
        return result;
    }

    public static TableRow parseTableRow(int lineStart, String line) {
        String normalized = normalizeTableLine(line);
        return parseTableRow(lineStart, lineStart + normalized.length(), line);
    }

    public static TableRow parseTableRow(int lineStart, int lineEnd, String line) {
        if (line == null) {
            return null;
        }
        line = normalizeTableLine(line);
        String trimmed = line.trim();
        if (TABLE_SEPARATOR.matcher(line).matches()) {
            return new TableRow(lineStart, lineEnd, true, List.of());
        }
        if (!TABLE_ROW.matcher(line).matches()) {
            return null;
        }
        if (!trimmed.startsWith("|")) {
            return null;
        }
        String inner = trimmed.substring(1);
        if (inner.endsWith("|")) {
            inner = inner.substring(0, inner.length() - 1);
        }
        String[] parts = inner.split("\\|", -1);
        List<TableCell> cells = new ArrayList<>();
        int scan = line.indexOf('|');
        if (scan < 0) {
            return null;
        }
        scan++;
        for (String part : parts) {
            int cellStartInLine = line.indexOf(part, Math.max(0, scan - 1));
            if (cellStartInLine < 0) {
                cellStartInLine = scan;
            }
            int contentStart = lineStart + cellStartInLine;
            int contentEnd = contentStart + part.length();
            cells.add(new TableCell(part.trim(), contentStart, contentEnd));
            scan = cellStartInLine + part.length() + 1;
        }
        if (cells.isEmpty()) {
            return null;
        }
        return new TableRow(lineStart, lineEnd, false, cells);
    }

    public static List<TableBlock> parseTableBlocks(String text) {
        List<TableBlock> blocks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return blocks;
        }
        List<TableRow> currentRows = new ArrayList<>();
        int blockStart = -1;
        int previousLineEnd = -1;
        int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && text.charAt(i) != '\n') {
                continue;
            }
            int lineEnd = i;
            String line = normalizeTableLine(text.substring(lineStart, lineEnd));
            if (line.trim().isEmpty() && !currentRows.isEmpty()) {
                previousLineEnd = lineEnd;
                lineStart = i + 1;
                continue;
            }
            TableRow row = parseTableRow(lineStart, lineEnd, line);
            if (row != null && (previousLineEnd < 0 || lineStart == previousLineEnd + 1)) {
                if (currentRows.isEmpty()) {
                    blockStart = lineStart;
                }
                currentRows.add(row);
                previousLineEnd = lineEnd;
            } else {
                if (!currentRows.isEmpty()) {
                    blocks.add(new TableBlock(blockStart, previousLineEnd, List.copyOf(currentRows)));
                    currentRows.clear();
                }
                blockStart = -1;
                previousLineEnd = -1;
                if (row != null) {
                    blockStart = lineStart;
                    currentRows.add(row);
                    previousLineEnd = lineEnd;
                }
            }
            lineStart = i + 1;
        }
        if (!currentRows.isEmpty()) {
            blocks.add(new TableBlock(blockStart, previousLineEnd, List.copyOf(currentRows)));
        }
        return blocks;
    }

    public static List<CodeFenceBlock> parseCodeFences(String text) {
        List<CodeFenceBlock> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        int lineStart = 0;
        boolean inFence = false;
        int blockStart = -1;
        int contentStart = -1;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && text.charAt(i) != '\n') {
                continue;
            }
            int lineEnd = i;
            String line = text.substring(lineStart, lineEnd).trim();
            if (!inFence) {
                if (FENCE_OPEN.matcher(line).matches()) {
                    inFence = true;
                    blockStart = lineStart;
                    contentStart = i < text.length() ? i + 1 : i;
                }
            } else {
                if (FENCE_CLOSE.matcher(line).matches()) {
                    int blockEnd = lineEnd;
                    int contentEnd = lineStart;
                    if (contentEnd > contentStart) {
                        result.add(new CodeFenceBlock(blockStart, blockEnd, contentStart, contentEnd));
                    } else {
                        result.add(new CodeFenceBlock(blockStart, blockEnd, contentStart, contentStart));
                    }
                    inFence = false;
                    blockStart = -1;
                    contentStart = -1;
                }
            }
            lineStart = i + 1;
        }
        if (inFence && blockStart >= 0) {
            result.add(new CodeFenceBlock(blockStart, text.length(), contentStart, text.length()));
        }
        return result;
    }

    /** Schnellcheck: Block-Parser nur bei relevanten Zeichen laufen lassen. */
    public static boolean mightHaveBlockStructures(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.contains("```")
                || mightHaveTableLines(text)
                || text.contains("\n- ")
                || text.contains("\n* ")
                || text.contains("\n+ ")
                || text.startsWith("- ")
                || text.startsWith("* ")
                || text.startsWith("+ ")
                || text.contains("\n1. ")
                || text.contains("\n- [")
                || text.contains("\n* [");
    }

    /** Nur echte Tabellenzeilen (|…|), nicht jedes Pipe-Zeichen im Fließtext. */
    private static boolean mightHaveTableLines(String text) {
        if (!text.contains("|")) {
            return false;
        }
        int searchFrom = 0;
        while (searchFrom < text.length()) {
            int pipe = text.indexOf('|', searchFrom);
            if (pipe < 0) {
                return false;
            }
            int lineStart = text.lastIndexOf('\n', pipe) + 1;
            int lineEnd = text.indexOf('\n', pipe);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            String line = text.substring(lineStart, lineEnd);
            if (TABLE_ROW.matcher(line).matches() || TABLE_SEPARATOR.matcher(line).matches()) {
                return true;
            }
            searchFrom = lineEnd + 1;
        }
        return false;
    }

    /** Tabellenzeile oder Trennzeile (|…| / |---|), nicht Horizontale Regel. */
    public static boolean isTableMarkdownLine(String line) {
        if (line == null) {
            return false;
        }
        return parseTableRow(0, normalizeTableLine(line)) != null;
    }

    public static String normalizeTableLine(String line) {
        if (line == null || line.isEmpty()) {
            return line == null ? "" : line;
        }
        return line.replace("\r", "");
    }

    public static boolean mightHaveHorizontalRules(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && text.charAt(i) != '\n') {
                continue;
            }
            String line = normalizeTableLine(text.substring(lineStart, i)).trim();
            if (!isTableMarkdownLine(line)
                    && line.matches("^(?:\\*{3,}|-{3,}|_{3,})$")) {
                return true;
            }
            lineStart = i + 1;
        }
        return false;
    }
}
