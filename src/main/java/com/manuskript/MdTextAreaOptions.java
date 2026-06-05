package com.manuskript;

import java.util.function.Consumer;

/**
 * Konfiguration für {@link MdTextArea}: Typografie, Anzeige und optionale Toolbar-Bereiche.
 */
public final class MdTextAreaOptions {

    private final String fontFamily;
    private final double fontSize;
    private final double lineSpacing;
    private final double paragraphSpacing;
    private final boolean justifyText;
    private final boolean hideMarkup;
    private final boolean showLineNumbers;
    private final boolean editable;
    private final int themeIndex;
    private final boolean showToolbar;
    private final boolean enableUndoRedo;
    private final boolean enableFontControls;
    private final boolean enableJustify;
    private final boolean enableBasicFormatting;
    private final boolean enableSearch;
    private final boolean enableReplace;
    private final boolean enableHideMarkupToggle;
    private final Consumer<String> onFontFamilyChanged;
    private final Consumer<Double> onFontSizeChanged;
    private final Consumer<Double> onLineSpacingChanged;
    private final Consumer<Integer> onParagraphSpacingChanged;
    private final Consumer<Boolean> onJustifyChanged;
    private final Consumer<Boolean> onHideMarkupChanged;
    private final Consumer<String> onSearchStatus;

