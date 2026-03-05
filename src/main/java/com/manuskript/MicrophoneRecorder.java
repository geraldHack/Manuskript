package com.manuskript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mikrofon-Aufnahme über Java Sound API.
 * Zeichnet PCM-Audio auf und schreibt es als WAV-Datei (16-bit, Mono, 22050 Hz).
 * Aufnahme läuft in einem Hintergrund-Thread.
 */
public class MicrophoneRecorder {

    private static final Logger logger = LoggerFactory.getLogger(MicrophoneRecorder.class);

    /** Aufnahmeformat: 16-bit, 22050 Hz, Mono (gut für Sprache, kleinere Dateien). */
    public static final float SAMPLE_RATE = 22050f;
    public static final int SAMPLE_SIZE_BITS = 16;
    public static final int CHANNELS = 1;

    private final AudioFormat format;
    private TargetDataLine line;
    private Thread recordThread;
    private final AtomicBoolean recording = new AtomicBoolean(false);
    /** true = Aufnahme pausiert (Line geschlossen), kann mit resumeRecording() fortgesetzt werden. */
    private final AtomicBoolean paused = new AtomicBoolean(false);
    /** true = Record-Thread soll nach Beendigung (inkl. Drain) aktuelle Puffergröße zu segmentEndSizes hinzufügen (nur bei Pause). */
    private final AtomicBoolean addSegmentEndOnExit = new AtomicBoolean(false);
    private ByteArrayOutputStream recordedBytes;
    /** Nach jeder Pause: End-Offset des zuletzt aufgenommenen Segments (für Zurücknehmen). */
    private final List<Integer> segmentEndSizes = new ArrayList<>();
    private final Object bufferLock = new Object();
    private volatile Exception lastError;

