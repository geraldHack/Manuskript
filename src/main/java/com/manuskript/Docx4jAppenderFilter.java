package com.manuskript;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Appender-Filter um alle org.docx4j Meldungen zu blockieren
 * Zusätzliche Sicherheitsebene auf Appender-Ebene
 */
public class Docx4jAppenderFilter extends Filter<ILoggingEvent> {
    
    @Override
    public void start() {
        super.start();
        // Filter explizit aktivieren
    }
    
    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event == null) {
            return FilterReply.NEUTRAL;
        }
        
        String loggerName = event.getLoggerName();
        if (loggerName != null) {
            // Prüfe auf vollständigen Namen (org.docx4j.*)
            if (loggerName.startsWith("org.docx4j")) {
                return FilterReply.DENY;
            }
            
            // Prüfe auf alle docx4j-Varianten
            if (loggerName.contains("docx4j")) {
                return FilterReply.DENY;
            }
        }
        
        // Prüfe auch im Nachrichtentext
        String message = event.getFormattedMessage();
        if (message != null) {
            String messageLower = message.toLowerCase();
            if (messageLower.contains("docx4j") || 
                messageLower.contains("wordprocessingml") || 
                messageLower.contains("package read") ||
                messageLower.contains("elapsed time") ||
                messageLower.contains("detected wordprocessingml")) {
                return FilterReply.DENY;
            }
        }
        
        return FilterReply.NEUTRAL;
    }
}

