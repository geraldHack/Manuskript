package com.manuskript;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrahiert Regie-Tags {@code […]} aus TTS-Text und mappt sie fuer Qwen3/ComfyUI auf {@code instruct}.
 * ElevenLabs laesst Tags im Text; ComfyUI entfernt sie und uebergibt Stil als instruct.
 */
public final class TtsTagMapper {

    private static final Logger logger = LoggerFactory.getLogger(TtsTagMapper.class);

    public static final String AUDIO_TAGS_PATH = "config/tts-audio-tags.json";

    private static final Pattern BRACKET_TAG = Pattern.compile("\\[[^\\]]+\\]");
    private static final Pattern LEADING_TAGS = Pattern.compile("^(\\[[^\\]]+\\]\\s*)+");
    private static final Pattern TAGS_BEFORE_QUOTE = Pattern.compile("(\\[[^\\]]+\\]\\s*)+(?=[«»\"„\\*])");
    private static final Pattern PROPER_NAME = Pattern.compile("^[A-ZÄÖÜ][a-zäöüßA-ZÄÖÜ\\-]+$");

    private static volatile Map<String, TagEntry> tagLookup = null;

    private TtsTagMapper() {}

    /** Ergebnis: bereinigter Sprechtext + optionaler instruct-Zusatz aus Tags. */
    public record PreparedTts(String cleanText, String tagInstruct, List<String> strippedTags) {
        public static PreparedTts unchanged(String text) {
            return new PreparedTts(text != null ? text : "", "", List.of());
        }
    }

    private record TagEntry(String category, String englishTag) {
        boolean supportsQwenInstruct() {
            return !"breath".equals(category) && !"action".equals(category);
        }
    }

    /**
     * Bereitet Text fuer Qwen3-TTS vor: Tags extrahieren, Sprechtext bereinigen, instruct bauen.
     */
    public static PreparedTts prepareForQwen3(String text) {
        return prepareForQwen3(text, Set.of(), false);
    }

    public static PreparedTts prepareForQwen3(String text, Set<String> knownSpeakerNames) {
        return prepareForQwen3(text, knownSpeakerNames, false);
    }

    /**
     * @param omitFixedSpeakerTraits true bei CustomVoice mit festem Speaker: Geschlecht/Stimmlage-Tags
     *                               nicht in instruct (Speaker-Knoten steuert die Stimme).
     */
    public static PreparedTts prepareForQwen3(String text, Set<String> knownSpeakerNames, boolean omitFixedSpeakerTraits) {
        if (text == null || text.isBlank()) {
            return PreparedTts.unchanged(text);
        }
        ensureLookupLoaded();

        LinkedHashSet<String> collected = new LinkedHashSet<>();
        String working = text.trim();

        Matcher leading = LEADING_TAGS.matcher(working);
        if (leading.find()) {
            collectTagInners(leading.group(), collected);
            working = working.substring(leading.end()).trim();
        }

        Matcher beforeQuote = TAGS_BEFORE_QUOTE.matcher(working);
        StringBuilder sb = new StringBuilder();
        while (beforeQuote.find()) {
            collectTagInners(beforeQuote.group(), collected);
            beforeQuote.appendReplacement(sb, "");
        }
        beforeQuote.appendTail(sb);
        working = sb.toString().replaceAll("\\s+", " ").trim();

        if (collected.isEmpty()) {
            return PreparedTts.unchanged(text.trim());
        }

        List<String> instructParts = new ArrayList<>();
        List<String> stripped = new ArrayList<>();
        Set<String> speakers = normalizeSpeakerSet(knownSpeakerNames);

        for (String inner : collected) {
            stripped.add("[" + inner + "]");
            String normalized = normalizeTagKey(inner);
            if (speakers.contains(normalized)) {
                continue;
            }
            if (omitFixedSpeakerTraits && genderInstruct(inner) != null) {
                continue;
            }
            String genderPhrase = genderInstruct(inner);
            if (genderPhrase != null) {
                instructParts.add(genderPhrase);
                continue;
            }
            TagEntry entry = tagLookup.get(normalized);
            if (omitFixedSpeakerTraits && entry != null && "voice".equals(entry.category())) {
                continue;
            }
            if (entry == null) {
                if (looksLikeSpeakerName(inner)) {
                    continue;
                }
                String fallback = buildFallbackInstruct(inner);
                if (fallback != null) {
                    instructParts.add(fallback);
                }
                continue;
            }
            if (!entry.supportsQwenInstruct()) {
                continue;
            }
            String phrase = toInstructPhrase(entry.category(), entry.englishTag());
            if (phrase != null && !phrase.isBlank()) {
                instructParts.add(phrase);
            }
        }

        String tagInstruct = String.join(" ", instructParts);
        if (!tagInstruct.isBlank()) {
            logger.info("TTS Regie-Tags -> instruct: {} (Tags: {})", tagInstruct, stripped);
        } else if (!stripped.isEmpty()) {
            logger.info("TTS Regie-Tags entfernt (ohne Qwen-instruct): {}", stripped);
        }
        return new PreparedTts(working, tagInstruct, List.copyOf(stripped));
    }

