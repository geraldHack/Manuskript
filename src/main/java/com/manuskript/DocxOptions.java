package com.manuskript;

/**
 * DOCX-Export Optionen für erweiterte Formatierung
 */
public class DocxOptions {
    // Schriftarten
    public String defaultFont = "Calibri";
    public String headingFont = "Calibri";
    public String codeFont = "Consolas";
    public int defaultFontSize = 11;
    public int heading1Size = 18;
    public int heading2Size = 16;
    public int heading3Size = 14;
    
    // Absatzformatierung
    public boolean justifyText = false;  // Standard: Linksbündig
    public boolean enableHyphenation = false;  // Standard: Keine Silbentrennung
    public double lineSpacing = 1.15;
    public double paragraphSpacing = 1.0;
    public boolean firstLineIndent = false;  // Standard: Keine Einrückung erste Zeile
    public double firstLineIndentSize = 1.0;  // Einrückung in cm
    
    // Überschriften
    public boolean centerH1 = false;  // Standard: Linksbündig
    public boolean newPageBeforeH1 = false;  // Standard: Keine neue Seite vor H1
    public boolean newPageBeforeH2 = false;  // Standard: Keine neue Seite vor H2
    public boolean boldHeadings = true;  // Standard: Überschriften fett
    public String headingColor = "2F5496"; // Blau
    
    // Seitenformat
    public double topMargin = 2.5;
    public double bottomMargin = 2.5;
    public double leftMargin = 2.5;
    public double rightMargin = 2.5;
    public boolean includePageNumbers = false;  // Standard: Keine Seitenzahlen
    public String pageNumberPosition = "center"; // left, center, right
    
    // Tabellen
    public boolean tableBorders = false;  // Standard: Keine Rahmen
    public String tableHeaderColor = "E7E6E6";
    public String tableBorderColor = "BFBFBF";
    
    // Code-Blöcke
    public String codeBackgroundColor = "F5F5F5";
    public String codeBorderColor = "D4D4D4";
    public boolean codeLineNumbers = false;
    
    // Blockquotes
    public String quoteBorderColor = "CCCCCC";
    public String quoteBackgroundColor = "F9F9F9";
    public double quoteIndent = 1.0;
    
    // Listen
    public String bulletStyle = "•"; // •, -, *, ◦, ▪
    public boolean listIndentation = false;  // Standard: Keine Einrückung
    public double listIndentSize = 0.5;
    
    // Links
    public String linkColor = "0563C1";
    public boolean underlineLinks = false;  // Standard: Keine Unterstreichung
    
    // Metadaten
    public String documentTitle = "";
    public String documentAuthor = "";
    public String documentSubject = "";
    public String documentKeywords = "";
    public String documentCategory = "";
    
    // Erweiterte Optionen
    public boolean includeTableOfContents = false;
    public boolean autoNumberHeadings = false;
    public boolean protectDocument = false;
    public String protectionPassword = "";
    public boolean trackChanges = false;
    public boolean showHiddenText = false;
    public boolean includeComments = false;
    public String language = "de-DE";
    public String readingLevel = "standard"; // standard, simplified, technical
    
