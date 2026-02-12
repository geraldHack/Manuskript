FFmpeg für Manuskript (Gesamt-Audiodatei mit Pausen)

Manuskript nutzt FFmpeg, um beim Erstellen der „Gesamt-Audiodatei“ 
3,5 Sekunden Stille zwischen den Segmenten einzufügen.

So richten Sie das gebündelte FFmpeg ein:

1. FFmpeg für Windows herunterladen (z. B. von https://www.gyan.dev/ffmpeg/builds/
   oder https://github.com/BtbN/FFmpeg-Builds/releases)
   – „ffmpeg-release-essentials.zip“ reicht.

2. ZIP entpacken. Sie erhalten einen Ordner mit z. B. bin\ffmpeg.exe.

3. Einen der folgenden Wege wählen:

   Variante A: ffmpeg.exe direkt hier ablegen
   – Kopieren Sie ffmpeg.exe in DIESEN Ordner (ffmpeg\).
   – Ergebnis: ffmpeg\ffmpeg.exe

   Variante B: kompletten Ordner übernehmen
   – Kopieren Sie den gesamten entpackten Ordner-Inhalt (inkl. bin\) hierher.
   – Ergebnis: ffmpeg\bin\ffmpeg.exe

4. Wenn in diesem Verzeichnis kein FFmpeg gefunden wird, verwendet
   Manuskript „ffmpeg“ aus dem System-PATH (falls installiert).

Analog zu pandoc\ – das Programmverzeichnis ist dasjenige, aus dem
Sie Manuskript starten (Projektroot bzw. das Verzeichnis mit der JAR).
