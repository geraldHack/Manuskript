package com.manuskript.backup;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupSettingsTest {

    @TempDir
    Path temp;

    @Test
    void migratesLegacySingleDestination() {
        JsonObject obj = new JsonObject();
        obj.addProperty("destination", "/tmp/backups");
        obj.addProperty("compress", false);
        obj.addProperty("encrypt", true);
        obj.addProperty("schedule", "WEEKLY");
        obj.addProperty("keep", 3);
        obj.addProperty("lastBackupFile", "roman.zip");
        BackupSettings settings = BackupSettings.migrateLegacy(obj);
        assertEquals(1, settings.targets.size());
        BackupTarget target = settings.targets.get(0);
        assertEquals("/tmp/backups", target.destination);
        assertFalse(target.compress);
        assertTrue(target.encrypt);
        assertEquals(BackupSchedule.WEEKLY, target.scheduleEnum());
        assertEquals(3, target.keep);
        assertEquals("roman.zip", target.lastBackupFile);
    }

    @Test
    void roundtripTargets() throws Exception {
        Path config = temp.resolve("app");
        BackupSettings settings = new BackupSettings();
        BackupTarget ssh = new BackupTarget();
        ssh.name = "Server";
        ssh.type = BackupKind.SSH.name();
        ssh.sshHost = "example.test";
        ssh.sshUser = "gerd";
        ssh.sshRemotePath = "/backups/manuskript";
        ssh.schedule = BackupSchedule.HOURLY.name();
        settings.targets.add(ssh);
        settings.save(config);
        BackupSettings loaded = BackupSettings.load(config);
        assertEquals(1, loaded.targets.size());
        assertEquals("Server", loaded.targets.get(0).name);
        assertEquals(BackupKind.SSH, loaded.targets.get(0).kind());
        assertEquals("example.test", loaded.targets.get(0).sshHost);
        assertEquals(BackupSchedule.HOURLY, loaded.targets.get(0).scheduleEnum());
    }

    @Test
    void loadsEmptyWhenMissing() {
        BackupSettings settings = BackupSettings.load(temp.resolve("missing"));
        assertTrue(settings.targets.isEmpty());
        assertTrue(Files.notExists(BackupSettings.file(temp.resolve("missing"))));
    }

    @Test
    void replaceByIdUpdatesInsteadOfAppending() throws Exception {
        Path config = temp.resolve("app");
        BackupSettings settings = new BackupSettings();
        BackupTarget first = new BackupTarget();
        first.name = "Alt";
        first.destination = "/tmp/a";
        settings.targets.add(first);
        settings.save(config);

        BackupTarget edited = new BackupTarget();
        edited.id = first.id;
        edited.name = "Neu";
        edited.destination = "/tmp/b";
        BackupSettings again = BackupSettings.load(config);
        again.replaceById(edited);
        again.save(config);

        BackupSettings loaded = BackupSettings.load(config);
        assertEquals(1, loaded.targets.size());
        assertEquals(first.id, loaded.targets.get(0).id);
        assertEquals("Neu", loaded.targets.get(0).name);
        assertEquals("/tmp/b", loaded.targets.get(0).destination);
    }

    @Test
    void kindAndScheduleParseLabels() {
        assertEquals(BackupKind.SSH, BackupKind.fromId("SSH / SCP"));
        assertEquals(BackupKind.FILESYSTEM, BackupKind.fromId("Dateisystem"));
        assertEquals(BackupSchedule.DAILY, BackupSchedule.fromId("Täglich"));
    }

    @Test
    void pluginRequestsBackgroundStart() {
        assertTrue(new ProjektBackupPlugin().wantsBackgroundStart());
    }
}
