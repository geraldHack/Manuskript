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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Lädt den offiziellen Plugin-Index und JARs von spoteroxe.de.
 */
public final class PluginCatalogClient {

    private static final Logger logger = LoggerFactory.getLogger(PluginCatalogClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration INDEX_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration JAR_TIMEOUT = Duration.ofSeconds(120);
    private static final long MAX_INDEX_BYTES = 512 * 1024;
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
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public RemotePluginIndex fetchIndex() throws IOException, InterruptedException {
        if (!PluginCatalogUrls.isAllowed(indexUri)) {
            throw new IOException("Katalog-URL ist nicht erlaubt");
        }
        HttpRequest request = HttpRequest.newBuilder(indexUri)
                .timeout(INDEX_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent())
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Katalog nicht erreichbar (HTTP " + response.statusCode() + ")");
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new IOException("Katalog ist leer");
        }
        if (body.length() > MAX_INDEX_BYTES) {
            throw new IOException("Katalog ist zu groß");
        }
        return RemotePluginIndex.parse(body);
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
            String actual = sha256Hex(temp);
            if (!actual.equals(spec.sha256())) {
                throw new IOException("Prüfsumme stimmt nicht");
            }
            File downloaded = temp.toFile();
            if (!PluginLoader.hasPluginDescriptor(downloaded)) {
                throw new IOException("Datei ist kein Manuskript-Plugin");
            }
            return PluginCatalog.installJar(downloaded, spec.fileName());
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException e) {
                logger.debug("Temp-JAR nicht gelöscht: {}", temp, e);
            }
        }
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
