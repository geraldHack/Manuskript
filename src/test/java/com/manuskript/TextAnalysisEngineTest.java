package com.manuskript;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextAnalysisEngineTest {

    private static final String SPRECHWOERTER = "sagte,fragte,stellte fest,flüsterte";

    @Test
    void findetKlassischeSprechantwort() {
        Pattern pattern = TextAnalysisEngine.compileSprechantwortenPattern(
                "(sagte|fragte)\\s+\\w+\\.", SPRECHWOERTER);
        Matcher matcher = pattern.matcher("„Los“, sagte er. Dann ging sie.");
        assertTrue(matcher.find());
        assertEquals("sagte er.", matcher.group());
    }

    @Test
    void findetSprechantwortTrotzZerstoerterPropertiesRegex() {
        String mangled = "(sagte|fragte|rief)s+w+.";
        assertFalse(TextAnalysisEngine.isUsableSprechantwortenRegex(mangled));

        Pattern pattern = TextAnalysisEngine.compileSprechantwortenPattern(mangled, SPRECHWOERTER);
        Matcher matcher = pattern.matcher("„Warum?“, fragte sie: Er schwieg.");
        assertTrue(matcher.find(), "Fallback aus Sprechwörtern muss greifen");
        assertEquals("fragte sie:", matcher.group());
    }

    @Test
    void propertiesLoadDarfBackslashSNichtVerschlucken() throws Exception {
        Properties props = new Properties();
        props.load(new StringReader("sprechantworten_regex=(sagte|fragte)\\\\s+\\\\w+\\\\.\n"));
        String regex = props.getProperty("sprechantworten_regex");
        assertTrue(regex.contains("\\s"), () -> "Geladene Regex war: " + regex);

        Pattern pattern = TextAnalysisEngine.compileSprechantwortenPattern(regex, SPRECHWOERTER);
        assertTrue(pattern.matcher("sagte er.").find());
    }

    @Test
    void einzelnesBackslashSWirdVonPropertiesZuS() throws Exception {
        Properties broken = new Properties();
        broken.load(new StringReader("sprechantworten_regex=(sagte)\\s+\\w+\\.\n"));
        assertEquals("(sagte)s+w+.", broken.getProperty("sprechantworten_regex"));
    }

    @Test
    void analyzeSprechantwortenFindetTrefferImKapiteltext() throws Exception {
        TextAnalysisEngine engine = new TextAnalysisEngine();
        TextAnalysisEngine.AnalysisResult result =
                engine.analyzeSprechantworten("„Los“, sagte er. Sie nickte und fragte ihn:");
        assertFalse(result.spans().isEmpty(), result.summary());
        assertTrue(result.summary().contains("Gefundene Treffer:"));
    }

    @Test
    void findetNamenMitUmlaut() {
        Pattern pattern = TextAnalysisEngine.compileSprechantwortenPattern("", SPRECHWOERTER);
        assertTrue(pattern.matcher("flüsterte Jörg.").find());
    }
}
