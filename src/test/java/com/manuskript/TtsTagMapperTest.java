package com.manuskript;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TtsTagMapperTest {

    @BeforeEach
    void resetCache() {
        TtsTagMapper.resetCacheForTests();
    }

    @Test
    void mapsGermanEmotionAndVoiceTagsToInstruct() {
        TtsTagMapper.PreparedTts prepared = TtsTagMapper.prepareForQwen3("[wütend][tiefe Stimme] »Wo ist mein Schwert?«");
        assertEquals("»Wo ist mein Schwert?«", prepared.cleanText());
        assertTrue(prepared.tagInstruct().contains("angrily"));
        assertTrue(prepared.tagInstruct().contains("deep voice"));
        assertEquals(2, prepared.strippedTags().size());
    }

    @Test
    void stripsSpeakerTagWithoutInstruct() {
        TtsTagMapper.PreparedTts prepared = TtsTagMapper.prepareForQwen3(
                "[Kalem][wütend] »Wo ist mein Schwert?«",
                java.util.Set.of("Kalem"));
        assertEquals("»Wo ist mein Schwert?«", prepared.cleanText());
        assertTrue(prepared.tagInstruct().contains("angrily"));
        assertFalse(prepared.tagInstruct().toLowerCase().contains("kalem"));
    }

    @Test
    void stripsActionTagsWithoutInstruct() {
        TtsTagMapper.PreparedTts prepared = TtsTagMapper.prepareForQwen3("[seufzt] »Hallo.«");
        assertEquals("»Hallo.«", prepared.cleanText());
        assertTrue(prepared.tagInstruct().isBlank());
    }

    @Test
    void leavesPlainTextUntouched() {
        TtsTagMapper.PreparedTts prepared = TtsTagMapper.prepareForQwen3("Er ging nach Hause.");
        assertEquals("Er ging nach Hause.", prepared.cleanText());
        assertTrue(prepared.tagInstruct().isBlank());
    }

    @Test
    void loadsProjectTagConfig() {
        var lookup = TtsTagMapper.loadTagLookup(Paths.get(TtsTagMapper.AUDIO_TAGS_PATH));
        assertTrue(lookup.containsKey("wütend"));
        assertTrue(lookup.containsKey("angry"));
    }

    @Test
    void rejectsHallucinatedSpeakerTag() {
        assertTrue(TtsTagMapper.isLikelyHallucinatedSpeakerTag("Kalem", Set.of("justus", "kata")));
        assertFalse(TtsTagMapper.isLikelyHallucinatedSpeakerTag("wütend", Set.of("justus")));
        assertFalse(TtsTagMapper.isLikelyHallucinatedSpeakerTag("Justus", Set.of("justus")));
    }

    @Test
    void mapsUserExampleTags() {
        TtsTagMapper.PreparedTts prepared = TtsTagMapper.prepareForQwen3("[überrascht][tiefe Stimme][männlich]»Du bist ruhig«");
        assertEquals("»Du bist ruhig«", prepared.cleanText());
        assertTrue(prepared.tagInstruct().contains("surprise") || prepared.tagInstruct().contains("Surprise"));
        assertTrue(prepared.tagInstruct().contains("deep voice"));
        assertEquals(3, prepared.strippedTags().size());
    }

    @Test
    void omitsGenderAndVoiceTraitsWhenFixedSpeaker() {
        TtsTagMapper.PreparedTts prepared = TtsTagMapper.prepareForQwen3(
                "[überrascht][tiefe Stimme][männlich]»Du bist ruhig«", Set.of(), true);
        assertEquals("»Du bist ruhig«", prepared.cleanText());
        assertTrue(prepared.tagInstruct().contains("surprise") || prepared.tagInstruct().contains("Surprise"));
        assertFalse(prepared.tagInstruct().toLowerCase().contains("deep"));
        assertFalse(prepared.tagInstruct().toLowerCase().contains("female"));
        assertFalse(prepared.tagInstruct().toLowerCase().contains("male"));
    }

    @Test
    void buildFixedSpeakerInstructMergesOnceWithoutStimmePrefix() {
        String instruct = ComfyUIClient.buildFixedSpeakerInstruct(
                "Speak with surprise.", "ruhig, klar artikuliert.");
        assertEquals(
                ComfyUIClient.DEFAULT_INSTRUCT_DEUTSCH + " Speak with surprise. ruhig, klar artikuliert.",
                instruct);
        assertFalse(instruct.contains("Stimme:"));
    }

    @Test
    void mergeInstructAppendsTagPart() {
        String merged = TtsTagMapper.mergeInstruct("Deutsch. Neutral.", "Speak angrily.");
        assertEquals("Deutsch. Neutral. Speak angrily.", merged);
    }
}