    private MdTextAreaOptions(Builder builder) {
        this.fontFamily = builder.fontFamily;
        this.fontSize = builder.fontSize;
        this.lineSpacing = builder.lineSpacing;
        this.paragraphSpacing = builder.paragraphSpacing;
        this.justifyText = builder.justifyText;
        this.hideMarkup = builder.hideMarkup;
        this.showLineNumbers = builder.showLineNumbers;
        this.editable = builder.editable;
        this.themeIndex = builder.themeIndex;
        this.showToolbar = builder.showToolbar;
        this.enableUndoRedo = builder.enableUndoRedo;
        this.enableFontControls = builder.enableFontControls;
        this.enableJustify = builder.enableJustify;
        this.enableBasicFormatting = builder.enableBasicFormatting;
        this.enableSearch = builder.enableSearch;
        this.enableReplace = builder.enableReplace;
        this.enableHideMarkupToggle = builder.enableHideMarkupToggle;
        this.onFontFamilyChanged = builder.onFontFamilyChanged;
        this.onFontSizeChanged = builder.onFontSizeChanged;
        this.onLineSpacingChanged = builder.onLineSpacingChanged;
        this.onParagraphSpacingChanged = builder.onParagraphSpacingChanged;
        this.onJustifyChanged = builder.onJustifyChanged;
        this.onHideMarkupChanged = builder.onHideMarkupChanged;
        this.onSearchStatus = builder.onSearchStatus;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String fontFamily() {
        return fontFamily;
    }

    public double fontSize() {
        return fontSize;
    }

    public double lineSpacing() {
        return lineSpacing;
    }

    public double paragraphSpacing() {
        return paragraphSpacing;
    }

    public boolean justifyText() {
        return justifyText;
    }

    public boolean hideMarkup() {
        return hideMarkup;
    }

    public boolean showLineNumbers() {
        return showLineNumbers;
    }

    public boolean editable() {
        return editable;
    }

    public int themeIndex() {
        return themeIndex;
    }

    public boolean showToolbar() {
        return showToolbar;
    }

    public boolean enableUndoRedo() {
        return enableUndoRedo;
    }

    public boolean enableFontControls() {
        return enableFontControls;
    }

    public boolean enableJustify() {
        return enableJustify;
    }

    public boolean enableBasicFormatting() {
        return enableBasicFormatting;
    }

    public boolean enableSearch() {
        return enableSearch;
    }

    public boolean enableReplace() {
        return enableReplace;
    }

    public boolean enableHideMarkupToggle() {
        return enableHideMarkupToggle;
    }

    public Consumer<String> onFontFamilyChanged() {
        return onFontFamilyChanged;
    }

    public Consumer<Double> onFontSizeChanged() {
        return onFontSizeChanged;
    }

    public Consumer<Double> onLineSpacingChanged() {
        return onLineSpacingChanged;
    }

    public Consumer<Integer> onParagraphSpacingChanged() {
        return onParagraphSpacingChanged;
    }

    public Consumer<Boolean> onJustifyChanged() {
        return onJustifyChanged;
    }

    public Consumer<Boolean> onHideMarkupChanged() {
        return onHideMarkupChanged;
    }

    public Consumer<String> onSearchStatus() {
        return onSearchStatus;
    }

    public static final class Builder {
        private String fontFamily = "Segoe UI";
        private double fontSize = 16.0;
        private double lineSpacing = 1.55;
        private double paragraphSpacing = 0;
        private boolean justifyText = false;
        private boolean hideMarkup = true;
        private boolean showLineNumbers = false;
        private boolean editable = true;
        private int themeIndex = 0;
        private boolean showToolbar = true;
        private boolean enableUndoRedo = false;
        private boolean enableFontControls = false;
        private boolean enableJustify = false;
        private boolean enableBasicFormatting = false;
        private boolean enableSearch = false;
        private boolean enableReplace = false;
        private boolean enableHideMarkupToggle = false;
        private Consumer<String> onFontFamilyChanged;
        private Consumer<Double> onFontSizeChanged;
        private Consumer<Double> onLineSpacingChanged;
        private Consumer<Integer> onParagraphSpacingChanged;
        private Consumer<Boolean> onJustifyChanged;
        private Consumer<Boolean> onHideMarkupChanged;
        private Consumer<String> onSearchStatus;

        public Builder fontFamily(String fontFamily) {
            this.fontFamily = fontFamily;
            return this;
        }

        public Builder fontSize(double fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        public Builder lineSpacing(double lineSpacing) {
            this.lineSpacing = lineSpacing;
            return this;
        }

        public Builder paragraphSpacing(double paragraphSpacing) {
            this.paragraphSpacing = paragraphSpacing;
            return this;
        }

        public Builder justifyText(boolean justifyText) {
            this.justifyText = justifyText;
            return this;
        }

        public Builder hideMarkup(boolean hideMarkup) {
            this.hideMarkup = hideMarkup;
            return this;
        }

        public Builder showLineNumbers(boolean showLineNumbers) {
            this.showLineNumbers = showLineNumbers;
            return this;
        }

        public Builder editable(boolean editable) {
            this.editable = editable;
            return this;
        }

        public Builder themeIndex(int themeIndex) {
            this.themeIndex = themeIndex;
            return this;
        }

        public Builder showToolbar(boolean showToolbar) {
            this.showToolbar = showToolbar;
            return this;
        }

        public Builder enableUndoRedo(boolean enable) {
            this.enableUndoRedo = enable;
            return this;
        }

        public Builder enableFontControls(boolean enable) {
            this.enableFontControls = enable;
            return this;
        }

        public Builder enableJustify(boolean enable) {
            this.enableJustify = enable;
            return this;
        }

        public Builder enableBasicFormatting(boolean enable) {
            this.enableBasicFormatting = enable;
            return this;
        }

        public Builder enableSearch(boolean enable) {
            this.enableSearch = enable;
            return this;
        }

        public Builder enableReplace(boolean enable) {
            this.enableReplace = enable;
            return this;
        }

        public Builder enableHideMarkupToggle(boolean enable) {
            this.enableHideMarkupToggle = enable;
            return this;
        }

        public Builder onFontFamilyChanged(Consumer<String> callback) {
            this.onFontFamilyChanged = callback;
            return this;
        }

        public Builder onFontSizeChanged(Consumer<Double> callback) {
            this.onFontSizeChanged = callback;
            return this;
        }

        public Builder onLineSpacingChanged(Consumer<Double> callback) {
            this.onLineSpacingChanged = callback;
            return this;
        }

        public Builder onParagraphSpacingChanged(Consumer<Integer> callback) {
            this.onParagraphSpacingChanged = callback;
            return this;
        }

        public Builder onJustifyChanged(Consumer<Boolean> callback) {
            this.onJustifyChanged = callback;
            return this;
        }

        public Builder onHideMarkupChanged(Consumer<Boolean> callback) {
            this.onHideMarkupChanged = callback;
            return this;
        }

        public Builder onSearchStatus(Consumer<String> callback) {
            this.onSearchStatus = callback;
            return this;
        }

        public MdTextAreaOptions build() {
            return new MdTextAreaOptions(this);
        }
    }
}
