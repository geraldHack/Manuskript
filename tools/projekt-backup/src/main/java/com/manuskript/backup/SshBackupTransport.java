package com.manuskript.backup;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.xfer.FileSystemFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * SCP/SFTP-Upload eines lokalen Archivs.
 */
public final class SshBackupTransport {

    private SshBackupTransport() {
    }

    public static String upload(Path localFile, BackupTarget target) throws IOException {
        if (localFile == null || !Files.isRegularFile(localFile)) {
            throw new IllegalArgumentException("Lokale Backup-Datei fehlt");
        }
        requireSsh(target);
        String remoteDir = target.sshRemotePath.trim();
        String remoteFile = remoteDir.replaceAll("/+$", "") + "/" + localFile.getFileName();
        try (SSHClient ssh = connect(target)) {
            ssh.newSCPFileTransfer().upload(new FileSystemFile(localFile.toFile()), remoteFile);
        }
        return remoteFile;
    }

    public static void prune(BackupTarget target, String projectPrefix, int keepCount) throws IOException {
        if (keepCount <= 0) {
            return;
        }
        requireSsh(target);
        String prefix = projectPrefix + "-";
        String remoteDir = target.sshRemotePath.trim();
        try (SSHClient ssh = connect(target);
             SFTPClient sftp = ssh.newSFTPClient()) {
            List<RemoteResourceInfo> files = new ArrayList<>();
            for (RemoteResourceInfo info : sftp.ls(remoteDir)) {
                if (!info.isRegularFile()) {
                    continue;
                }
                String name = info.getName();
                if (name.startsWith(prefix)
                        && (name.endsWith(".zip") || name.toLowerCase(Locale.ROOT).endsWith(".zip.enc"))) {
                    files.add(info);
                }
            }
            files.sort(Comparator.comparingLong((RemoteResourceInfo info) -> info.getAttributes().getMtime()).reversed());
            for (int i = keepCount; i < files.size(); i++) {
                sftp.rm(files.get(i).getPath());
            }
        }
    }

    static SSHClient connect(BackupTarget target) throws IOException {
        SSHClient ssh = new SSHClient();
        if (target.sshAcceptUnknownHost) {
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
        } else {
            ssh.loadKnownHosts();
        }
        int port = target.sshPort > 0 ? target.sshPort : 22;
        ssh.connect(target.sshHost.trim(), port);
        authenticate(ssh, target);
        return ssh;
    }

    private static void authenticate(SSHClient ssh, BackupTarget target) throws IOException {
        String user = target.sshUser == null ? "" : target.sshUser.trim();
        if (user.isEmpty()) {
            throw new IllegalArgumentException("SSH-Benutzer fehlt");
        }
        String keyPath = target.sshKeyPath == null ? "" : target.sshKeyPath.trim();
        char[] password = target.sshPassword == null ? new char[0] : target.sshPassword.toCharArray();
        try {
            if (!keyPath.isEmpty()) {
                Path key = Path.of(keyPath);
                if (!Files.isRegularFile(key)) {
                    throw new IllegalArgumentException("SSH-Schlüssel nicht gefunden: " + keyPath);
                }
                KeyProvider keys = password.length == 0
                        ? ssh.loadKeys(key.toString())
                        : ssh.loadKeys(key.toString(), target.sshPassword);
                ssh.authPublickey(user, keys);
            } else if (password.length > 0) {
                ssh.authPassword(user, target.sshPassword);
            } else {
                ssh.authPublickey(user);
            }
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private static void requireSsh(BackupTarget target) {
        if (target == null || target.kind() != BackupKind.SSH) {
            throw new IllegalArgumentException("Kein SSH-Ziel");
        }
        if (target.sshHost == null || target.sshHost.isBlank()) {
            throw new IllegalArgumentException("SSH-Host fehlt");
        }
        if (target.sshRemotePath == null || target.sshRemotePath.isBlank()) {
            throw new IllegalArgumentException("Remote-Pfad fehlt");
        }
    }
}
