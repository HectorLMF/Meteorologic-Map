package com.javamid.flyweight;

import java.awt.*;

public final class ParticleStyle {
    private final Color color;
    private final float strokeWidth;

    public ParticleStyle(Color color, float strokeWidth) {
        this.color = color;
        this.strokeWidth = strokeWidth;
    }

    public Color getColor() {
        return color;
    }

    public float getStrokeWidth() {
        return strokeWidth;
    }
}
