package com.manuskript.plugin;

import com.manuskript.AppVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Listet den Plugin-Ordner auf spoteroxe.de und lädt JARs plus gleichnamige {@code .txt}.
 */
public final class PluginCatalogClient {

    private static final Logger logger = LoggerFactory.getLogger(PluginCatalogClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration INDEX_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration JAR_TIMEOUT = Duration.ofSeconds(120);
    private static final long MAX_INDEX_BYTES = 512 * 1024;
    private static final long MAX_NOTES_BYTES = 64 * 1024;
    private static final long MAX_JAR_BYTES = 80L * 1024 * 1024;

    private final HttpClient httpClient;
    private final URI indexUri;

    public PluginCatalogClient() {
        this(defaultClient(), PluginCatalogUrls.indexUri());
    }

    PluginCatalogClient(HttpClient httpClient, URI indexUri) {
        this.httpClient = httpClient;
        this.indexUri = indexUri;
    }

    public static HttpClient defaultClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public RemotePluginIndex fetchIndex() throws IOException, InterruptedException {
        if (!PluginCatalogUrls.isAllowed(indexUri)) {
            throw new IOException("Katalog-URL ist nicht erlaubt");
        }
        URI directory = listingDirectory(indexUri);
        try {
            HttpRequest request = HttpRequest.newBuilder(indexUri)
                    .timeout(INDEX_TIMEOUT)
                    .header("Accept", "text/html,text/plain,*/*")
                    .header("User-Agent", userAgent())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            URI listingUri = response.uri() != null ? response.uri() : indexUri;
            if (response.statusCode() == 200
                    && PluginCatalogUrls.isAllowed(listingUri)
                    && response.body() != null
                    && !response.body().isBlank()
                    && response.body().length() <= MAX_INDEX_BYTES) {
                List<String> names = PluginDirectoryListing.fileNames(response.body());
                if (!names.isEmpty()) {
                    directory = listingDirectory(listingUri);
                    Map<String, PluginNotes> notes = new HashMap<>();
                    for (String name : names) {
                        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                            continue;
                        }
                        String notesName = PluginJarName.notesFileName(name);
                        notes.put(name.toLowerCase(Locale.ROOT), fetchNotes(directory.resolve(notesName)));
                    }
                    RemotePluginIndex listed = RemotePluginIndex.fromListing(directory, names, notes);
                    if (!listed.plugins().isEmpty()) {
                        return listed;
                    }
                }
            }
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            logger.debug("Plugin-Ordner nicht listbar, suche bekannte TXT: {}", e.getMessage());
        }
        return fetchByProbing(directory);
    }

