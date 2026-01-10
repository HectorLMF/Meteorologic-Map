package com.javamid.flyweight;

import java.awt.*;

public final class ParticleStyle {
    private final Color fillColor;
    private final Color strokeColor;
    private final float strokeWidth;

    public ParticleStyle(Color fillColor, Color strokeColor, float strokeWidth) {
        this.fillColor = fillColor;
        this.strokeColor = strokeColor;
        this.strokeWidth = strokeWidth;
    }

    public Color getFillColor() {
        return fillColor;
    }
    
    public Color getStrokeColor() {
        return strokeColor;
    }

    public float getStrokeWidth() {
        return strokeWidth;
    }
}
