package com.manuskript.buchpreview;

import java.nio.file.Files;
import java.nio.file.Path;

final class CoverDataUri {

    private CoverDataUri() {
    }

    static String fromFile(Path cover) {
        if (cover == null || !Files.isRegularFile(cover)) {
            return "";
        }
        return PreviewImage.dataUri(cover);
    }
}
