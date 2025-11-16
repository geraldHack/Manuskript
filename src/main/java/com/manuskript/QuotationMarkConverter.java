package com.manuskript;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Universelle Anführungszeichen-Konvertierung für verschiedene Sprachen
 * Unterstützt Deutsche, Französische, Englische und Schweizer Anführungszeichen
 * 
 * Logik basierend auf dem Makro "Text-Bereinigung":
 * 1. ALLE Anführungszeichen zu englischen normalisieren
 * 2. Paare mit Regex finden und je nach Sprache konvertieren
 */
public class QuotationMarkConverter {
    
    /**
     * Konvertiert alle Anführungszeichen im Text zu einem einheitlichen Stil
     */
    public static String convertQuotationMarks(String text, String targetStyle) {
        if (text == null || targetStyle == null) return text;
        
        String result = text;
        
        // Konvertiere basierend auf dem gewünschten Stil
        switch (targetStyle.toLowerCase()) {
            case "deutsch":
                result = convertToGerman(result);
                break;
            case "französisch":
                result = convertToFrench(result);
                break;
            case "englisch":
                result = convertToEnglish(result);
                break;
            case "schweizer":
                result = convertToSwiss(result);
                break;
        }
        
        return result;
    }
    
    /**
     * SCHRITT 1 & 2: Normalisiert ALLE Anführungszeichen zu englischen
     * Genau wie im Makro: Schritt 1 (doppelte) und Schritt 2 (einfache)
     */
    private static String convertToEnglish(String text) {
        // Schritt 1: ALLE Unicode doppelte Anführungszeichen zu englisch
        // Pattern: [\u201C\u201D\u201E\u201F\u00AB\u00BB]
        text = text.replaceAll("[\\u201C\\u201D\\u201E\\u201F\\u00AB\\u00BB]", "\"");
        
        // Schritt 2: ALLE Unicode einfache Anführungszeichen zu englisch
        // Pattern: [\u2018\u2019\u201A\u201B\u2039\u203A]
        // WICHTIG: U+2019 kann sowohl typographisches Apostroph als auch schließendes Anführungszeichen sein
        // Wir normalisieren es zu ', die spätere Logik unterscheidet dann zwischen Apostroph und Anführungszeichen
        text = text.replaceAll("[\\u2018\\u2019\\u201A\\u201B\\u2039\\u203A]", "'");
        
        return text;
    }
    
    /**
     * Konvertiert zu deutschen Anführungszeichen: „" und ‚'
     * Makro-Regeln: Schritt 14 (doppelte) und Schritt 15 (einfache)
     */
    private static String convertToGerman(String text) {
        // ERSTER DURCHLAUF: Alle zu englischen Anführungszeichen
        text = convertToEnglish(text);
        
        // SCHRITT 1: Konvertiere doppelte Anführungszeichen
        // Makro-Regel 14: "(.*?)" -> „$1"
        // WICHTIG: Das schließende Anführungszeichen ist " (U+201C - deutsches typografisches schließendes Anführungszeichen)
        text = text.replaceAll("\"(.*?)\"", "\u201E$1\u201C");
        
        // SCHRITT 2: Konvertiere einfache Anführungszeichen-Paare
        // Makro-Regel 15: '(.*?)' -> ‚$1'
        // WICHTIG: Nur Paare finden, die NICHT direkt an Buchstaben grenzen (keine Apostrophe)
        // Das öffnende ' muss am Textanfang, nach Leerzeichen/Satzzeichen oder nach Anführungszeichen stehen
        // Das schließende ' muss vor Leerzeichen/Satzzeichen, vor Anführungszeichen oder am Textende stehen
        // Öffnend: U+201A (‚), Schließend: U+2019 (')
        text = text.replaceAll("(^|[\\s\\.,!?;:\"„»«])'([^']+)'([\\s\\.,!?;:\"„»«]|$)", "$1\u201A$2\u2019$3");
        
        return text;
    }
    
