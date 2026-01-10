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
 * Overlay de precipitación: círculos con cromática 0→20 mm/h (azul→morado).
 */
public class PrecipitationOverlayPanel extends JPanel {

    private final JXMapViewer mapViewer;
    private List<WeatherStation> stations = new ArrayList<>();
    private final Map<String, Double> stationPrecip = new HashMap<>();
    private double influenceRadiusKm = MapConfig.STATION_GENERATION_RADIUS_KM;
    private boolean active = false;
    private StationMarkerPanel stationMarkerPanel;

    public PrecipitationOverlayPanel(JXMapViewer mapViewer) {
        this.mapViewer = mapViewer;
        setOpaque(false);
        setBackground(new Color(0, 0, 0, 0));
    }

    public void setStations(List<WeatherStation> stations) {
        this.stations = stations != null ? new ArrayList<>(stations) : new ArrayList<>();
        repaint();
    }

    public void setStationPrecipitation(String stationId, double mmPerHour) {
        stationPrecip.put(stationId, mmPerHour);
        if (active) repaint();
    }

    public boolean hasPrecipitationData(String stationId) { return stationPrecip.containsKey(stationId); }

    public void setInfluenceRadiusKm(double radiusKm) {
        this.influenceRadiusKm = radiusKm;
        if (active) repaint();
    }

    public void setActive(boolean active) {
        this.active = active;
        setVisible(active);
        if (active) repaint();
    }

    public boolean isActive() { return active; }

    public void setStationMarkerPanel(StationMarkerPanel panel) {
        this.stationMarkerPanel = panel;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!active || stations.isEmpty() || mapViewer == null) return;

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
                Double mm = stationPrecip.get(station.getId());
                if (mm == null) continue;

                GeoPosition geoPos = new GeoPosition(station.getLatitude(), station.getLongitude());
                Point2D screenPos = mapViewer.getTileFactory().geoToPixel(geoPos, mapViewer.getZoom());
                Rectangle viewportBounds = mapViewer.getViewportBounds();
                int screenX = (int) (screenPos.getX() - viewportBounds.x);
                int screenY = (int) (screenPos.getY() - viewportBounds.y);

                int radiusPixels = kmToPixels(influenceRadiusKm, station.getLatitude());
                int diameter = radiusPixels * 2;

                Color base = precipitationToColor(mm);
                int alpha = (int) (Math.max(0, Math.min(20, mm)) * 3.5); // más transparencia
                alpha = Math.max(20, Math.min(70, alpha));
                Color centerColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
                Color midColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha / 2);
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

    public void clear() { stationPrecip.clear(); repaint(); }

    /**
     * Mapea precipitación (mm/h) a color: 0→azul claro, 10→azul, 20→morado.
     */
    private Color precipitationToColor(double mm) {
        double p = Math.max(0, Math.min(20, mm));
        if (p <= 5) {
            double k = p / 5.0;
            return new Color((int)(180 - 50*k), (int)(220 - 30*k), 255);
        } else if (p <= 10) {
            double k = (p - 5) / 5.0;
            return new Color((int)(130 - 20*k), (int)(190 - 50*k), (int)(255 - 30*k));
        } else if (p <= 15) {
            double k = (p - 10) / 5.0;
            return new Color((int)(110 - 20*k), (int)(140 - 40*k), (int)(225 - 25*k));
        } else {
            double k = (p - 15) / 5.0;
            return new Color((int)(90 - 20*k), (int)(100 - 20*k), (int)(200 - 50*k));
        }
    }
}
