package com.manuskript.plugin;

import com.manuskript.AppVersion;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Zusammenführung lokaler Katalog-JARs und des Online-Index.
 */
public final class PluginCatalogItem {

    private final PluginCatalog.Entry local;
    private final RemotePluginIndex.Spec remote;
    private final boolean updateAvailable;
    private final boolean compatible;

    private PluginCatalogItem(
            PluginCatalog.Entry local,
            RemotePluginIndex.Spec remote,
            boolean updateAvailable,
            boolean compatible) {
        this.local = local;
        this.remote = remote;
        this.updateAvailable = updateAvailable;
        this.compatible = compatible;
    }

    public PluginCatalog.Entry local() {
        return local;
    }

    public RemotePluginIndex.Spec remote() {
        return remote;
    }

    public boolean updateAvailable() {
        return updateAvailable;
    }

    public boolean compatible() {
        return compatible;
    }

    public boolean canEnable() {
        return local != null && local.catalogFile() != null;
    }

    public boolean canInstall() {
        return remote != null && compatible && (local == null || updateAvailable);
    }

    public String id() {
        if (remote != null) {
            return remote.id();
        }
        return local != null ? local.id() : "";
    }

    public String label() {
        if (remote != null && remote.label() != null && !remote.label().isBlank()) {
            return remote.label();
        }
        return local != null ? local.displayLabel() : id();
    }

    public String description() {
        return remote != null ? nullToEmpty(remote.description()) : "";
    }

    public String fileName() {
        if (remote != null) {
            return remote.fileName();
        }
        return local != null ? local.fileName() : "";
    }

    public String statusText() {
        if (remote != null && !compatible) {
            String need = remote.requires();
            return "Braucht Manuskript " + need + " (aktuell " + AppVersion.current() + ")";
        }
        if (local == null) {
            return "Nicht installiert — kann geladen werden";
        }
        String enabled = local.enabled()
                ? "Aktiv — liegt in plugins/" + local.fileName()
                : "Aus — nur im Katalog (" + local.fileName() + ")";
        if (updateAvailable && remote != null) {
            String version = remote.version() != null && !remote.version().isBlank()
                    ? " (" + remote.version() + ")"
                    : "";
            return enabled + " · Update verfügbar" + version;
        }
        if (remote != null && remote.version() != null && !remote.version().isBlank()) {
            return enabled + " · aktuell " + remote.version();
        }
        return enabled;
    }

    public static List<PluginCatalogItem> merge(List<PluginCatalog.Entry> localEntries, RemotePluginIndex index) {
        Map<String, PluginCatalog.Entry> byId = new LinkedHashMap<>();
        Map<String, PluginCatalog.Entry> byFile = new LinkedHashMap<>();
        if (localEntries != null) {
            for (PluginCatalog.Entry entry : localEntries) {
                if (entry == null) {
                    continue;
                }
                if (entry.id() != null && !entry.id().isBlank()) {
                    byId.putIfAbsent(entry.id(), entry);
                }
                if (entry.fileName() != null) {
                    byFile.putIfAbsent(entry.fileName().toLowerCase(Locale.ROOT), entry);
                }
            }
        }
        List<PluginCatalogItem> items = new ArrayList<>();
        if (index != null) {
            for (RemotePluginIndex.Spec spec : index.plugins()) {
                PluginCatalog.Entry local = byId.remove(spec.id());
                if (local == null) {
                    local = byFile.remove(spec.fileName().toLowerCase(Locale.ROOT));
                    if (local != null && local.id() != null) {
                        byId.remove(local.id());
                    }
                } else {
                    byFile.remove(spec.fileName().toLowerCase(Locale.ROOT));
                    if (local.fileName() != null) {
                        byFile.remove(local.fileName().toLowerCase(Locale.ROOT));
                    }
                }
                boolean compatible = PluginVersions.meetsRequirement(AppVersion.current(), spec.requires());
                boolean update = local != null && hashesDiffer(local, spec);
                items.add(new PluginCatalogItem(local, spec, update, compatible));
            }
        }
        for (PluginCatalog.Entry leftover : byId.values()) {
            if (leftover.fileName() != null) {
                byFile.remove(leftover.fileName().toLowerCase(Locale.ROOT));
            }
            items.add(new PluginCatalogItem(leftover, null, false, true));
        }
        for (PluginCatalog.Entry leftover : byFile.values()) {
            items.add(new PluginCatalogItem(leftover, null, false, true));
        }
        return List.copyOf(items);
    }

    static boolean hashesDiffer(PluginCatalog.Entry local, RemotePluginIndex.Spec spec) {
        if (local == null || spec == null || local.catalogFile() == null || !local.catalogFile().isFile()) {
            return spec != null;
        }
        try {
            String actual = PluginCatalogClient.sha256Hex(local.catalogFile());
            return !actual.equals(spec.sha256());
        } catch (IOException e) {
            return true;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
