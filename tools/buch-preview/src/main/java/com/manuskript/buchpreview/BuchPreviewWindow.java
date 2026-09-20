package com.manuskript.buchpreview;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.manuskript.plugin.PluginHost;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BuchPreviewWindow {

    private static final Gson GSON = new Gson();
    private static final String VERSION = "1.1.8";

    private final PluginHost host;
    private Stage stage;
    private WebView webView;
    private WebEngine webEngine;
    private ComboBox<HtmlExportFinder.HtmlExport> sourceBox;
    private ComboBox<BookFormat> formatBox;
    private Label statusLabel;
    private Path projectRoot;
    private final List<HtmlExportFinder.HtmlExport> extraSources = new ArrayList<>();
    private TextStats.Counts counts = new TextStats.Counts(0, 0);
    private int lastImageCount;
    private boolean pageReady;
    private boolean applyingPayload;

    public BuchPreviewWindow(PluginHost host) {
        this.host = host;
    }

    public void show() {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            refreshSources();
            return;
        }
        stage = host.createThemedStage("Taschenbuch-Preview " + VERSION);
        host.attachScene(stage, new Scene(buildUi(), 1180, 760));
        applyWindowSize();
        stage.show();
        applyWindowSize();
        Platform.runLater(this::applyWindowSize);
        refreshSources();
    }

    private BorderPane buildUi() {
        sourceBox = new ComboBox<>();
        sourceBox.setPrefWidth(360);
        sourceBox.setMinHeight(32);
        sourceBox.setOnAction(e -> pushToView());

        formatBox = new ComboBox<>();
        formatBox.setPrefWidth(240);
        formatBox.setMinHeight(32);
        formatBox.getItems().addAll(BookFormat.presets());
        formatBox.getSelectionModel().selectFirst();
        formatBox.setOnAction(e -> pushToView());

        Button reload = toolbarButton("Neu laden");
        reload.setOnAction(e -> refreshSources());

        Button pickHtml = toolbarButton("HTML wählen…");
        pickHtml.setOnAction(e -> pickHtmlFile());

        Button cover = viewButton("Cover", "cover");
        Button dummy = viewButton("3D", "dummy");
        Button flip = viewButton("Blättern", "flip");

        statusLabel = new Label("");
        statusLabel.setWrapText(true);
        statusLabel.setMinHeight(32);
        statusLabel.getStyleClass().add("dialog-label");

        HBox row1 = new HBox(10,
                labeled("HTML:", sourceBox),
                labeled("Format:", formatBox),
                pickHtml, reload);
        row1.setAlignment(Pos.CENTER_LEFT);

        HBox row2 = new HBox(10, cover, dummy, flip, statusLabel);
        row2.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        VBox bar = new VBox(8, row1, row2);
        bar.setPadding(new Insets(12));

        webView = new WebView();
        webView.setMinWidth(640);
        webView.setMinHeight(400);
        webEngine = webView.getEngine();
        webEngine.setJavaScriptEnabled(true);
        webView.widthProperty().addListener((obs, old, now) -> fitPreview());
        webView.heightProperty().addListener((obs, old, now) -> fitPreview());
        webEngine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == javafx.concurrent.Worker.State.SUCCEEDED) {
                pageReady = true;
                installBridge();
                if (applyingPayload) {
                    applyingPayload = false;
                    finishEmbeddedLoad();
                    return;
                }
                pushToView();
            }
        });

        URL html = BuchPreviewWindow.class.getResource("/buchpreview/preview.html");
        if (html != null) {
            webEngine.load(html.toExternalForm());
        } else {
            statusLabel.setText("preview.html nicht gefunden.");
        }

        BorderPane root = new BorderPane();
        root.setTop(bar);
        root.setCenter(webView);
        return root;
    }

    private static HBox labeled(String title, javafx.scene.Node node) {
        Label label = new Label(title);
        label.getStyleClass().add("dialog-label");
        HBox box = new HBox(6, label, node);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private Button viewButton(String text, String view) {
        Button button = toolbarButton(text);
        button.setOnAction(e -> showView(view));
        return button;
    }

    private static Button toolbarButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("dialog-button");
        button.setMinHeight(36);
        button.setMinWidth(88);
        button.setStyle("-fx-font-size: 14px; -fx-padding: 6 14;");
        return button;
    }

    private void applyWindowSize() {
        if (stage == null) {
            return;
        }
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double width = Math.min(1180, bounds.getWidth() - 80);
        double height = Math.min(760, bounds.getHeight() - 110);
        width = Math.max(Math.min(960, bounds.getWidth() - 40), width);
        height = Math.max(Math.min(640, bounds.getHeight() - 40), height);
        width = Math.min(width, bounds.getWidth() - 24);
        height = Math.min(height, bounds.getHeight() - 24);
        stage.setMaxWidth(Double.MAX_VALUE);
        stage.setMaxHeight(Double.MAX_VALUE);
        stage.setMinWidth(Math.min(900, bounds.getWidth() - 24));
        stage.setMinHeight(Math.min(600, bounds.getHeight() - 24));
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(bounds.getMinX() + Math.max(12, (bounds.getWidth() - width) / 2));
        stage.setY(bounds.getMinY() + Math.max(12, (bounds.getHeight() - height) / 2));
    }

    private void fitPreview() {
        if (!pageReady || webEngine == null) {
            return;
        }
        try {
            webEngine.executeScript("typeof fitPreview === 'function' && fitPreview()");
        } catch (Exception ignored) {
        }
    }

    private void installBridge() {
        try {
            JSObject window = (JSObject) webEngine.executeScript("window");
            window.setMember("javaBridge", new BuchPreviewBridge(this));
        } catch (Exception e) {
            setStatus("Java-Bridge fehlgeschlagen: " + e.getMessage());
        }
    }

    private void refreshSources() {
        projectRoot = host.projectRoot().orElse(null);
        List<HtmlExportFinder.HtmlExport> exports = new ArrayList<>(HtmlExportFinder.find(projectRoot, true));
        for (HtmlExportFinder.HtmlExport extra : extraSources) {
            boolean exists = exports.stream().anyMatch(e -> e.htmlFile().equals(extra.htmlFile()));
            if (!exists) {
                exports.add(0, extra);
            }
        }
        HtmlExportFinder.HtmlExport previous = sourceBox.getValue();
        sourceBox.getItems().setAll(exports);
        if (exports.isEmpty()) {
            sourceBox.setValue(null);
            ExportMetadata meta = ExportMetadata.load(projectRoot, true);
            if (pageReady) {
                pushEmptyHint(meta);
            }
            String where = ExportMetadata.describeSearch(projectRoot, meta);
            if (!meta.blurb.isBlank()) {
                setStatus("Kein HTML5-Export. Klappentext ist da. " + where);
            } else {
                setStatus("Kein HTML5-Export, kein Klappentext. " + where);
            }
            return;
        }
        boolean keepPicked = previous != null && extraSources.stream()
                .anyMatch(e -> e.htmlFile().equals(previous.htmlFile()));
        if (keepPicked && exports.stream().anyMatch(e -> e.htmlFile().equals(previous.htmlFile()))) {
            sourceBox.setValue(exports.stream()
                    .filter(e -> e.htmlFile().equals(previous.htmlFile()))
                    .findFirst()
                    .orElse(exports.get(0)));
        } else {
            sourceBox.getSelectionModel().selectFirst();
        }
        pushToView();
    }

    private void pickHtmlFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("HTML5-Export wählen");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML", "*.html", "*.htm"));
        if (projectRoot != null) {
            chooser.setInitialDirectory(projectRoot.toFile());
        }
        Path exportHome = Path.of(System.getProperty("user.home", ""), "export");
        if (Files.isDirectory(exportHome)) {
            chooser.setInitialDirectory(exportHome.toFile());
        }
        ExportMetadata meta = ExportMetadata.load(projectRoot, true);
        Path out = meta.resolveOutputDirectory(projectRoot);
        if (out != null) {
            chooser.setInitialDirectory(out.toFile());
        }
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        Path html = file.toPath();
        HtmlExportFinder.HtmlExport picked = new HtmlExportFinder.HtmlExport(html, html.getParent());
        extraSources.removeIf(e -> e.htmlFile().equals(html));
        extraSources.add(0, picked);
        refreshSources();
        sourceBox.setValue(picked);
        pushToView();
    }

    private void pushEmptyHint() {
        pushEmptyHint(ExportMetadata.load(projectRoot, true));
    }

    private void pushEmptyHint(ExportMetadata meta) {
        if (meta == null) {
            meta = new ExportMetadata("", "", "", "", "");
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("bodyHtml",
                "<p>Kein HTML5-Export gefunden. Bitte einmal <strong>Buch exportieren → html5</strong> oder „HTML wählen…“.</p>");
        payload.addProperty("blurb", meta.blurb);
        payload.addProperty("coverDataUri", CoverDataUri.fromFile(meta.resolveCover(projectRoot)));
        payload.addProperty("title", meta.title);
        payload.addProperty("author", meta.author);
        payload.addProperty("authorInfo", meta.authorInfo);
        payload.addProperty("words", 0);
        payload.addProperty("sentences", 0);
        addFormat(payload, formatBox.getValue() == null ? BookFormat.presets().get(0) : formatBox.getValue());
        executeLoad(payload, text(payload, "bodyHtml"));
    }

    private void pushToView() {
        if (!pageReady || webEngine == null) {
            return;
        }
        HtmlExportFinder.HtmlExport export = sourceBox.getValue();
        BookFormat format = formatBox.getValue() == null ? BookFormat.presets().get(0) : formatBox.getValue();
        if (export == null) {
            pushEmptyHint();
            return;
        }
        try {
            String html = Files.readString(export.htmlFile(), StandardCharsets.UTF_8);
            String body = HtmlImageInliner.inline(
                    HtmlBodyExtractor.displayInnerHtml(html), export.folder());
            String plain = HtmlBodyExtractor.plainText(html);
            counts = TextStats.of(plain);
            lastImageCount = HtmlImageInliner.countDataImages(body);
            ExportMetadata meta = ExportMetadata.loadForExport(projectRoot, export.htmlFile());
            Path bookDir = ExportMetadata.findBookDirForHtml(export.htmlFile());
            Path coverRoot = bookDir != null ? bookDir : projectRoot;
            String blurb = meta.blurb;
            if (blurb.isBlank()) {
                blurb = HtmlBodyExtractor.abstractText(html);
            }
            Path cover = meta.resolveCover(coverRoot);
            if (cover == null) {
                cover = ExportMetadata.coverInFolder(export.folder());
            }
            JsonObject payload = new JsonObject();
            payload.addProperty("bodyHtml", body);
            payload.addProperty("blurb", blurb);
            payload.addProperty("title", meta.title);
            payload.addProperty("author", meta.author);
            payload.addProperty("authorInfo", meta.authorInfo);
            payload.addProperty("coverDataUri", CoverDataUri.fromFile(cover));
            payload.addProperty("words", counts.words());
            payload.addProperty("sentences", counts.sentences());
            addFormat(payload, format);
            executeLoad(payload, body);
        } catch (Exception e) {
            setStatus("HTML konnte nicht geladen werden: " + e.getMessage());
        }
    }

    private static void addFormat(JsonObject payload, BookFormat format) {
        JsonObject fmt = new JsonObject();
        fmt.addProperty("id", format.id);
        fmt.addProperty("label", format.label);
        fmt.addProperty("widthMm", format.widthMm);
        fmt.addProperty("heightMm", format.heightMm);
        fmt.addProperty("marginInnerMm", format.marginInnerMm);
        fmt.addProperty("marginOuterMm", format.marginOuterMm);
        fmt.addProperty("marginTopMm", format.marginTopMm);
        fmt.addProperty("marginBottomMm", format.marginBottomMm);
        fmt.addProperty("hardcover", format.hardcover);
        fmt.addProperty("paperMm", format.paperMm);
        fmt.addProperty("coverExtraMm", format.coverExtraMm);
        payload.add("format", fmt);
    }

    private void executeLoad(JsonObject payload, String bodyHtml) {
        try {
            URL resource = BuchPreviewWindow.class.getResource("/buchpreview/preview.html");
            if (resource == null) {
                setStatus("preview.html nicht gefunden.");
                return;
            }
            String template;
            try (InputStream in = resource.openStream()) {
                template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!template.contains("/*BP_BOOT*/")) {
                setStatus("Vorschau-Template ohne Boot-Marke.");
                return;
            }
            applyingPayload = true;
            webEngine.loadContent(template.replace("/*BP_BOOT*/", jsBoot(bodyHtml, payload)), "text/html");
        } catch (Exception e) {
            applyingPayload = false;
            setStatus("Vorschau-Update fehlgeschlagen: " + e.getMessage());
        }
    }

    private String jsBoot(String bodyHtml, JsonObject payload) {
        return "window.bpBodyHtml=" + jsString(bodyHtml) + ";"
                + "window.bpBlurb=" + jsString(text(payload, "blurb")) + ";"
                + "window.bpTitle=" + jsString(text(payload, "title")) + ";"
                + "window.bpAuthor=" + jsString(text(payload, "author")) + ";"
                + "window.bpAuthorInfo=" + jsString(text(payload, "authorInfo")) + ";"
                + "window.bpCover=" + jsString(text(payload, "coverDataUri")) + ";"
                + "window.bpFormatJson=" + jsString(GSON.toJson(payload.get("format"))) + ";";
    }

    private static String jsString(String value) {
        return GSON.toJson(value == null ? "" : value).replace("</", "<\\/");
    }

    private void finishEmbeddedLoad() {
        try {
            Object ready = webEngine.executeScript("typeof loadPreviewFromJava === 'function'");
            if (!Boolean.TRUE.equals(ready)) {
                setStatus("Vorschau-Anzeige nicht bereit.");
                return;
            }
            Object result = webEngine.executeScript("loadPreviewFromJava()");
            if (result instanceof String s && s.startsWith("ERR:")) {
                setStatus("Vorschau: " + s.substring(4));
                return;
            }
            int pages = toInt(result, 1);
            BookFormat format = formatBox.getValue() == null ? BookFormat.presets().get(0) : formatBox.getValue();
            onPaginated(pages, format.spineMm(pages));
            showView("flip");
            Platform.runLater(this::fitPreview);
        } catch (Exception e) {
            setStatus("Vorschau-Update fehlgeschlagen: " + e.getMessage());
        }
    }

    private static String text(JsonObject payload, String key) {
        if (payload == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return "";
        }
        try {
            return payload.get(key).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private void showView(String view) {
        if (!pageReady || webEngine == null) {
            return;
        }
        try {
            Object ready = webEngine.executeScript("typeof showView === 'function'");
            if (Boolean.TRUE.equals(ready)) {
                JSObject window = (JSObject) webEngine.executeScript("window");
                window.call("showView", view);
            }
        } catch (Exception ignored) {
        }
    }

    void onPaginated(int pages, double spineMm) {
        Platform.runLater(() -> {
            BookFormat format = formatBox.getValue() == null ? BookFormat.presets().get(0) : formatBox.getValue();
            double spine = spineMm > 0 ? spineMm : format.spineMm(pages);
            setStatus(counts.words() + " Wörter · " + counts.sentences() + " Sätze · "
                    + pages + " Seiten · " + lastImageCount + " Bilder · Rücken "
                    + Math.round(spine) + " mm · " + format.label);
        });
    }

    private void setStatus(String text) {
        if (statusLabel != null) {
            statusLabel.setText(text == null ? "" : text);
        }
    }
}
