package com.manuskript.dictation;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhisperTranscriptGuardTest {

    @Test
    void shortWavSkipsInitialPrompt() {
        byte[] wav = new byte[44 + 22050]; // 0,5 s bei 22050 Hz, 16-bit Mono
        assertTrue(WhisperTranscriptGuard.isShortClipWav(wav));
        assertNull(WhisperTranscriptGuard.promptForClip("Deutsch. Glossar: Kata", wav));
    }

    @Test
    void longerWavKeepsPrompt() {
        byte[] wav = new byte[44 + (int) (22050 * 2 * 4)]; // 4 s
        assertFalse(WhisperTranscriptGuard.isShortClipWav(wav));
        assertTrue(WhisperTranscriptGuard.promptForClip("Deutsch. Glossar: Kata", wav)
                .contains("Kata"));
    }

    @Test
    void echteKurzeSaetzeSindKeinMuell() {
        assertFalse(WhisperTranscriptGuard.looksLikeHallucination("Er nickte."));
        assertFalse(WhisperTranscriptGuard.looksLikeHallucination("Ja."));
        assertFalse(WhisperTranscriptGuard.looksLikeHallucination("Komm her, sagte sie."));
    }

    @Test
    void youtubeOutroIstHalluzination() {
        assertTrue(WhisperTranscriptGuard.looksLikeHallucination(
                "Untertitel von Amara.org"));
        assertTrue(WhisperTranscriptGuard.looksLikeHallucination(
                "Thanks for watching!"));
        assertThrows(IllegalStateException.class,
                () -> WhisperTranscriptGuard.requireRealSpeech("Vielen Dank fürs Zuschauen."));
    }

    @Test
    void wiederholungsschleifeIstHalluzination() {
        assertTrue(WhisperTranscriptGuard.looksLikeHallucination(
                "ja ja ja ja ja ja ja ja ja"));
    }

    @Test
    void whisperCommandSetsAntiHallucinationFlags() {
        List<String> cmd = WhisperRuntime.buildCommand(
                "whisper-cli",
                Path.of("model.bin"),
                Path.of("audio.wav"),
                "de",
                Path.of("out"),
                null);
        assertTrue(cmd.contains("-sns"));
        assertFalse(cmd.contains("-nc"));
        assertTrue(cmd.contains("-tp"));
        assertTrue(cmd.contains("-tpi"));
        assertTrue(cmd.contains("-nth"));
        assertFalse(cmd.contains("--prompt"));
    }

    @Test
    void whisperHelpTextIsNotATranscript() {
        String help = "error: unknown argument: -nc\nusage: whisper-cli [options] file0 file1 ...";
        assertTrue(LocalWhisperBackend.looksLikeCliFailure(help, 1));
        assertTrue(LocalWhisperBackend.looksLikeCliFailure(help, 0));
        assertFalse(LocalWhisperBackend.looksLikeCliFailure("Er ging zur Tür.", 0));
    }
}
