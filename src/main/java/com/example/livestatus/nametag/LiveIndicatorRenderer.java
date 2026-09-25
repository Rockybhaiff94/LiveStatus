package com.example.livestatus.nametag;

import net.kyori.adventure.text.Component;

/**
 * Abstraction for rendering the LIVE visual indicator.
 * Allows switching between static text and future animated renderers without altering core logic.
 */
public interface LiveIndicatorRenderer {

    /**
     * Renders the Adventure Component representation of the indicator.
     */
    Component render();

    /**
     * Returns the plain-text fallback of the indicator.
     */
    String renderPlainText();
}

