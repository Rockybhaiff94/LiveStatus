package com.example.livestatus;

import com.example.livestatus.nametag.StaticLiveIndicatorRenderer;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StaticLiveIndicatorRendererTest {

    @Test
    @DisplayName("18. StaticLiveIndicatorRenderer parses MiniMessage correctly")
    void testMiniMessageParsing() {
        StaticLiveIndicatorRenderer renderer = new StaticLiveIndicatorRenderer("<red><bold>LIVE</bold></red>", "LIVE");
        Component comp = renderer.render();
        assertNotNull(comp);
        assertEquals("LIVE", renderer.renderPlainText());
    }

    @Test
    @DisplayName("19. StaticLiveIndicatorRenderer handles fallback gracefully")
    void testFallback() {
        StaticLiveIndicatorRenderer renderer = new StaticLiveIndicatorRenderer("", "STREAMING");
        assertEquals("STREAMING", renderer.renderPlainText());
    }
}