    /**
     * Speichert alle DOCX-Optionen in den User Preferences
     */
    public void saveToPreferences() {
        // Schriftarten
        ResourceManager.saveParameter("docx.defaultFont", defaultFont);
        ResourceManager.saveParameter("docx.headingFont", headingFont);
        ResourceManager.saveParameter("docx.codeFont", codeFont);
        ResourceManager.saveParameter("docx.defaultFontSize", String.valueOf(defaultFontSize));
        ResourceManager.saveParameter("docx.heading1Size", String.valueOf(heading1Size));
        ResourceManager.saveParameter("docx.heading2Size", String.valueOf(heading2Size));
        ResourceManager.saveParameter("docx.heading3Size", String.valueOf(heading3Size));
        
        // Absatzformatierung
        ResourceManager.saveParameter("docx.justifyText", String.valueOf(justifyText));
        ResourceManager.saveParameter("docx.enableHyphenation", String.valueOf(enableHyphenation));
        ResourceManager.saveParameter("docx.lineSpacing", String.valueOf(lineSpacing));
        ResourceManager.saveParameter("docx.paragraphSpacing", String.valueOf(paragraphSpacing));
        ResourceManager.saveParameter("docx.firstLineIndent", String.valueOf(firstLineIndent));
        ResourceManager.saveParameter("docx.firstLineIndentSize", String.valueOf(firstLineIndentSize));
        
        // Überschriften
        ResourceManager.saveParameter("docx.centerH1", String.valueOf(centerH1));
        ResourceManager.saveParameter("docx.newPageBeforeH1", String.valueOf(newPageBeforeH1));
        ResourceManager.saveParameter("docx.newPageBeforeH2", String.valueOf(newPageBeforeH2));
        ResourceManager.saveParameter("docx.boldHeadings", String.valueOf(boldHeadings));
        ResourceManager.saveParameter("docx.headingColor", headingColor);
        
        // Seitenformat
        ResourceManager.saveParameter("docx.topMargin", String.valueOf(topMargin));
        ResourceManager.saveParameter("docx.bottomMargin", String.valueOf(bottomMargin));
        ResourceManager.saveParameter("docx.leftMargin", String.valueOf(leftMargin));
        ResourceManager.saveParameter("docx.rightMargin", String.valueOf(rightMargin));
        ResourceManager.saveParameter("docx.includePageNumbers", String.valueOf(includePageNumbers));
        ResourceManager.saveParameter("docx.pageNumberPosition", pageNumberPosition);
        
        // Tabellen
        ResourceManager.saveParameter("docx.tableBorders", String.valueOf(tableBorders));
        ResourceManager.saveParameter("docx.tableHeaderColor", tableHeaderColor);
        ResourceManager.saveParameter("docx.tableBorderColor", tableBorderColor);
        
        // Code-Blöcke
        ResourceManager.saveParameter("docx.codeBackgroundColor", codeBackgroundColor);
        ResourceManager.saveParameter("docx.codeBorderColor", codeBorderColor);
        ResourceManager.saveParameter("docx.codeLineNumbers", String.valueOf(codeLineNumbers));
        
        // Blockquotes
        ResourceManager.saveParameter("docx.quoteBorderColor", quoteBorderColor);
        ResourceManager.saveParameter("docx.quoteBackgroundColor", quoteBackgroundColor);
        ResourceManager.saveParameter("docx.quoteIndent", String.valueOf(quoteIndent));
        
        // Listen
        ResourceManager.saveParameter("docx.bulletStyle", bulletStyle);
        ResourceManager.saveParameter("docx.listIndentation", String.valueOf(listIndentation));
        ResourceManager.saveParameter("docx.listIndentSize", String.valueOf(listIndentSize));
        
        // Links
        ResourceManager.saveParameter("docx.linkColor", linkColor);
        ResourceManager.saveParameter("docx.underlineLinks", String.valueOf(underlineLinks));
        
        // Metadaten
        ResourceManager.saveParameter("docx.documentTitle", documentTitle);
        ResourceManager.saveParameter("docx.documentAuthor", documentAuthor);
        ResourceManager.saveParameter("docx.documentSubject", documentSubject);
        ResourceManager.saveParameter("docx.documentKeywords", documentKeywords);
        ResourceManager.saveParameter("docx.documentCategory", documentCategory);
        
        // Erweiterte Optionen
        ResourceManager.saveParameter("docx.includeTableOfContents", String.valueOf(includeTableOfContents));
        ResourceManager.saveParameter("docx.autoNumberHeadings", String.valueOf(autoNumberHeadings));
        ResourceManager.saveParameter("docx.protectDocument", String.valueOf(protectDocument));
        ResourceManager.saveParameter("docx.protectionPassword", protectionPassword);
        ResourceManager.saveParameter("docx.trackChanges", String.valueOf(trackChanges));
        ResourceManager.saveParameter("docx.showHiddenText", String.valueOf(showHiddenText));
        ResourceManager.saveParameter("docx.includeComments", String.valueOf(includeComments));
        ResourceManager.saveParameter("docx.language", language);
        ResourceManager.saveParameter("docx.readingLevel", readingLevel);
    }
    
