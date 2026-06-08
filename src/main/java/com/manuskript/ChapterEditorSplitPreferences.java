package com.manuskript;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.SplitPane;

import java.util.prefs.Preferences;

/**
 * Persistiert Divider-Positionen des Canvas-Kapitel-Editors (Editor | Agenten | Lektorat).
 */
public final class ChapterEditorSplitPreferences {

    private static final String PREF_KEY_PREFIX = "prototype_editor_main_split_";
    private static final double MIN_DIVIDER = 0.12;
    private static final double MAX_DIVIDER = 0.88;

    private ChapterEditorSplitPreferences() {
    }

    public static Preferences preferencesNode() {
        return Preferences.userNodeForPackage(ManuskriptEditorTestWindow.class);
    }

    public static void apply(SplitPane splitPane) {
        apply(splitPane, preferencesNode());
    }

    public static void apply(SplitPane splitPane, Preferences prefs) {
        if (splitPane == null || prefs == null) {
            return;
        }
        double[] positions = load(splitPane.getItems().size(), prefs);
        if (positions.length == 0) {
            return;
        }
        splitPane.setDividerPositions(positions);
        Platform.runLater(() -> {
            if (splitPane.getItems().size() == positions.length + 1) {
                splitPane.setDividerPositions(load(splitPane.getItems().size(), prefs));
            }
        });
    }

    public static void bindPersistence(SplitPane splitPane) {
        bindPersistence(splitPane, preferencesNode());
    }

    public static void bindPersistence(SplitPane splitPane, Preferences prefs) {
        if (splitPane == null || prefs == null) {
            return;
        }
        ListChangeListener<SplitPane.Divider> dividerListener = change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (SplitPane.Divider divider : change.getAddedSubList()) {
                        attachSaveListener(divider, splitPane, prefs);
                    }
                }
            }
        };
        splitPane.getDividers().addListener(dividerListener);
        for (SplitPane.Divider divider : splitPane.getDividers()) {
            attachSaveListener(divider, splitPane, prefs);
        }
    }

    private static void attachSaveListener(SplitPane.Divider divider, SplitPane splitPane, Preferences prefs) {
        divider.positionProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                save(splitPane, prefs);
            }
        });
    }

    public static void save(SplitPane splitPane, Preferences prefs) {
        if (splitPane == null || prefs == null || splitPane.getDividers().isEmpty()) {
            return;
        }
        int itemCount = splitPane.getItems().size();
        for (int i = 0; i < splitPane.getDividers().size(); i++) {
            prefs.putDouble(prefKey(itemCount, i), clamp(splitPane.getDividers().get(i).getPosition()));
        }
    }

    private static double[] load(int itemCount, Preferences prefs) {
        int dividerCount = Math.max(0, itemCount - 1);
        if (dividerCount == 0) {
            return new double[0];
        }
        double[] defaults = defaultPositions(dividerCount);
        double[] positions = new double[dividerCount];
        for (int i = 0; i < dividerCount; i++) {
            positions[i] = clamp(prefs.getDouble(prefKey(itemCount, i), defaults[i]));
        }
        return positions;
    }

    private static String prefKey(int itemCount, int dividerIndex) {
        return PREF_KEY_PREFIX + itemCount + "_div_" + dividerIndex;
    }

    private static double[] defaultPositions(int dividerCount) {
        if (dividerCount == 1) {
            return new double[]{0.78};
        }
        if (dividerCount >= 2) {
            return new double[]{0.55, 0.78};
        }
        return new double[0];
    }

    private static double clamp(double position) {
        return Math.max(MIN_DIVIDER, Math.min(MAX_DIVIDER, position));
    }
}