    public File download(RemotePluginIndex.Spec spec, File catalogDir)
            throws IOException, InterruptedException {
        if (spec == null) {
            throw new IllegalArgumentException("Plugin-Eintrag fehlt");
        }
        if (!PluginVersions.meetsRequirement(AppVersion.current(), spec.requires())) {
            throw new IOException("Braucht Manuskript " + spec.requires()
                    + " (aktuell " + AppVersion.current() + ")");
        }
        if (!PluginCatalogUrls.isAllowed(spec.jar())) {
            throw new IOException("Download-URL ist nicht erlaubt");
        }
        Path catalogPath = catalogDir != null ? catalogDir.toPath() : PluginCatalog.catalogDirectory().toPath();
        Files.createDirectories(catalogPath);
        Path temp = Files.createTempFile(catalogPath, "plugin-download-", ".jar");
        try {
            HttpRequest request = HttpRequest.newBuilder(spec.jar())
                    .timeout(JAR_TIMEOUT)
                    .header("Accept", "application/java-archive,application/octet-stream,*/*")
                    .header("User-Agent", userAgent())
                    .GET()
                    .build();
            HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(temp));
            if (response.statusCode() != 200) {
                throw new IOException("Download fehlgeschlagen (HTTP " + response.statusCode() + ")");
            }
            long size = Files.size(temp);
            if (size <= 0) {
                throw new IOException("Download ist leer");
            }
            if (size > MAX_JAR_BYTES) {
                throw new IOException("Download ist zu groß");
            }
            if (spec.sha256() != null && !spec.sha256().isBlank()) {
                String actual = sha256Hex(temp);
                if (!actual.equals(spec.sha256())) {
                    throw new IOException("Prüfsumme stimmt nicht");
                }
            }
            File downloaded = temp.toFile();
            if (!PluginLoader.hasPluginDescriptor(downloaded)) {
                throw new IOException("Datei ist kein Manuskript-Plugin");
            }
            File installed = PluginCatalog.installJar(downloaded, spec.fileName());
            PluginCatalog.installNotes(spec.fileName(), new PluginNotes(
                    spec.label(), spec.version(), spec.requires(), spec.description()).render());
            return installed;
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException e) {
                logger.debug("Temp-JAR nicht gelöscht: {}", temp, e);
            }
        }
    }

    private RemotePluginIndex fetchByProbing(URI directory) {
        URI dir = listingDirectory(directory);
        Set<String> ids = new LinkedHashSet<>(PluginCatalogUrls.OFFICIAL_IDS);
        for (PluginCatalog.Entry entry : PluginCatalog.list()) {
            if (entry.id() != null && PluginCatalogUrls.isAllowedId(entry.id())) {
                ids.add(entry.id());
            }
            PluginJarName.Parsed parsed = PluginJarName.parse(entry.fileName());
            if (parsed != null) {
                ids.add(parsed.id());
            }
        }
        List<String> names = new ArrayList<>();
        Map<String, PluginNotes> notes = new HashMap<>();
        for (String id : ids) {
            PluginNotes best = PluginNotes.empty();
            String bestVersion = "";
            for (String candidate : noteCandidates(id)) {
                PluginNotes parsed = fetchNotes(dir.resolve(candidate));
                if (parsed.label().isBlank() && parsed.version().isBlank() && parsed.description().isBlank()) {
                    continue;
                }
                String version = !parsed.version().isBlank()
                        ? parsed.version()
                        : versionFromNotesFileName(candidate);
                if (version.isBlank()) {
                    continue;
                }
                if (bestVersion.isEmpty() || PluginVersions.compare(bestVersion, version) < 0) {
                    best = parsed;
                    bestVersion = version;
                }
            }
            if (bestVersion.isBlank()) {
                continue;
            }
            String jarName = id + "-" + bestVersion + ".jar";
            names.add(jarName);
            names.add(id + "-" + bestVersion + ".txt");
            notes.put(jarName.toLowerCase(Locale.ROOT), new PluginNotes(
                    best.label(), bestVersion, best.requires(), best.description()));
        }
        return RemotePluginIndex.fromListing(dir, names, notes);
    }

    static List<String> noteCandidates(String id) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (id == null || id.isBlank()) {
            return List.of();
        }
        names.add(id + ".txt");
        String localVersion = localNotesVersion(id);
        if (!localVersion.isBlank()) {
            names.add(id + "-" + localVersion + ".txt");
            for (String next : PluginVersions.successorVersions(localVersion)) {
                names.add(id + "-" + next + ".txt");
            }
        }
        return List.copyOf(names);
    }

    static String localNotesVersion(String id) {
        File catalog = PluginCatalog.catalogDirectory();
        if (catalog == null || id == null) {
            return "";
        }
        String beside = PluginNotes.loadBeside(new File(catalog, id + ".jar").toPath()).version();
        if (!beside.isBlank()) {
            return beside;
        }
        File plugins = PluginCatalog.activeDirectory();
        if (plugins != null) {
            return PluginNotes.loadBeside(new File(plugins, id + ".jar").toPath()).version();
        }
        return "";
    }

    static String versionFromNotesFileName(String fileName) {
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            return "";
        }
        PluginJarName.Parsed parsed = PluginJarName.parse(fileName.substring(0, fileName.length() - 4) + ".jar");
        return parsed == null ? "" : parsed.version();
    }

    private PluginNotes fetchNotes(URI uri) {
        if (!PluginCatalogUrls.isAllowed(uri)) {
            return PluginNotes.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(INDEX_TIMEOUT)
                    .header("Accept", "text/plain,*/*")
                    .header("User-Agent", userAgent())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null) {
                return PluginNotes.empty();
            }
            if (response.body().length() > MAX_NOTES_BYTES) {
                return PluginNotes.parse(response.body().substring(0, (int) MAX_NOTES_BYTES));
            }
            return PluginNotes.parse(response.body());
        } catch (Exception e) {
            logger.debug("Plugin-Notiz nicht geladen: {}", uri, e);
            return PluginNotes.empty();
        }
    }

    private static URI listingDirectory(URI uri) {
        if (uri == null) {
            return PluginCatalogUrls.indexUri();
        }
        String asString = uri.toString();
        if (!asString.endsWith("/")) {
            return URI.create(asString + "/");
        }
        return uri;
    }

    public static String sha256Hex(File file) throws IOException {
        if (file == null) {
            throw new IOException("Datei fehlt");
        }
        return sha256Hex(file.toPath());
    }

    public static String sha256Hex(Path path) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String sha256Hex(byte[] bytes) {
        return HexFormat.of().formatHex(sha256().digest(bytes == null ? new byte[0] : bytes));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 fehlt", e);
        }
    }

    private static String userAgent() {
        return "Manuskript/" + AppVersion.current() + " PluginCatalog";
    }
}
