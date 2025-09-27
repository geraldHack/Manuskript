package com.manuskript;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Universelle Anführungszeichen-Konvertierung für verschiedene Sprachen
 * Unterstützt Deutsche, Französische, Englische und Schweizer Anführungszeichen
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
     * Konvertiert zu deutschen Anführungszeichen: „" und ‚'
     */
    private static String convertToGerman(String text) {
        text = convertToFrench(text);
        
        text = text.replace("»", "\u201E");
        text = text.replace("«", "\u201D");
        text = text.replace("›", "\u201A");
        text = text.replace("‹", "\u2019");
        
        return text;
    }
    
    /**
     * Konvertiert zu französischen Anführungszeichen: »« und ›‹
     */
    private static String convertToFrench(String text) {
        // ERSTER DURCHLAUF: Doppelte Anführungszeichen
        text = convertDoubleQuotationPairs(text, "»", "«");
        
        // ZWEITER DURCHLAUF: Einfache Anführungszeichen
        text = convertSingleQuotationPairs(text, "›", "‹");
        
        // DRITTER DURCHLAUF: Gerade Apostrophe zu einfachen Anführungszeichen (nur wenn Anführungszeichen)
        text = convertStraightApostrophesToQuotations(text, "›", "‹");
        
        // Apostrophe: ' (gerader Apostroph)
        text = text.replaceAll("[\\u2019\\u00B4\\u0060]", "'");
        
        return text;
    }
    
    /**
     * Konvertiert zu englischen Anführungszeichen: "" und ''
     */
    private static String convertToEnglish(String text) {
        // ERSTER DURCHLAUF: Doppelte Anführungszeichen
        text = convertDoubleQuotationPairs(text, "\"", "\"");
        
        // ZWEITER DURCHLAUF: Einfache Anführungszeichen (nur typografische zu geraden)
        text = convertSingleQuotationPairs(text, "'", "'");
        
        // Apostrophe: ' (gerader Apostroph) - nur für echte Apostrophe
        text = text.replaceAll("[\\u2019\\u00B4\\u0060]", "'");
        
        return text;
    }
    
    /**
     * Konvertiert zu schweizer Anführungszeichen: «» und ‹›
     */
    private static String convertToSwiss(String text) {
        // ERSTER DURCHLAUF: Doppelte Anführungszeichen
        text = convertDoubleQuotationPairs(text, "«", "»");
        
        // ZWEITER DURCHLAUF: Einfache Anführungszeichen
        text = convertSingleQuotationPairs(text, "‹", "›");
        
        // DRITTER DURCHLAUF: Gerade Apostrophe zu einfachen Anführungszeichen (nur wenn Anführungszeichen)
        text = convertStraightApostrophesToQuotations(text, "‹", "›");
        
        // Apostrophe: ' (gerader Apostroph)
        text = text.replaceAll("[\\u0027\\u2019\\u00B4\\u0060]", "'");
        
        return text;
    }
    
    /**
     * Konvertiert doppelte Anführungszeichen-Paare
     */
    private static String convertDoubleQuotationPairs(String text, String openChar, String closeChar) {
        // Finde alle doppelten Anführungszeichen
        String pattern = "[\\u201E\\u201C\\u201D\\u00AB\\u00BB\\u0022]";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(text);
        
        List<Integer> positions = new ArrayList<>();
        while (matcher.find()) {
            positions.add(matcher.start());
        }
        
        // Konvertiere Paare: erstes = öffnend, zweites = schließend
        StringBuilder result = new StringBuilder(text);
        for (int i = 0; i < positions.size(); i += 2) {
            if (i + 1 < positions.size()) {
                // Öffnendes Anführungszeichen
                result.setCharAt(positions.get(i), openChar.charAt(0));
                // Schließendes Anführungszeichen
                result.setCharAt(positions.get(i + 1), closeChar.charAt(0));
            }
        }
        
        return result.toString();
    }
    
    /**
     * Konvertiert einfache Anführungszeichen-Paare
     * Unterscheidet zwischen Apostrophen und echten Anführungszeichen
     */
    private static String convertSingleQuotationPairs(String text, String openChar, String closeChar) {
        // Finde alle einfachen Anführungszeichen (inklusive gerade Apostrophe)
        String pattern = "[\\u201A\\u2018\\u2019\\u2039\\u203A\\u0027]";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(text);
        
        List<Integer> positions = new ArrayList<>();
        while (matcher.find()) {
            positions.add(matcher.start());
        }
        
        // Konvertiere Paare: erstes = öffnend, zweites = schließend
        StringBuilder result = new StringBuilder(text);
        for (int i = 0; i < positions.size(); i += 2) {
            if (i + 1 < positions.size()) {
                // Öffnendes Anführungszeichen
                result.setCharAt(positions.get(i), openChar.charAt(0));
                // Schließendes Anführungszeichen
                result.setCharAt(positions.get(i + 1), closeChar.charAt(0));
            }
        }
        
        return result.toString();
    }
    
    
    /**
     * Intelligente Apostroph-Erkennung und Konvertierung
     * 1. Erste Schleife: Finde Apostrophe und ersetze sie durch ApOsTrOpH
     * 2. Zweite Schleife: Behandle alle übrigen ' als Anführungszeichen
     * 3. Dritte Schleife: Ersetze ApOsTrOpH zurück zu '
     */
    private static String convertStraightApostrophesToQuotations(String text, String openChar, String closeChar) {
        // ERSTE SCHLEIFE: Finde Apostrophe und ersetze sie durch ApOsTrOpH
        text = markApostrophes(text);
        
        // ZWEITE SCHLEIFE: Behandle alle übrigen ' als Anführungszeichen
        text = convertRemainingQuotations(text, openChar, closeChar);
        
        // DRITTE SCHLEIFE: Ersetze ApOsTrOpH zurück zu '
        text = text.replace("ApOsTrOpH", "'");
        
        return text;
    }
    
    /**
     * Markiert Apostrophe durch Ersetzung mit ApOsTrOpH
     * Vereinfachte Version - nur die eindeutigsten Apostrophe
     */
    private static String markApostrophes(String text) {
        System.out.println("DEBUG: markApostrophes - Input: " + text);
        
        // Erst alle typographischen Anführungszeichen zu geraden ' konvertieren
        text = text.replace("\u203A", "'"); // ›
        text = text.replace("\u2039", "'"); // ‹
        text = text.replace("\u201A", "'"); // ‚
        text = text.replace("\u2018", "'"); // '
        text = text.replace("\u201C", "'"); // "
        text = text.replace("\u201D", "'"); // "
        
        System.out.println("DEBUG: Nach Konvertierung zu geraden ': " + text);
        
        // Nur die eindeutigsten Apostrophe markieren
        // 1. ' zwischen Buchstaben (z.B. "don't", "I'm")
        String result = text.replaceAll("([a-zA-ZäöüÄÖÜß])'([a-zA-ZäöüÄÖÜß])", "$1ApOsTrOpH$2");
        
        if (!result.equals(text)) {
            System.out.println("DEBUG: Änderung gefunden: " + result);
        } else {
            System.out.println("DEBUG: Keine Änderung");
        }
        
        return result;
    }
    
    /**
     * Intelligente Apostroph-Erkennung für komplexe Fälle
     * Algorithmus: Suche den gesamten Satz, ignoriere von vorne kommend alle Paare von '
     * Ist dann hinten ein "Pärchen" ist es ein Anführungszeichen, ist es einzeln ein Apostroph
     */
    private static String markApostrophesIntelligent(String text) {
        // Finde alle ' im Text
        Pattern pattern = Pattern.compile("'");
        Matcher matcher = pattern.matcher(text);
        
        StringBuilder result = new StringBuilder(text);
        int offset = 0;
        
        while (matcher.find()) {
            int pos = matcher.start();
            
            // Finde den Satz, in dem dieses ' vorkommt
            String sentence = findSentenceContaining(text, pos);
            if (sentence != null) {
                // Analysiere den Satz
                boolean isApostrophe = analyzeApostropheInSentence(sentence, pos);
                
                if (isApostrophe) {
                    // Markiere als Apostroph
                    result.setCharAt(pos + offset, 'A');
                    result.insert(pos + offset + 1, "pOsTrOpH");
                    offset += 8; // "pOsTrOpH".length()
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * Findet den Satz, der die Position enthält
     */
    private static String findSentenceContaining(String text, int pos) {
        // Finde Satzanfang (vorheriger Punkt, Ausrufezeichen, Fragezeichen oder Anfang)
        int start = pos;
        while (start > 0 && !".!?".contains(text.substring(start - 1, start))) {
            start--;
        }
        
        // Finde Satzende (nächster Punkt, Ausrufezeichen, Fragezeichen oder Ende)
        int end = pos;
        while (end < text.length() && !".!?".contains(text.substring(end, end + 1))) {
            end++;
        }
        
        return text.substring(start, end);
    }
    
    /**
     * Analysiert, ob ein ' in einem Satz ein Apostroph oder Anführungszeichen ist
     */
    private static boolean analyzeApostropheInSentence(String sentence, int pos) {
        // Zähle alle ' im Satz
        int count = 0;
        for (char c : sentence.toCharArray()) {
            if (c == '\'') count++;
        }
        
        // Wenn ungerade Anzahl, ist das letzte ein Apostroph
        if (count % 2 == 1) {
            // Finde die Position des letzten '
            int lastPos = sentence.lastIndexOf('\'');
            return pos == lastPos;
        }
        
        // Wenn gerade Anzahl, prüfe ob es in einem Paar ist
        // Vereinfachte Heuristik: Wenn es am Ende eines Wortes steht, ist es ein Apostroph
        return sentence.charAt(pos - 1) != ' ' && sentence.charAt(pos + 1) != ' ';
    }
    
    
    /**
     * Intelligente Apostroph-Erkennung für bessere Unterscheidung
     */
    private static String markApostrophesSmart(String text) {
        // Erste Phase: Markiere eindeutige Apostrophe
        // 1. ' zwischen Buchstaben (z.B. "don't", "I'm")
        text = text.replaceAll("([a-zA-ZäöüÄÖÜß])'([a-zA-ZäöüÄÖÜß])", "$1ApOsTrOpH$2");
        
        // 2. ' nach Buchstabe, vor Leerzeichen oder Satzzeichen (z.B. "John's", "it's")
        text = text.replaceAll("([a-zA-ZäöüÄÖÜß])'([\\s\\p{Punct}])", "$1ApOsTrOpH$2");
        
        // Zweite Phase: Markiere wahrscheinliche Apostrophe am Ende
        // 3. ' am Ende von Wörtern (nur wenn es ein Apostroph ist, nicht Anführungszeichen)
        // Apostrophe am Ende enden meist mit 's' (Genitiv) oder sind bekannte Namen
        text = text.replaceAll("([a-zA-ZäöüÄÖÜß]+)'([\\s\\p{Punct}]|$)", "$1ApOsTrOpH$2");
        
        // Dritte Phase: Markiere Apostrophe vor Anführungszeichen
        // 4. ' nach Buchstabe, vor Anführungszeichen (z.B. "Letos'\"")
        text = text.replaceAll("([a-zA-ZäöüÄÖÜß])'([\\u201E\\u201C\\u201D\\u201A\\u2018\\u2019\\u2039\\u203A\\u00AB\\u00BB])", "$1ApOsTrOpH$2");
        
        return text;
    }
    
    /**
     * Konvertiert alle übrigen ' zu Anführungszeichen
     */
    private static String convertRemainingQuotations(String text, String openChar, String closeChar) {
        // Finde alle Paare von ' die noch nicht konvertiert wurden
        String pattern = "'([^']*)'";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(text);
        
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String content = matcher.group(1);
            String replacement = openChar + content + closeChar;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Findet alle Anführungszeichen im Text und markiert Inkonsistenzen
     */
    public static List<QuotationMark> findQuotationMarks(String text) {
        List<QuotationMark> marks = new ArrayList<>();
        
        // Regex für alle Anführungszeichen
        String pattern = "[\u201E\u201C\u201D\u00AB\u00BB\u201A\u2018\u2019\u2039\u203A\u0027\u00B4\u0060\"]";
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
