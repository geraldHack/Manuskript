package com.manuskript;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotdServiceTest {

    @AfterEach
    void restore() {
        ResourceManager.saveParameter(MotdService.PARAM_ENABLED, "true");
        ResourceManager.saveParameter(MotdService.PARAM_LAST_SEEN_ID, "");
    }

    @Test
    void parse_readsIdTitleAndBody() {
        Optional<MotdService.Message> msg = MotdService.parse("""
                id: 2026-09-21-1
                title: Hallo
                ---
                Erste Zeile.
                Zweite Zeile.
                """);
        assertTrue(msg.isPresent());
        assertEquals("2026-09-21-1", msg.get().id());
        assertEquals("Hallo", msg.get().title());
        assertTrue(msg.get().body().contains("Erste Zeile"));
        assertTrue(msg.get().body().contains("Zweite Zeile"));
    }

    @Test
    void parse_rejectsMissingId() {
        assertTrue(MotdService.parse("""
                title: Ohne Id
                ---
                Text
                """).isEmpty());
    }

    @Test
    void parse_rejectsEmptyBody() {
        assertTrue(MotdService.parse("""
                id: x
                ---
                """).isEmpty());
    }

    @Test
    void shouldShow_respectsEnabledAndLastSeen() {
        MotdService.Message msg = new MotdService.Message("abc", "T", "Body");
        ResourceManager.saveParameter(MotdService.PARAM_ENABLED, "true");
        ResourceManager.saveParameter(MotdService.PARAM_LAST_SEEN_ID, "");
        assertTrue(MotdService.shouldShow(msg));

        MotdService.markSeen("abc");
        assertFalse(MotdService.shouldShow(msg));

        MotdService.clearLastSeenId();
        assertTrue(MotdService.shouldShow(msg));

        MotdService.setEnabled(false);
        assertFalse(MotdService.shouldShow(msg));
    }

    @Test
    void motdUrl_isAllowed() {
        assertTrue(MotdUrls.isAllowed(MotdUrls.motdUri()));
        assertTrue(MotdUrls.isAllowed(URI.create("https://www.spoteroxe.de/downloads/manuskript-motd.txt")));
        assertFalse(MotdUrls.isAllowed(URI.create("http://spoteroxe.de/downloads/manuskript-motd.txt")));
        assertFalse(MotdUrls.isAllowed(URI.create("https://spoteroxe.de/downloads/other.txt")));
        assertFalse(MotdUrls.isAllowed(URI.create("https://evil.example/downloads/manuskript-motd.txt")));
    }
}