    /**
     * Lädt alle DOCX-Optionen aus den User Preferences
     */
    public void loadFromPreferences() {
        // Schriftarten
        defaultFont = ResourceManager.getParameter("docx.defaultFont", defaultFont);
        headingFont = ResourceManager.getParameter("docx.headingFont", headingFont);
        codeFont = ResourceManager.getParameter("docx.codeFont", codeFont);
        defaultFontSize = ResourceManager.getIntParameter("docx.defaultFontSize", defaultFontSize);
        heading1Size = ResourceManager.getIntParameter("docx.heading1Size", heading1Size);
        heading2Size = ResourceManager.getIntParameter("docx.heading2Size", heading2Size);
        heading3Size = ResourceManager.getIntParameter("docx.heading3Size", heading3Size);
        
        // Absatzformatierung
        justifyText = Boolean.parseBoolean(ResourceManager.getParameter("docx.justifyText", String.valueOf(justifyText)));
        enableHyphenation = Boolean.parseBoolean(ResourceManager.getParameter("docx.enableHyphenation", String.valueOf(enableHyphenation)));
        lineSpacing = ResourceManager.getDoubleParameter("docx.lineSpacing", lineSpacing);
        paragraphSpacing = ResourceManager.getDoubleParameter("docx.paragraphSpacing", paragraphSpacing);
        firstLineIndent = Boolean.parseBoolean(ResourceManager.getParameter("docx.firstLineIndent", String.valueOf(firstLineIndent)));
        firstLineIndentSize = ResourceManager.getDoubleParameter("docx.firstLineIndentSize", firstLineIndentSize);
        
        // Überschriften
        centerH1 = Boolean.parseBoolean(ResourceManager.getParameter("docx.centerH1", String.valueOf(centerH1)));
        newPageBeforeH1 = Boolean.parseBoolean(ResourceManager.getParameter("docx.newPageBeforeH1", String.valueOf(newPageBeforeH1)));
        newPageBeforeH2 = Boolean.parseBoolean(ResourceManager.getParameter("docx.newPageBeforeH2", String.valueOf(newPageBeforeH2)));
        boldHeadings = Boolean.parseBoolean(ResourceManager.getParameter("docx.boldHeadings", String.valueOf(boldHeadings)));
        headingColor = ResourceManager.getParameter("docx.headingColor", headingColor);
        
        // Seitenformat
        topMargin = ResourceManager.getDoubleParameter("docx.topMargin", topMargin);
        bottomMargin = ResourceManager.getDoubleParameter("docx.bottomMargin", bottomMargin);
        leftMargin = ResourceManager.getDoubleParameter("docx.leftMargin", leftMargin);
        rightMargin = ResourceManager.getDoubleParameter("docx.rightMargin", rightMargin);
        includePageNumbers = Boolean.parseBoolean(ResourceManager.getParameter("docx.includePageNumbers", String.valueOf(includePageNumbers)));
        pageNumberPosition = ResourceManager.getParameter("docx.pageNumberPosition", pageNumberPosition);
        
        // Tabellen
        tableBorders = Boolean.parseBoolean(ResourceManager.getParameter("docx.tableBorders", String.valueOf(tableBorders)));
        tableHeaderColor = ResourceManager.getParameter("docx.tableHeaderColor", tableHeaderColor);
        tableBorderColor = ResourceManager.getParameter("docx.tableBorderColor", tableBorderColor);
        
        // Code-Blöcke
        codeBackgroundColor = ResourceManager.getParameter("docx.codeBackgroundColor", codeBackgroundColor);
        codeBorderColor = ResourceManager.getParameter("docx.codeBorderColor", codeBorderColor);
        codeLineNumbers = Boolean.parseBoolean(ResourceManager.getParameter("docx.codeLineNumbers", String.valueOf(codeLineNumbers)));
        
        // Blockquotes
        quoteBorderColor = ResourceManager.getParameter("docx.quoteBorderColor", quoteBorderColor);
        quoteBackgroundColor = ResourceManager.getParameter("docx.quoteBackgroundColor", quoteBackgroundColor);
        quoteIndent = ResourceManager.getDoubleParameter("docx.quoteIndent", quoteIndent);
        
        // Listen
        bulletStyle = ResourceManager.getParameter("docx.bulletStyle", bulletStyle);
        listIndentation = Boolean.parseBoolean(ResourceManager.getParameter("docx.listIndentation", String.valueOf(listIndentation)));
        listIndentSize = ResourceManager.getDoubleParameter("docx.listIndentSize", listIndentSize);
        
        // Links
        linkColor = ResourceManager.getParameter("docx.linkColor", linkColor);
        underlineLinks = Boolean.parseBoolean(ResourceManager.getParameter("docx.underlineLinks", String.valueOf(underlineLinks)));
        
        // Metadaten
        documentTitle = ResourceManager.getParameter("docx.documentTitle", documentTitle);
        documentAuthor = ResourceManager.getParameter("docx.documentAuthor", documentAuthor);
        documentSubject = ResourceManager.getParameter("docx.documentSubject", documentSubject);
        documentKeywords = ResourceManager.getParameter("docx.documentKeywords", documentKeywords);
        documentCategory = ResourceManager.getParameter("docx.documentCategory", documentCategory);
        
        // Erweiterte Optionen
        includeTableOfContents = Boolean.parseBoolean(ResourceManager.getParameter("docx.includeTableOfContents", String.valueOf(includeTableOfContents)));
        autoNumberHeadings = Boolean.parseBoolean(ResourceManager.getParameter("docx.autoNumberHeadings", String.valueOf(autoNumberHeadings)));
        protectDocument = Boolean.parseBoolean(ResourceManager.getParameter("docx.protectDocument", String.valueOf(protectDocument)));
        protectionPassword = ResourceManager.getParameter("docx.protectionPassword", protectionPassword);
        trackChanges = Boolean.parseBoolean(ResourceManager.getParameter("docx.trackChanges", String.valueOf(trackChanges)));
        showHiddenText = Boolean.parseBoolean(ResourceManager.getParameter("docx.showHiddenText", String.valueOf(showHiddenText)));
        includeComments = Boolean.parseBoolean(ResourceManager.getParameter("docx.includeComments", String.valueOf(includeComments)));
        language = ResourceManager.getParameter("docx.language", language);
        readingLevel = ResourceManager.getParameter("docx.readingLevel", readingLevel);
    }
    
