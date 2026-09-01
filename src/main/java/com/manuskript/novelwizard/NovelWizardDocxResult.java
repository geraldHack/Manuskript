package com.manuskript.novelwizard;

import java.nio.file.Path;
import java.util.List;

public record NovelWizardDocxResult(
        List<String> titles,
        List<Path> paths,
        int created,
        int updatedExisting,
        int preservedExisting) {

    public NovelWizardDocxResult(List<String> titles, List<Path> paths, int created, int updatedExisting) {
        this(titles, paths, created, updatedExisting, 0);
    }

    public int total() {
        return paths == null ? 0 : paths.size();
    }
}