    /** Nur Tags entfernen (Voice Clone: kein instruct moeglich). */
    public static PreparedTts stripRegieTags(String text) {
        PreparedTts prepared = prepareForQwen3(text);
        return new PreparedTts(prepared.cleanText(), "", prepared.strippedTags());
    }

    public static String mergeInstruct(String baseInstruct, String tagInstruct) {
        String base = baseInstruct != null ? baseInstruct.trim() : "";
        String tags = tagInstruct != null ? tagInstruct.trim() : "";
        if (tags.isEmpty()) return base;
        if (base.isEmpty()) return tags;
        return base + " " + tags;
    }

    /** Tag aus tts-audio-tags.json (Emotion, Delivery, Voice, …). */
    public static boolean isKnownRegieTag(String inner) {
        if (inner == null || inner.isBlank()) return false;
        ensureLookupLoaded();
        return tagLookup.containsKey(normalizeTagKey(inner));
    }

    /**
     * Unbekannte Sprecher-Namen aus KI-Antworten verwerfen (z. B. halluziniertes [Kalem]),
     * wenn der Tag nicht in der Sprecherliste steht und wie ein Eigenname aussieht.
     */
    public static boolean isLikelyHallucinatedSpeakerTag(String inner, Set<String> knownSpeakerNameKeys) {
        if (inner == null || inner.isBlank()) return false;
        if (isKnownRegieTag(inner)) return false;
        String key = normalizeTagKey(inner);
        if (knownSpeakerNameKeys != null && knownSpeakerNameKeys.contains(key)) {
            return false;
        }
        return looksLikeSpeakerName(inner);
    }

    /** Fuer Tests: Lookup-Cache zuruecksetzen. */
    static void resetCacheForTests() {
        tagLookup = null;
    }

    private static void collectTagInners(String tagRun, Set<String> out) {
        Matcher m = BRACKET_TAG.matcher(tagRun);
        while (m.find()) {
            String inner = m.group().substring(1, m.group().length() - 1).trim();
            if (!inner.isEmpty()) {
                out.add(inner);
            }
        }
    }

    private static Set<String> normalizeSpeakerSet(Set<String> names) {
        Set<String> out = new LinkedHashSet<>();
        if (names == null) return out;
        for (String n : names) {
            if (n != null && !n.isBlank()) {
                out.add(normalizeTagKey(n));
            }
        }
        return out;
    }

    private static boolean looksLikeSpeakerName(String inner) {
        if (inner == null || inner.isBlank()) return false;
        if (inner.contains(" ") || inner.contains(",")) return false;
        return PROPER_NAME.matcher(inner.trim()).matches();
    }

