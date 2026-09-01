package com.manuskript.agent;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRouterModelTagsTest {

    @Test
    void derivesCompactTagsFromOpenRouterModel() {
        JsonObject model = new JsonObject();
        model.addProperty("id", "meta-llama/llama-4-maverick:free");
        model.addProperty("context_length", 256_000);
        JsonObject pricing = new JsonObject();
        pricing.addProperty("prompt", "0");
        pricing.addProperty("completion", "0");
        model.add("pricing", pricing);
        JsonObject arch = new JsonObject();
        arch.addProperty("modality", "text+image");
        model.add("architecture", arch);
        model.add("reasoning", new JsonObject());

        List<String> tags = OpenRouterModelTags.deriveTags(model);
        assertTrue(tags.contains(OpenRouterModelTags.TAG_FREE));
        assertTrue(tags.contains(OpenRouterModelTags.TAG_VISION));
        assertTrue(tags.contains(OpenRouterModelTags.TAG_REASONING));
        assertTrue(tags.contains(OpenRouterModelTags.TAG_LONG_CONTEXT));
        assertFalse(tags.contains("temperature"));
    }

    @Test
    void pricingTextExtractsCostSuffix() {
        ModelOption option = new ModelOption(
                "openai/gpt-4o-mini",
                "openai/gpt-4o-mini (Input: 0.15 $/1M · Output: 0.60 $/1M)");
        assertEquals("Input: 0.15 $/1M · Output: 0.60 $/1M", option.pricingText());
    }

    @Test
    void pickUiTagsIsLimited() {
        List<ModelOption> options = List.of(
                new ModelOption("a", "a", List.of(OpenRouterModelTags.TAG_FREE)),
                new ModelOption("b", "b", List.of(OpenRouterModelTags.TAG_TOOLS)),
                new ModelOption("c", "c", List.of(OpenRouterModelTags.TAG_VISION)));
        List<String> ui = OpenRouterModelTags.pickUiTags(options);
        assertEquals(3, ui.size());
        assertTrue(ui.size() <= OpenRouterModelTags.MAX_UI_TAGS);
    }

    @Test
    void tooltipDescribesKnownTags() {
        assertTrue(OpenRouterModelTags.tooltip(OpenRouterModelTags.TAG_FREE).contains("Kosten"));
        assertTrue(OpenRouterModelTags.tooltip(OpenRouterModelTags.TAG_VISION).contains("Vision"));
        assertTrue(OpenRouterModelTags.tooltip(OpenRouterModelTags.TAG_TOOLS).contains("Tools"));
    }
}
