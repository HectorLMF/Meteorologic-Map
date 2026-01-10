package com.javamid.ui;

import com.javamid.config.MapConfig;
import com.javamid.model.WeatherStation;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel transparente que renderiza overlay de temperatura.
 * Muestra círculos con escala cromática según temperatura en °C.
 */
public class TemperatureOverlayPanel extends JPanel {

    private final JXMapViewer mapViewer;
    private List<WeatherStation> stations = new ArrayList<>();
    private final Map<String, Double> stationTemperature = new HashMap<>();
    private double influenceRadiusKm = MapConfig.STATION_GENERATION_RADIUS_KM;
    private boolean active = false;
    private StationMarkerPanel stationMarkerPanel;

    public TemperatureOverlayPanel(JXMapViewer mapViewer) {
        this.mapViewer = mapViewer;
        setOpaque(false);
        setBackground(new Color(0, 0, 0, 0));
    }

    public void setStations(List<WeatherStation> stations) {
        this.stations = stations != null ? new ArrayList<>(stations) : new ArrayList<>();
        repaint();
    }

    public void setStationTemperature(String stationId, double temperatureC) {
        stationTemperature.put(stationId, temperatureC);
        if (active) {
            repaint();
        }
    }

    public boolean hasTemperatureData(String stationId) {
        return stationTemperature.containsKey(stationId);
    }

    public void setInfluenceRadiusKm(double radiusKm) {
        this.influenceRadiusKm = radiusKm;
        if (active) {
            repaint();
        }
    }

    public void setActive(boolean active) {
        this.active = active;
        setVisible(active);
        if (active) {
            repaint();
        }
    }

    public boolean isActive() { return active; }

    public void setStationMarkerPanel(StationMarkerPanel panel) {
        this.stationMarkerPanel = panel;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!active || stations.isEmpty() || mapViewer == null) {
            return;
        }

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        try {
            List<WeatherStation> drawStations = stations;
            if (stationMarkerPanel != null) {
                List<WeatherStation> filtered = stationMarkerPanel.getVisibleUnclusteredStations();
                if (filtered != null && !filtered.isEmpty()) {
                    drawStations = filtered;
                }
            }

            for (WeatherStation station : drawStations) {
                Double temp = stationTemperature.get(station.getId());
                if (temp == null) continue;

                GeoPosition geoPos = new GeoPosition(station.getLatitude(), station.getLongitude());
                Point2D screenPos = mapViewer.getTileFactory().geoToPixel(geoPos, mapViewer.getZoom());
                Rectangle viewportBounds = mapViewer.getViewportBounds();
                int screenX = (int) (screenPos.getX() - viewportBounds.x);
                int screenY = (int) (screenPos.getY() - viewportBounds.y);

                int radiusPixels = kmToPixels(influenceRadiusKm, station.getLatitude());
                int diameter = radiusPixels * 2;

                // Color por temperatura (gradiente frío→caliente)
                Color base = temperatureToColor(temp);
                int alpha = 60; // más transparencia
                Color centerColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
                Color midColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.max(8, alpha / 2));
                Color edgeColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), 0);

                float[] dist = {0.0f, 0.7f, 1.0f};
                java.awt.RadialGradientPaint gradient = new java.awt.RadialGradientPaint(
                        new Point2D.Float(screenX, screenY), radiusPixels,
                        dist, new Color[]{centerColor, midColor, edgeColor}
                );
                g2d.setPaint(gradient);
                g2d.fillOval(screenX - radiusPixels, screenY - radiusPixels, diameter, diameter);

                g2d.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.min(150, alpha * 2)));
                g2d.setStroke(new BasicStroke(1.5f));
                g2d.drawOval(screenX - radiusPixels, screenY - radiusPixels, diameter, diameter);
            }
        } finally {
            g2d.dispose();
        }
    }

    private int kmToPixels(double km, double latitude) {
        if (mapViewer == null) return 0;
        try {
            double deltaLat = km / 111.0;
            GeoPosition p1 = new GeoPosition(latitude, 0.0);
            GeoPosition p2 = new GeoPosition(latitude + deltaLat, 0.0);
            Point2D w1 = mapViewer.getTileFactory().geoToPixel(p1, mapViewer.getZoom());
            Point2D w2 = mapViewer.getTileFactory().geoToPixel(p2, mapViewer.getZoom());
            return (int) Math.abs(w2.getY() - w1.getY());
        } catch (Exception ex) {
            int zoom = mapViewer.getZoom();
            double metersPerPixel = (40075000.0 * Math.cos(Math.toRadians(latitude))) / (256.0 * Math.pow(2, zoom));
            return (int) ((km * 1000.0) / metersPerPixel);
        }
    }

    public void onMapChanged() { if (active) repaint(); }

    public void clear() { stationTemperature.clear(); repaint(); }

    /**
     * Mapea temperatura en °C a color: azul (-10) → verde (15) → amarillo (25) → rojo (35).
     */
    private Color temperatureToColor(double c) {
        double t = Math.max(-10, Math.min(35, c));
        if (t <= 0) {
            // -10 to 0: blue to cyan
            double k = (t + 10) / 10.0;
            return new Color((int)(0 + 55*k), (int)(80 + 175*k), 255);
        } else if (t <= 15) {
            // 0 to 15: cyan to green
            double k = t / 15.0;
            return new Color((int)(55 - 55*k), (int)(255 - 55*k), (int)(255 - 255*k));
        } else if (t <= 25) {
            // 15 to 25: green to yellow
            double k = (t - 15) / 10.0;
            return new Color((int)(0 + 255*k), 200, 0);
        } else {
            // 25 to 35: yellow to red
            double k = (t - 25) / 10.0;
            return new Color(255, (int)(200 - 150*k), 0);
        }
    }
}