    private static String normalizeTagKey(String raw) {
        if (raw == null) return "";
        String s = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        if (s.startsWith("[") && s.endsWith("]") && s.length() > 2) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private static String genderInstruct(String inner) {
        if (inner == null || inner.isBlank()) return null;
        return switch (normalizeTagKey(inner)) {
            case "männlich", "maennlich", "male", "m" -> "Use a male voice.";
            case "weiblich", "female", "w" -> "Use a female voice.";
            default -> null;
        };
    }

    private static String buildFallbackInstruct(String inner) {
        String key = normalizeTagKey(inner);
        if (key.isEmpty()) return null;
        if (looksLikeSpeakerName(inner)) return null;
        return "Speak with a " + key + " tone.";
    }

    private static String toInstructPhrase(String category, String englishTag) {
        if (englishTag == null || englishTag.isBlank()) return null;
        String en = englishTag.trim().toLowerCase(Locale.ROOT);
        return switch (category) {
            case "emotion" -> emotionInstruct(en);
            case "delivery" -> deliveryInstruct(en);
            case "voice" -> voiceInstruct(en);
            default -> null;
        };
    }

    private static String emotionInstruct(String en) {
        return switch (en) {
            case "angry" -> "Speak angrily.";
            case "happy" -> "Speak happily.";
            case "sad" -> "Speak sadly.";
            case "scared" -> "Speak fearfully.";
            case "surprised" -> "Speak with surprise.";
            case "excited" -> "Speak excitedly.";
            case "nervously" -> "Speak nervously.";
            case "calm" -> "Speak calmly.";
            case "whisper", "whispering" -> "Speak in a whisper.";
            default -> en.endsWith("ly") ? "Speak " + en + "." : "Speak with a " + en + " tone.";
        };
    }

    private static String deliveryInstruct(String en) {
        return switch (en) {
            case "whisper", "whispering" -> "Speak in a whisper.";
            case "calmly" -> "Speak calmly.";
            case "sternly" -> "Speak sternly.";
            case "softly" -> "Speak softly.";
            case "firmly" -> "Speak firmly.";
            case "quickly" -> "Speak quickly.";
            case "slowly" -> "Speak slowly.";
            case "dramatic" -> "Speak dramatically.";
            case "sarcastic", "ironic" -> "Speak sarcastically.";
            default -> "Speak " + en + ".";
        };
    }

    private static String voiceInstruct(String en) {
        return switch (en) {
            case "low voice" -> "Use a deep voice.";
            case "high voice" -> "Use a high-pitched voice.";
            case "hoarse" -> "Use a hoarse voice.";
            case "rough" -> "Use a rough voice.";
            case "soft" -> "Use a soft voice.";
            case "loud" -> "Use a loud voice.";
            case "quiet" -> "Use a quiet voice.";
            default -> en.contains("voice") ? capitalizeFirst(en) + "." : "Voice quality: " + en + ".";
        };
    }

    private static String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @SuppressWarnings("unchecked")
    private static void ensureLookupLoaded() {
        if (tagLookup != null) return;
        synchronized (TtsTagMapper.class) {
            if (tagLookup != null) return;
            tagLookup = loadTagLookup(Paths.get(AUDIO_TAGS_PATH));
        }
    }

    static Map<String, TagEntry> loadTagLookup(Path path) {
        Map<String, TagEntry> lookup = new HashMap<>();
        if (!Files.isRegularFile(path)) {
            logger.warn("TTS-Audio-Tags nicht gefunden: {}", path.toAbsolutePath());
            return lookup;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<String, Object> root = new Gson().fromJson(reader, new TypeToken<Map<String, Object>>() {}.getType());
            Object categoriesObj = root != null ? root.get("categories") : null;
            if (!(categoriesObj instanceof Map<?, ?> categories)) {
                return lookup;
            }
            for (Map.Entry<?, ?> catEntry : categories.entrySet()) {
                String category = String.valueOf(catEntry.getKey());
                if (!(catEntry.getValue() instanceof Map<?, ?> langMap)) continue;
                Object deObj = langMap.get("de");
                Object enObj = langMap.get("en");
                if (!(deObj instanceof List<?> deList) || !(enObj instanceof List<?> enList)) continue;
                int n = Math.min(deList.size(), enList.size());
                for (int i = 0; i < n; i++) {
                    String enTag = enList.get(i) != null ? enList.get(i).toString().trim() : "";
                    if (enTag.isEmpty()) continue;
                    TagEntry entry = new TagEntry(category, enTag);
                    String deTag = deList.get(i) != null ? deList.get(i).toString().trim() : "";
                    if (!deTag.isEmpty()) {
                        lookup.put(normalizeTagKey(deTag), entry);
                    }
                    lookup.put(normalizeTagKey(enTag), entry);
                }
            }
            logger.debug("TTS-Tag-Lookup geladen: {} Eintraege aus {}", lookup.size(), path);
        } catch (IOException e) {
            logger.warn("TTS-Audio-Tags konnten nicht geladen werden: {}", e.getMessage());
        }
        return lookup;
    }
}
