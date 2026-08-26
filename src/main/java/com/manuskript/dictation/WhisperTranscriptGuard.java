package com.manuskript.dictation;

import com.manuskript.MicrophoneRecorder;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Gegen Whisper-Halluzinationen bei kurzen oder stillen Aufnahmen.
 */
final class WhisperTranscriptGuard {

    /**
     * Unter dieser Dauer keinen Initial-Prompt an Whisper geben:
     * bei wenig Sprache wiederholt das Modell den Prompt statt des Gesprochenen.
     */
    static final double SHORT_CLIP_SEC = 2.5;

    private static final Pattern WS = Pattern.compile("\\s+");

    private static final List<String> HALLUCINATION_MARKERS = List.of(
            "untertitel von",
            "untertitel der",
            "subtitles by",
            "amara.org",
            "thanks for watching",
            "thank you for watching",
            "vielen dank fürs zuschauen",
            "vielen dank fürs schauen",
            "please subscribe",
            "like and subscribe",
            "abonnieren sie den kanal",
            "www.youtube",
            "[musik]",
            "[musik spielt]",
            "[music]",
            "♪"
    );

    private WhisperTranscriptGuard() {
    }

    static boolean isShortClipPcm(int pcmBytes) {
        if (pcmBytes <= 0) {
            return true;
        }
        double seconds = pcmBytes / (MicrophoneRecorder.SAMPLE_RATE * 2.0);
        return seconds < SHORT_CLIP_SEC;
    }

    static boolean isShortClipWav(byte[] wavBytes) {
        if (wavBytes == null || wavBytes.length <= 44) {
            return true;
        }
        return isShortClipPcm(wavBytes.length - 44);
    }

    static String promptForClip(String initialPrompt, byte[] wavBytes) {
        if (isShortClipWav(wavBytes)) {
            return null;
        }
        return initialPrompt;
    }

    static boolean looksLikeHallucination(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return false;
        }
        String normalized = WS.matcher(transcript.toLowerCase(Locale.GERMAN).trim()).replaceAll(" ");
        for (String marker : HALLUCINATION_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return isHighlyRepetitive(normalized);
    }

    /**
     * Wirft, wenn das Transkript klassischer Whisper-Müll ist (YouTube-Outro, Wiederholungsschleife).
     */
    static String requireRealSpeech(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            throw new IllegalStateException("whisper-cli lieferte leere Transkription.");
        }
        String trimmed = transcript.trim();
        if (looksLikeHallucination(trimmed)) {
            throw new IllegalStateException(
                    "Spracherkennung hat keinen sinnvollen Text erkannt (typisch bei sehr kurzen Diktaten). "
                            + "Bitte den Satz etwas langsamer sprechen und die Taste etwas länger halten.");
        }
        return trimmed;
    }

    private static boolean isHighlyRepetitive(String normalized) {
        String[] words = normalized.split(" ");
        if (words.length < 8) {
            return false;
        }
        int unique = 0;
        HashSet<String> seen = new HashSet<>();
        for (String word : words) {
            if (word.length() >= 2 && seen.add(word)) {
                unique++;
            }
        }
        return unique <= 2 && words.length >= 8;
    }
}
