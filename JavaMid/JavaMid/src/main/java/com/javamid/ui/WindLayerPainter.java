package com.javamid.ui;

import com.javamid.flyweight.ParticleStyle;
import com.javamid.flyweight.WeatherFlyweightFactory;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.painter.Painter;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Painter that renders wind particles on top of the map. Particles are lightweight and share styles
 * provided by the Flyweight factory.
 */
public class WindLayerPainter implements Painter<JXMapViewer> {

    private final List<WindParticle> particles = new ArrayList<>();
    private final Timer timer;
    private volatile double windSpeed = 0.0; // m/s
    private volatile double windDeg = 0.0; // degrees (meteorological)
    private final Random rnd = new Random();
    private final JXMapViewer viewer;
    private long lastUpdateTime = 0;

    public WindLayerPainter(JXMapViewer viewer) {
        this.viewer = viewer;
        // 30 FPS update - only repaint the viewer, not reload
        timer = new Timer(33, e -> {
            step(0.033);
            // Use a component-specific repaint to avoid triggering tile reloads
            SwingUtilities.invokeLater(() -> viewer.repaint());
        });
        timer.setCoalesce(true);
    }

    public void start() {
        populateInitialParticles();
        timer.start();
    }

    public void stop() {
        timer.stop();
        synchronized (particles) { particles.clear(); }
    }

    public void setWind(double speedMetersPerSecond, double deg) {
        this.windSpeed = speedMetersPerSecond;
        this.windDeg = deg;
        // update particles' velocity according to new wind
        double rad = Math.toRadians(270 - deg); // convert meteorological deg to screen vector (y-down)
        double vx = Math.cos(rad) * speedMetersPerSecond * 10; // scale for screen
        double vy = Math.sin(rad) * speedMetersPerSecond * 10;
        synchronized (particles) {
            for (WindParticle p : particles) {
                p.setVelocity(vx + (rnd.nextDouble()-0.5)*2.0, vy + (rnd.nextDouble()-0.5)*2.0);
            }
        }
    }

    private void populateInitialParticles() {
        synchronized (particles) {
            particles.clear();
            int count = 120; // number of particles
            Dimension size = viewer.getSize();
            if (size.width <= 0 || size.height <= 0) return;
            for (int i = 0; i < count; i++) {
                // spawn along edges randomly
                Point2D.Double start = randomEdgePoint(size.width, size.height);
                ParticleStyle style = WeatherFlyweightFactory.getStyleForWind(windSpeed);
                // velocity based on current wind
                double rad = Math.toRadians(270 - windDeg);
                double vx = Math.cos(rad) * windSpeed * 10 + (rnd.nextDouble()-0.5)*2.0;
                double vy = Math.sin(rad) * windSpeed * 10 + (rnd.nextDouble()-0.5)*2.0;
                particles.add(new WindParticle(start, vx, vy, style, null)); // null stationId para partículas globales
            }
        }
    }

    private Point2D.Double randomEdgePoint(int w, int h) {
        int edge = rnd.nextInt(4);
        switch (edge) {
            case 0: return new Point2D.Double(0, rnd.nextDouble()*h); // left
            case 1: return new Point2D.Double(w, rnd.nextDouble()*h); // right
            case 2: return new Point2D.Double(rnd.nextDouble()*w, 0); // top
            default: return new Point2D.Double(rnd.nextDouble()*w, h); // bottom
        }
    }

    private void step(double deltaSeconds) {
        Dimension size = viewer.getSize();
        synchronized (particles) {
            Iterator<WindParticle> it = particles.iterator();
            while (it.hasNext()) {
                WindParticle p = it.next();
                p.update(deltaSeconds);
                Point2D.Double pos = p.getPos();
                // if out of bounds by margin, respawn at edge
                if (pos.x < -20 || pos.x > size.width+20 || pos.y < -20 || pos.y > size.height+20) {
                    Point2D.Double start = randomEdgePoint(size.width, size.height);
                    p.setPos(start);
                    double rad = Math.toRadians(270 - windDeg);
                    double vx = Math.cos(rad) * windSpeed * 10 + (rnd.nextDouble()-0.5)*2.0;
                    double vy = Math.sin(rad) * windSpeed * 10 + (rnd.nextDouble()-0.5)*2.0;
                    p.setVelocity(vx, vy);
                }
            }
        }
    }

    @Override
    public void paint(Graphics2D g, JXMapViewer map, int w, int h) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // draw particles in component pixel space
        synchronized (particles) {
            for (WindParticle p : particles) {
                Point2D.Double pos = p.getPos();
                ParticleStyle s = p.getStyle();
                
                // Draw short line indicating wind direction based on velocity
                double vx = p.getVx();
                double vy = p.getVy();
                double speed = Math.sqrt(vx * vx + vy * vy);
                
                if (speed > 0.1) {
                    // Normalize and scale for visual length
                    double len = 6 + s.getStrokeWidth() * 2;
                    double dx = (vx / speed) * len;
                    double dy = (vy / speed) * len;
                    
                    Line2D line = new Line2D.Double(pos.x, pos.y, pos.x + dx, pos.y + dy);
                    
                    // Draw white fill (thicker)
                    g2.setColor(s.getFillColor());
                    g2.setStroke(new BasicStroke(s.getStrokeWidth() + 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(line);
                    
                    // Draw black border (thinner)
                    g2.setColor(s.getStrokeColor());
                    g2.setStroke(new BasicStroke(s.getStrokeWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(line);
                } else {
                    // Draw a small dot if speed is very low - white fill with black border
                    int dotSize = 3;
                    int x = (int)(pos.x - dotSize/2);
                    int y = (int)(pos.y - dotSize/2);
                    
                    // Draw white fill
                    g2.setColor(s.getFillColor());
                    g2.fillOval(x, y, dotSize, dotSize);
                    
                    // Draw black border
                    g2.setColor(s.getStrokeColor());
                    g2.drawOval(x, y, dotSize, dotSize);
                }
            }
        }
        g2.dispose();
    }
}