    /**
     * Konvertiert zu französischen Anführungszeichen: »« und ›‹
     * Makro-Regeln: Schritt 12 (doppelte) und Schritt 13 (einfache)
     */
    private static String convertToFrench(String text) {
        // ERSTER DURCHLAUF: Alle zu englischen Anführungszeichen
        text = convertToEnglish(text);
        
        // SCHRITT 1: Konvertiere doppelte Anführungszeichen
        // Makro-Regel 12: "(.*?)" -> »$1«
        text = text.replaceAll("\"(.*?)\"", "»$1«");
        
        // SCHRITT 2: Konvertiere einfache Anführungszeichen-Paare
        // Makro-Regel 13: '(.*?)' -> ›$1‹
        // WICHTIG: Nur Paare finden, die NICHT direkt an Buchstaben grenzen (keine Apostrophe)
        // Das öffnende ' muss am Textanfang, nach Leerzeichen/Satzzeichen oder nach Anführungszeichen stehen
        // Das schließende ' muss vor Leerzeichen/Satzzeichen, vor Anführungszeichen oder am Textende stehen
        text = text.replaceAll("(^|[\\s\\.,!?;:\"„»«])'([^']+)'([\\s\\.,!?;:\"„»«]|$)", "$1›$2‹$3");
        
        return text;
    }
    
    /**
     * Konvertiert zu schweizer Anführungszeichen: «» und ‹›
     * Makro-Regeln: Schritt 16 (doppelte) und Schritt 17 (einfache)
     */
    private static String convertToSwiss(String text) {
        // ERSTER DURCHLAUF: Alle zu englischen Anführungszeichen
        text = convertToEnglish(text);
        
        // SCHRITT 1: Konvertiere doppelte Anführungszeichen
        // Makro-Regel 16: "(.*?)" -> «$1»
        text = text.replaceAll("\"(.*?)\"", "«$1»");
        
        // SCHRITT 2: Konvertiere einfache Anführungszeichen-Paare
        // Makro-Regel 17: '(.*?)' -> ‹$1›
        // WICHTIG: Nur Paare finden, die NICHT direkt an Buchstaben grenzen (keine Apostrophe)
        // Das öffnende ' muss am Textanfang, nach Leerzeichen/Satzzeichen oder nach Anführungszeichen stehen
        // Das schließende ' muss vor Leerzeichen/Satzzeichen, vor Anführungszeichen oder am Textende stehen
        text = text.replaceAll("(^|[\\s\\.,!?;:\"„»«])'([^']+)'([\\s\\.,!?;:\"„»«]|$)", "$1‹$2›$3");
        
        return text;
    }
    
    /**
     * Findet alle Anführungszeichen im Text und markiert Inkonsistenzen
     */
    public static List<QuotationMark> findQuotationMarks(String text) {
        List<QuotationMark> marks = new ArrayList<>();
        
        // Regex für alle Anführungszeichen
        String pattern = "[\u201E\u201C\u201D\u00AB\u00BB\u201A\u2018\u2019\u2039\u203A\u0027\"]";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(text);
        
        while (matcher.find()) {
            String mark = matcher.group();
            int position = matcher.start();
            
            marks.add(new QuotationMark(mark, position, "quotation-mark"));
        }
        
        return marks;
    }
    
    /**
     * Erkennt Inkonsistenzen in Anführungszeichen-Paaren
     */
    public static List<Inconsistency> findInconsistencies(String text) {
        List<Inconsistency> inconsistencies = new ArrayList<>();
        List<QuotationMark> marks = findQuotationMarks(text);
        
        // Einfache Inkonsistenz-Erkennung: ungerade Anzahl von Anführungszeichen
        if (marks.size() % 2 != 0) {
            for (QuotationMark mark : marks) {
                inconsistencies.add(new Inconsistency(mark, "Ungerade Anzahl von Anführungszeichen"));
            }
        }
        
        return inconsistencies;
    }
    
    /**
     * Repräsentiert ein gefundenes Anführungszeichen
     */
    public static class QuotationMark {
        public final String mark;
        public final int position;
        public final String type;
        
        public QuotationMark(String mark, int position, String type) {
            this.mark = mark;
            this.position = position;
            this.type = type;
        }
    }
    
    /**
     * Repräsentiert eine Inkonsistenz
     */
    public static class Inconsistency {
        public final QuotationMark mark;
        public final String reason;
        
        public Inconsistency(QuotationMark mark, String reason) {
            this.mark = mark;
            this.reason = reason;
        }
    }
}
