package com.example.livestatus.nametag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Static implementation of LiveIndicatorRenderer.
 * Renders a configurable static indicator (e.g. "<red><bold>LIVE</bold></red>").
 */
public class StaticLiveIndicatorRenderer implements LiveIndicatorRenderer {

    private final Component component;
    private final String plainText;

    public StaticLiveIndicatorRenderer(String format, String fallbackText) {
        String defaultText = fallbackText != null && !fallbackText.isBlank() ? fallbackText : "LIVE";
        Component parsed;
        if (format != null && !format.isBlank()) {
            try {
                parsed = MiniMessage.miniMessage().deserialize(format);
            } catch (Exception e) {
                parsed = Component.text(defaultText);
            }
        } else {
            parsed = Component.text(defaultText);
        }
        this.component = parsed;
        this.plainText = PlainTextComponentSerializer.plainText().serialize(this.component);
    }

    @Override
    public Component render() {
        return component;
    }

    @Override
    public String renderPlainText() {
        return plainText;
    }
}

