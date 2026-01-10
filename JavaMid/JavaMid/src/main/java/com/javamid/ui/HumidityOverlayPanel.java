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
import java.util.logging.Logger;

/**
 * Panel transparente que renderiza overlay de humedad.
 * Muestra círculos azules transparentes en el radio de influencia de cada estación
 * con intensidad proporcional al nivel de humedad.
 */
public class HumidityOverlayPanel extends JPanel {
    
    private static final Logger LOGGER = Logger.getLogger(HumidityOverlayPanel.class.getName());
    
    private final JXMapViewer mapViewer;
    private List<WeatherStation> stations = new ArrayList<>();
    private Map<String, Double> stationHumidity = new HashMap<>();
    private double influenceRadiusKm = MapConfig.STATION_GENERATION_RADIUS_KM;
    private boolean active = false;
    private StationMarkerPanel stationMarkerPanel;
    
    public HumidityOverlayPanel(JXMapViewer mapViewer) {
        this.mapViewer = mapViewer;
        setOpaque(false);
        setBackground(new Color(0, 0, 0, 0));
    }
    
    public void setStations(List<WeatherStation> stations) {
        this.stations = stations != null ? new ArrayList<>(stations) : new ArrayList<>();
        repaint();
    }
    
    public void setStationHumidity(String stationId, double humidity) {
        stationHumidity.put(stationId, humidity);
        LOGGER.info(String.format("Station %s humidity set to %.1f%%. Total stations with data: %d", 
                                  stationId, humidity, stationHumidity.size()));
        if (active) {
            repaint();
        }
    }
    
    public boolean hasHumidityData(String stationId) {
        return stationHumidity.containsKey(stationId);
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
        LOGGER.info("Humidity layer " + (active ? "ACTIVATED" : "DEACTIVATED") + 
                   ". Stations: " + stations.size() + ", Humidity data: " + stationHumidity.size());
        if (active) {
            repaint();
        }
    }
    
    public boolean isActive() {
        return active;
    }

    public void setStationMarkerPanel(StationMarkerPanel panel) {
        this.stationMarkerPanel = panel;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        if (!active || stations.isEmpty() || mapViewer == null) {
            if (active) {
                LOGGER.warning("Humidity layer active but cannot paint: stations=" + stations.size() + ", mapViewer=" + (mapViewer != null));
            }
            return;
        }
        
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        int paintedStations = 0;
        try {
            // Si hay panel de marcadores, limitar a estaciones visibles no agrupadas
            List<WeatherStation> drawStations;
            if (stationMarkerPanel != null) {
                drawStations = stationMarkerPanel.getVisibleUnclusteredStations();
            } else {
                drawStations = stations;
            }

            // Dibujar círculos de humedad solo para estaciones permitidas
            for (WeatherStation station : drawStations) {
                Double humidity = stationHumidity.get(station.getId());
                if (humidity == null) {
                    continue; // Skip stations without humidity data
                }
                
                paintedStations++;
                
                // Convertir posición geográfica a píxeles de pantalla
                GeoPosition geoPos = new GeoPosition(station.getLatitude(), station.getLongitude());
                Point2D screenPos = mapViewer.getTileFactory().geoToPixel(geoPos, mapViewer.getZoom());
                Rectangle viewportBounds = mapViewer.getViewportBounds();
                
                int screenX = (int) (screenPos.getX() - viewportBounds.x);
                int screenY = (int) (screenPos.getY() - viewportBounds.y);
                
                // Calcular radio en píxeles basado en el zoom
                int radiusPixels = kmToPixels(influenceRadiusKm, station.getLatitude());
                
                // Calcular intensidad del color basado en humedad (0-100%)
                // Más humedad = mayor intensidad de color pero manteniendo más transparencia
                int alpha = (int) (humidity * 0.6); // Máximo ~60 de opacidad
                alpha = Math.max(12, Math.min(60, alpha)); // Entre 12 y 60
                
                // Azul más saturado para mayor intensidad visual
                Color base = new Color(0, 160, 255);
                
                // Dibujar círculo relleno con gradiente radial
                int diameter = radiusPixels * 2;
                
                // Crear gradiente radial desde el centro
                Point2D center = new Point2D.Float(screenX, screenY);
                float radius = radiusPixels;
                float[] dist = {0.0f, 0.7f, 1.0f};
                Color centerColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
                Color midColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.max(6, alpha / 2));
                Color edgeColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), 0);
                Color[] colors = {centerColor, midColor, edgeColor};
                
                RadialGradientPaint gradient = new RadialGradientPaint(
                    center, radius, dist, colors
                );
                
                g2d.setPaint(gradient);
                g2d.fillOval(screenX - radiusPixels, screenY - radiusPixels, diameter, diameter);
                
                // Opcional: Dibujar borde del círculo
                g2d.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.min(150, alpha * 2)));
                g2d.setStroke(new BasicStroke(1.5f));
                g2d.drawOval(screenX - radiusPixels, screenY - radiusPixels, diameter, diameter);
            }
            
            if (paintedStations > 0) {
                LOGGER.info("Painted " + paintedStations + " humidity circles. Radius: " + influenceRadiusKm + " km");
            }
        } finally {
            g2d.dispose();
        }
    }
    
    /**
     * Convierte kilómetros a píxeles basado en el nivel de zoom.
     */
    private int kmToPixels(double km, double latitude) {
        if (mapViewer == null) return 0;
        try {
            // Igualar el cálculo al de StationMarkerPanel para paridad visual
            double deltaLat = km / 111.0; // ~111 km por grado de latitud
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
    
    public void onMapChanged() {
        if (active) {
            repaint();
        }
    }
    
    public void clear() {
        stationHumidity.clear();
        repaint();
    }
}