    /**
     * Setzt alle Optionen auf Standardwerte zurück
     */
    public void resetToDefaults() {
        // Schriftarten
        defaultFont = "Calibri";
        headingFont = "Calibri";
        codeFont = "Consolas";
        defaultFontSize = 11;
        heading1Size = 18;
        heading2Size = 16;
        heading3Size = 14;
        
        // Absatzformatierung
        justifyText = false;
        enableHyphenation = false;
        lineSpacing = 1.15;
        paragraphSpacing = 1.0;
        firstLineIndent = false;
        firstLineIndentSize = 1.0;
        
        // Überschriften
        centerH1 = false;
        newPageBeforeH1 = false;
        newPageBeforeH2 = false;
        boldHeadings = true;
        headingColor = "2F5496";
        
        // Seitenformat
        topMargin = 2.5;
        bottomMargin = 2.5;
        leftMargin = 2.5;
        rightMargin = 2.5;
        includePageNumbers = false;
        pageNumberPosition = "center";
        
        // Tabellen
        tableBorders = false;
        tableHeaderColor = "E7E6E6";
        tableBorderColor = "BFBFBF";
        
        // Code-Blöcke
        codeBackgroundColor = "F5F5F5";
        codeBorderColor = "D4D4D4";
        codeLineNumbers = false;
        
        // Blockquotes
        quoteBorderColor = "CCCCCC";
        quoteBackgroundColor = "F9F9F9";
        quoteIndent = 1.0;
        
        // Listen
        bulletStyle = "•";
        listIndentation = false;
        listIndentSize = 0.5;
        
        // Links
        linkColor = "0563C1";
        underlineLinks = false;
        
        // Metadaten
        documentTitle = "";
        documentAuthor = "";
        documentSubject = "";
        documentKeywords = "";
        documentCategory = "";
        
        // Erweiterte Optionen
        includeTableOfContents = false;
        autoNumberHeadings = false;
        protectDocument = false;
        protectionPassword = "";
        trackChanges = false;
        showHiddenText = false;
        includeComments = false;
        language = "de-DE";
        readingLevel = "standard";
    }
}
