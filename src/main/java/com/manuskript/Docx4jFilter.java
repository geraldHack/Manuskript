package com.manuskript;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

/**
 * TurboFilter um alle org.docx4j Meldungen zu blockieren
 * Blockiert sowohl direkte SLF4J-Logger als auch über JUL-Bridge kommende Meldungen
 */
public class Docx4jFilter extends TurboFilter {
    
    @Override
    public void start() {
        super.start();
        // TurboFilter explizit aktivieren
    }
    
    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        if (logger != null) {
            String loggerName = logger.getName();
            if (loggerName != null) {
                // Prüfe auf vollständigen Namen (org.docx4j.*)
                if (loggerName.startsWith("org.docx4j")) {
                    return FilterReply.DENY;
                }
                
                // Prüfe auf alle docx4j-Varianten (Fallback für ungewöhnliche Namen)
                if (loggerName.contains("docx4j")) {
                    return FilterReply.DENY;
                }
            }
            
            // Zusätzlich: Prüfe auch im Format-String und in Parametern
            // Dies fängt auch Meldungen ab, die über ungewöhnliche Logger kommen
            if (format != null) {
                String formatLower = format.toLowerCase();
                if (formatLower.contains("docx4j") || 
                    formatLower.contains("wordprocessingml") || 
                    formatLower.contains("package read") ||
                    formatLower.contains("elapsed time") ||
                    formatLower.contains("detected wordprocessingml")) {
                    return FilterReply.DENY;
                }
            }
            
            if (params != null) {
                for (Object param : params) {
                    if (param != null) {
                        String paramStr = param.toString().toLowerCase();
                        if (paramStr.contains("docx4j") || paramStr.contains("wordprocessingml")) {
                            return FilterReply.DENY;
                        }
                    }
                }
            }
        }
        return FilterReply.NEUTRAL;
    }
}