    public MicrophoneRecorder() {
        this.format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS,
                (SAMPLE_SIZE_BITS / 8) * CHANNELS, SAMPLE_RATE, false);
    }

    /**
     * Prüft, ob ein Mikrofon-Eingang verfügbar ist.
     */
    public static boolean isMicrophoneAvailable() {
        try {
            AudioFormat fmt = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS,
                    (SAMPLE_SIZE_BITS / 8) * CHANNELS, SAMPLE_RATE, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
            return AudioSystem.isLineSupported(info);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Startet die Aufnahme. Blockiert nicht; Aufnahme läuft im Hintergrund.
     * Bei bereits pausierter Aufnahme muss {@link #resumeRecording()} verwendet werden.
     * @return true wenn Aufnahme gestartet wurde, false bei Fehler (z. B. kein Mikrofon)
     */
    public boolean startRecording() {
        if (recording.get()) {
            logger.warn("Aufnahme läuft bereits.");
            return true;
        }
        if (isPaused()) {
            return resumeRecording();
        }
        lastError = null;
        paused.set(false);
        addSegmentEndOnExit.set(false);
        synchronized (segmentEndSizes) {
            segmentEndSizes.clear();
        }
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                lastError = new IOException("Kein Mikrofon für dieses Format unterstützt (16-bit, 22050 Hz, Mono).");
                return false;
            }
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            recordedBytes = new ByteArrayOutputStream();
            recording.set(true);
            recordThread = new Thread(this::runRecord, "MicrophoneRecorder");
            recordThread.setDaemon(true);
            recordThread.start();
            return true;
        } catch (LineUnavailableException e) {
            lastError = e;
            logger.warn("Mikrofon nicht verfügbar: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Pausiert die Aufnahme (z. B. Leertaste losgelassen). Line wird geschlossen, Daten bleiben in recordedBytes.
     * Fortsetzung mit {@link #resumeRecording()} öffnet eine neue Line und hängt weiter an.
     */
    public void pauseRecording() {
        if (!recording.get()) return;
        addSegmentEndOnExit.set(true);
        recording.set(false);
        if (recordThread != null) {
            try {
                recordThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            recordThread = null;
        }
        addSegmentEndOnExit.set(false);
        line = null;
        paused.set(recordedBytes != null);
    }

    /**
     * Setzt eine pausierte Aufnahme fort: öffnet eine neue Line und hängt weitere Daten an recordedBytes an.
     * @return true wenn fortgesetzt wurde, false wenn nicht pausiert
     */
    public boolean resumeRecording() {
        if (!paused.get() || recordedBytes == null) return false;
        if (recording.get()) return true;
        lastError = null;
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                lastError = new IOException("Kein Mikrofon für dieses Format unterstützt.");
                return false;
            }
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            paused.set(false);
            recording.set(true);
            recordThread = new Thread(this::runRecord, "MicrophoneRecorder");
            recordThread.setDaemon(true);
            recordThread.start();
            return true;
        } catch (LineUnavailableException e) {
            lastError = e;
            logger.warn("Mikrofon bei Fortsetzung nicht verfügbar: {}", e.getMessage());
            return false;
        }
    }

    /** true wenn Aufnahme pausiert ist (Daten vorhanden, kann fortgesetzt werden). */
    public boolean isPaused() {
        return paused.get() && recordedBytes != null;
    }

    /**
     * Nimmt die letzte Space-Aufnahme (letztes Segment) zurück. Nur wenn pausiert und mindestens ein Segment vorhanden.
     * @return true wenn ein Segment zurückgenommen wurde
     */
    public boolean undoLastSegment() {
        if (!paused.get() || recordedBytes == null) return false;
        int bytesToKeep;
        synchronized (segmentEndSizes) {
            if (segmentEndSizes.isEmpty()) return false;
            int lastIdx = segmentEndSizes.size() - 1;
            bytesToKeep = (lastIdx > 0) ? segmentEndSizes.get(lastIdx - 1) : 0;
            segmentEndSizes.remove(lastIdx);
        }
        synchronized (bufferLock) {
            byte[] all = recordedBytes.toByteArray();
            int keep = Math.min(bytesToKeep, all.length);
            recordedBytes = new ByteArrayOutputStream();
            if (keep > 0) {
                recordedBytes.write(all, 0, keep);
            }
        }
        return true;
    }

    /** true wenn in Pause mindestens ein Segment vorhanden ist (Zurücknehmen möglich). */
    public boolean canUndoLastSegment() {
        if (!paused.get()) return false;
        synchronized (segmentEndSizes) {
            return !segmentEndSizes.isEmpty();
        }
    }

    /**
     * Verwirft den Aufnahme-Puffer (und ggf. laufende Aufnahme) ohne eine Datei zu schreiben.
     * Nach dem Aufruf ist weder Aufnahme noch Pause aktiv; Abspielen/Übernehmen hat keine Daten.
     */
    public void discardBuffer() {
        addSegmentEndOnExit.set(false);
        recording.set(false);
        if (recordThread != null) {
            try {
                recordThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            recordThread = null;
        }
        line = null;
        synchronized (bufferLock) {
            recordedBytes = null;
        }
        synchronized (segmentEndSizes) {
            segmentEndSizes.clear();
        }
        paused.set(false);
    }

    /**
     * Schreibt den aktuellen Puffer in eine temporäre WAV-Datei, ohne die Aufnahme zu beenden.
     * Nur nutzbar wenn pausiert (oder mit Daten). Erlaubt z. B. Abspielen während der Pause.
     * @return Pfad zur Temp-WAV oder null wenn keine Daten
     */
    public Path writeCurrentBufferToTempFile() {
        if (recordedBytes == null) return null;
        byte[] pcm;
        synchronized (bufferLock) {
            pcm = recordedBytes.toByteArray();
        }
        if (pcm.length == 0) return null;
        try {
            Path wav = Files.createTempFile("manuskript-preview-", ".wav");
            writeWav(wav, pcm);
            return wav;
        } catch (IOException e) {
            lastError = e;
            return null;
        }
    }

    /** Keine Verzögerung – Aufnahme startet sofort nach dem dritten Piep (der ist das Startsignal). */
    private static final int START_SILENCE_MS = 0;

    private void runRecord() {
        try {
            Thread.sleep(START_SILENCE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] buffer = new byte[4096];
        while (recording.get() && line != null && line.isOpen()) {
            int n = line.read(buffer, 0, buffer.length);
            if (n > 0 && recordedBytes != null) {
                synchronized (bufferLock) {
                    recordedBytes.write(buffer, 0, n);
                }
            }
        }
        // Noch in der Line gepufferte Daten lesen (verhindert Abbruch vor Ende bei Wiedergabe)
        TargetDataLine l = line;
        if (l != null && l.isOpen() && recordedBytes != null) {
            try {
                l.stop();
                while (l.available() > 0) {
                    int n = l.read(buffer, 0, Math.min(buffer.length, l.available()));
                    if (n <= 0) break;
                    synchronized (bufferLock) {
                        recordedBytes.write(buffer, 0, n);
                    }
                }
                l.close();
            } catch (Exception e) {
                logger.trace("Drain nach Pause/Stop: {}", e.getMessage());
            }
        }
        // Kurze Stille ans Ende anhängen (klingt weniger abgehackt), dann Segmentende eintragen
        if (addSegmentEndOnExit.get() && recordedBytes != null) {
            int size;
            synchronized (bufferLock) {
                int silenceSamples = (int) Math.round(SAMPLE_RATE * 0.18);
                byte[] silence = new byte[silenceSamples * 2];
                recordedBytes.write(silence, 0, silence.length);
                size = recordedBytes.size();
            }
            if (size > 0) {
                synchronized (segmentEndSizes) {
                    segmentEndSizes.add(size);
                }
            }
        }
    }

    /**
     * Beendet die Aufnahme (oder pausierte Aufnahme) und schreibt die Daten in eine temporäre WAV-Datei.
     * @return Pfad zur erzeugten WAV-Datei, oder null wenn keine Daten oder Fehler
     */
    public Path stopRecording() {
        addSegmentEndOnExit.set(false);
        recording.set(false);
        paused.set(false);
        if (recordThread != null) {
            try {
                recordThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            recordThread = null;
        }
        line = null;
        byte[] pcm;
        synchronized (bufferLock) {
            pcm = recordedBytes != null ? recordedBytes.toByteArray() : null;
            recordedBytes = null;
        }
        synchronized (segmentEndSizes) {
            segmentEndSizes.clear();
        }
        if (pcm == null || pcm.length == 0) {
            return null;
        }
        try {
            Path wav = Files.createTempFile("manuskript-recording-", ".wav");
            writeWav(wav, pcm);
            return wav;
        } catch (IOException e) {
            lastError = e;
            logger.warn("WAV schreiben fehlgeschlagen: {}", e.getMessage());
            return null;
        }
    }

    /** Liefert den letzten Fehler (z. B. wenn startRecording fehlschlug). */
    public Exception getLastError() {
        return lastError;
    }

    public boolean isRecording() {
        return recording.get();
    }

    private void writeWav(Path path, byte[] pcmData) throws IOException {
        int dataLen = pcmData.length;
        int totalLen = 36 + dataLen;
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(Files.newOutputStream(path))) {
            out.writeBytes("RIFF");
            out.write(intToLittleEndian(totalLen), 0, 4);
            out.writeBytes("WAVE");
            out.writeBytes("fmt ");
            out.write(intToLittleEndian(16), 0, 4); // Subchunk1Size
            out.write(shortToLittleEndian((short) 1), 0, 2);  // AudioFormat = PCM
            out.write(shortToLittleEndian((short) CHANNELS), 0, 2);
            out.write(intToLittleEndian((int) SAMPLE_RATE), 0, 4);
            out.write(intToLittleEndian((int) (SAMPLE_RATE * (SAMPLE_SIZE_BITS / 8) * CHANNELS)), 0, 4); // ByteRate
            out.write(shortToLittleEndian((short) ((SAMPLE_SIZE_BITS / 8) * CHANNELS)), 0, 2); // BlockAlign
            out.write(shortToLittleEndian((short) SAMPLE_SIZE_BITS), 0, 2);
            out.writeBytes("data");
            out.write(intToLittleEndian(dataLen), 0, 4);
            out.write(pcmData);
        }
    }

    private static byte[] intToLittleEndian(int v) {
        return new byte[] { (byte) (v & 0xff), (byte) ((v >> 8) & 0xff), (byte) ((v >> 16) & 0xff), (byte) ((v >> 24) & 0xff) };
    }

    private static byte[] shortToLittleEndian(short v) {
        return new byte[] { (byte) (v & 0xff), (byte) ((v >> 8) & 0xff) };
    }
}
