package com.javamid.flyweight;

import java.awt.*;

/**
 * Patron Flyweight para estilos de marcadores de estaciones.
 * Comparte colores y tamaños entre multiples marcadores.
 */
public final class MarkerStyle {
    private final Color outerColor;
    private final Color innerColor;
    private final Color markerColor;
    private final Color centerColor;
    private final int outerRadius;
    private final int innerRadius;
    private final int markerRadius;
    private final int centerRadius;
    private final float strokeWidth;

    public MarkerStyle(Color outerColor, Color innerColor, Color markerColor, Color centerColor,
                      int outerRadius, int innerRadius, int markerRadius, int centerRadius, float strokeWidth) {
        this.outerColor = outerColor;
        this.innerColor = innerColor;
        this.markerColor = markerColor;
        this.centerColor = centerColor;
        this.outerRadius = outerRadius;
        this.innerRadius = innerRadius;
        this.markerRadius = markerRadius;
        this.centerRadius = centerRadius;
        this.strokeWidth = strokeWidth;
    }

    public Color getOuterColor() {
        return outerColor;
    }

    public Color getInnerColor() {
        return innerColor;
    }

    public Color getMarkerColor() {
        return markerColor;
    }

    public Color getCenterColor() {
        return centerColor;
    }

    public int getOuterRadius() {
        return outerRadius;
    }

    public int getInnerRadius() {
        return innerRadius;
    }

    public int getMarkerRadius() {
        return markerRadius;
    }

    public int getCenterRadius() {
        return centerRadius;
    }

    public float getStrokeWidth() {
        return strokeWidth;
    }
}
