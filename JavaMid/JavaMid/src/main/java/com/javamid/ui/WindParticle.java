package com.javamid.ui;

import com.javamid.flyweight.ParticleStyle;

import java.awt.geom.Point2D;

/**
 * Represents a single particle moving on screen (positions in pixel space relative to component).
 */
public class WindParticle {
    private ParticleStyle style;
    private Point2D.Double pos;
    private double vx;
    private double vy;
    private String stationId; // ID de la estación que generó esta partícula

    public WindParticle(Point2D.Double start, double vx, double vy, ParticleStyle style, String stationId) {
        this.pos = start;
        this.vx = vx;
        this.vy = vy;
        this.style = style;
        this.stationId = stationId;
    }

    public void update(double deltaSeconds) {
        pos.x += vx * deltaSeconds;
        pos.y += vy * deltaSeconds;
    }

    /**
     * Reinicia la partícula con nuevos valores (para reutilización en Object Pool).
     */
    public void reset(Point2D.Double start, double vx, double vy, ParticleStyle style, String stationId) {
        this.pos = start;
        this.vx = vx;
        this.vy = vy;
        this.style = style;
        this.stationId = stationId;
    }
    
    /**
     * Reinicia la partícula a valores por defecto (para devolución al pool).
     */
    public void resetToDefaults() {
        this.pos = new Point2D.Double(0, 0);
        this.vx = 0;
        this.vy = 0;
        this.style = null;
        this.stationId = null;
    }

    public Point2D.Double getPos() { return pos; }
    public ParticleStyle getStyle() { return style; }
    public double getVx() { return vx; }
    public double getVy() { return vy; }
    public String getStationId() { return stationId; }

    public void setPos(Point2D.Double p) { this.pos = p; }
    public void setVelocity(double vx, double vy) { this.vx = vx; this.vy = vy; }
    public void setStationId(String id) { this.stationId = id; }
}
