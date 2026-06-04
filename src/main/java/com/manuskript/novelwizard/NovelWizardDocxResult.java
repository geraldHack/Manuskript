package com.manuskript.novelwizard;

import java.nio.file.Path;
import java.util.List;

public record NovelWizardDocxResult(
        List<String> titles,
        List<Path> paths,
        int created,
        int updatedExisting) {

    public int total() {
        return paths == null ? 0 : paths.size();
    }
}
