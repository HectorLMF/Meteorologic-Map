package com.javamid.ui;

import com.javamid.model.WeatherStation;
import com.javamid.flyweight.MarkerStyle;
import com.javamid.flyweight.MarkerStyleFactory;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel overlay transparente que dibuja marcadores en las ubicaciones de estaciones meteorológicas.
 * La estación activa se marca con circunferencia.
 * Las estaciones muy cercanas se agrupan en clusters.
 */
public class StationMarkerPanel extends JPanel {
    
    private static final double CLUSTER_DISTANCE_PX = 50.0; // Distancia máxima para agrupar
    
    // Caché de clustering
    private List<StationCluster> cachedClusters = new ArrayList<>();
    private int lastZoom = -1;
    private boolean clustersDirty = true;
    
    /**
     * Clase interna para representar un cluster de estaciones
     */
    private static class StationCluster {
        List<WeatherStation> stations = new ArrayList<>();

        StationCluster(WeatherStation first, Point2D.Double screenPos) {
            stations.add(first);
        }

        void addStation(WeatherStation station, Point2D.Double screenPos) {
            stations.add(station);
        }

        int getCount() {
            return stations.size();
        }

        boolean contains(WeatherStation station) {
            return stations.contains(station);
        }
    }
    
    private final JXMapViewer mapViewer;
    private List<WeatherStation> allStations = new ArrayList<>();
    private List<WeatherStation> visibleStations = new ArrayList<>();
    private WeatherStation activeStation;
    private Consumer<WeatherStation> onStationSelected;
    private double influenceRadiusKm = 5.0; // Radio de influencia en kilómetros
    
    public StationMarkerPanel(JXMapViewer mapViewer) {
        this.mapViewer = mapViewer;
        setOpaque(false); // Fondo transparente
        setLayout(null);
        
        // Detectar clics en estaciones
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!handleStationClick(e.getX(), e.getY())) {
                    // Si no se clickeó en una estación, reenviar el evento al mapa
                    redispatchMouseEvent(e);
                }
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                redispatchMouseEvent(e);
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                redispatchMouseEvent(e);
            }
        });
        
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                redispatchMouseEvent(e);
            }
            
            @Override
            public void mouseMoved(MouseEvent e) {
                redispatchMouseEvent(e);
            }
        });
        
        addMouseWheelListener(e -> redispatchMouseWheelEvent(e));

        // Recalcular visibilidad y clusters cuando cambie el mapa (pan/zoom) o el tamaño del panel
        mapViewer.addPropertyChangeListener(evt -> {
            String name = evt.getPropertyName();
            if ("zoom".equals(name) || "center".equals(name) || "centerPosition".equals(name) || "addressLocation".equals(name)) {
                clustersDirty = true;
                repaint();
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                clustersDirty = true;
                repaint();
            }
        });
    }
    
    /**
     * Establece todas las estaciones visibles
     */
    public void setStations(List<WeatherStation> stations) {
        this.allStations = stations != null ? new ArrayList<>(stations) : new ArrayList<>();
        clustersDirty = true; // Invalidar caché de clustering y visibilidad
        updateVisibleStations();
        
        System.out.println("[STATIONS] Setting " + allStations.size() + " stations");
        
        // Si no hay estación activa, seleccionar la más cercana al centro
        if (activeStation == null && !allStations.isEmpty()) {
            WeatherStation closest = findClosestToCenter();
            System.out.println("[STATIONS] Found closest station: " + (closest != null ? closest.getName() + " (" + closest.getId() + ")" : "null"));
            
            if (closest != null) {
                setActiveStation(closest);
            }
        }
        
        repaint();
    }
    
    /**
     * Establece la estación activa
     */
    public void setActiveStation(WeatherStation station) {
        this.activeStation = station;
        repaint();
        
        // Notificar al listener
        if (onStationSelected != null && station != null) {
            onStationSelected.accept(station);
        }
    }
    
    /**
     * Obtiene la estación activa
     */
    public WeatherStation getActiveStation() {
        return activeStation;
    }
    
    /**
     * Obtiene todas las estaciones visibles
     */
    public List<WeatherStation> getStations() {
        return new ArrayList<>(allStations);
    }
    
    /**
     * Establece el listener para cuando se selecciona una estación
     */
    public void setOnStationSelected(Consumer<WeatherStation> listener) {
        this.onStationSelected = listener;
    }
    
    /**
     * Limpia todos los marcadores
     */
    public void clearStations() {
        this.allStations.clear();
        this.visibleStations.clear();
        this.activeStation = null;
        repaint();
    }
    
    /**
     * Establece el radio de influencia en kilómetros
     */
    public void setInfluenceRadiusKm(double radiusKm) {
        this.influenceRadiusKm = radiusKm;
        repaint();
    }
    
    /**
     * Encuentra la estación más cercana al centro del mapa
     */
    private WeatherStation findClosestToCenter() {
        if (allStations.isEmpty()) {
            return null;
        }
        
        GeoPosition center = mapViewer.getCenterPosition();
        double centerLat = center.getLatitude();
        double centerLon = center.getLongitude();
        
        WeatherStation closest = null;
        double minDistance = Double.MAX_VALUE;
        
        for (WeatherStation station : allStations) {
            double distance = calculateDistance(
                centerLat, centerLon,
                station.getLatitude(), station.getLongitude()
            );
            
            if (distance < minDistance) {
                minDistance = distance;
                closest = station;
            }
        }
        
        return closest;
    }
    
    /**
     * Calcula distancia en km entre dos puntos (fórmula simplificada)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }
    
    /**
     * Agrupa estaciones que están muy cerca visualmente
     */
    private List<StationCluster> createClusters() {
        List<StationCluster> clusters = new ArrayList<>();
        List<WeatherStation> processed = new ArrayList<>();
        
        for (WeatherStation station : visibleStations) {
            if (processed.contains(station)) continue;
            
            Point2D screenPos = getScreenPosition(station);
            if (screenPos == null) continue;
            if (!isOnScreen(screenPos, 0)) continue;
            
            // Crear nuevo cluster con esta estación
            StationCluster cluster = new StationCluster(station, new Point2D.Double(screenPos.getX(), screenPos.getY()));
            processed.add(station);
            
            // Buscar otras estaciones cercanas para agregar al cluster
            for (WeatherStation other : visibleStations) {
                if (processed.contains(other)) continue;
                
                Point2D otherPos = getScreenPosition(other);
                if (otherPos == null) continue;
                if (!isOnScreen(otherPos, 0)) continue;
                
                double dx = screenPos.getX() - otherPos.getX();
                double dy = screenPos.getY() - otherPos.getY();
                double distance = Math.sqrt(dx * dx + dy * dy);
                
                if (distance < CLUSTER_DISTANCE_PX) {
                    cluster.addStation(other, new Point2D.Double(otherPos.getX(), otherPos.getY()));
                    processed.add(other);
                }
            }
            
            clusters.add(cluster);
        }
        
        return clusters;
    }

    /**
     * Asegura que la caché de clusters esté actualizada para el zoom/viewport actual.
     */
    private void ensureClustersComputed() {
        int currentZoom = mapViewer.getZoom();
        if (currentZoom != lastZoom) {
            clustersDirty = true;
            lastZoom = currentZoom;
        }
        if (clustersDirty) {
            updateVisibleStations();
            cachedClusters = createClusters();
            clustersDirty = false;
        }
    }

    /**
     * Devuelve las estaciones visibles que no están agrupadas (clusters de tamaño 1).
     */
    public List<WeatherStation> getVisibleUnclusteredStations() {
        ensureClustersComputed();
        List<WeatherStation> result = new ArrayList<>();
        for (StationCluster c : cachedClusters) {
            if (c.getCount() == 1 && !c.stations.isEmpty()) {
                result.add(c.stations.get(0));
            }
        }
        return result;
    }
    
    /**
     * Maneja el clic en una estación
     * @return true si se clickeó en una estación, false si no
     */
    private boolean handleStationClick(int mouseX, int mouseY) {
        if (visibleStations.isEmpty()) {
            return false;
        }
        
        // Buscar la estación más cercana al punto clicado (dentro de un radio)
        WeatherStation clickedStation = null;
        double minDistance = 25.0; // Radio de detección en píxeles (aumentado por iconos más grandes)
        
        for (WeatherStation station : visibleStations) {
            Point2D screenPos = getScreenPosition(station);
            if (screenPos == null) continue;
            
            double dx = screenPos.getX() - mouseX;
            double dy = screenPos.getY() - mouseY;
            double distance = Math.sqrt(dx * dx + dy * dy);
            
            if (distance < minDistance) {
                minDistance = distance;
                clickedStation = station;
            }
        }
        
        if (clickedStation != null) {
            setActiveStation(clickedStation);
            return true;
        }
        
        return false;
    }
    
    /**
     * Reenvía eventos de mouse al mapa para permitir pan/zoom
     */
    private void redispatchMouseEvent(MouseEvent e) {
        Point point = SwingUtilities.convertPoint(this, e.getPoint(), mapViewer);
        MouseEvent newEvent = new MouseEvent(
            mapViewer,
            e.getID(),
            e.getWhen(),
            e.getModifiersEx(),
            point.x,
            point.y,
            e.getClickCount(),
            e.isPopupTrigger(),
            e.getButton()
        );
        mapViewer.dispatchEvent(newEvent);
    }
    
    /**
     * Reenvía eventos de scroll al mapa para permitir zoom
     */
    private void redispatchMouseWheelEvent(java.awt.event.MouseWheelEvent e) {
        Point point = SwingUtilities.convertPoint(this, e.getPoint(), mapViewer);
        java.awt.event.MouseWheelEvent newEvent = new java.awt.event.MouseWheelEvent(
            mapViewer,
            e.getID(),
            e.getWhen(),
            e.getModifiersEx(),
            point.x,
            point.y,
            e.getClickCount(),
            e.isPopupTrigger(),
            e.getScrollType(),
            e.getScrollAmount(),
            e.getWheelRotation()
        );
        mapViewer.dispatchEvent(newEvent);
    }
    
    /**
     * Convierte las coordenadas geográficas a posición en pantalla
     * Reformado para ser consistente con responsive layout
     */
    private Point2D getScreenPosition(WeatherStation station) {
        if (station == null) {
            return null;
        }
        
        try {
            GeoPosition stationPos = new GeoPosition(station.getLatitude(), station.getLongitude());
            
            // Convertir posición geográfica a píxeles en el sistema de coordenadas del mapa
            Point2D worldPoint = mapViewer.getTileFactory().geoToPixel(stationPos, mapViewer.getZoom());
            
            // Obtener el centro del mapa en coordenadas del mundo
            GeoPosition mapCenter = mapViewer.getCenterPosition();
            Point2D centerWorldPoint = mapViewer.getTileFactory().geoToPixel(mapCenter, mapViewer.getZoom());
            
            // Calcular el offset desde el centro del mapa
            double offsetX = worldPoint.getX() - centerWorldPoint.getX();
            double offsetY = worldPoint.getY() - centerWorldPoint.getY();
            
            // Convertir a coordenadas de pantalla
            // El centro del panel está en (width/2, height/2)
            int screenX = (int) (getWidth() / 2.0 + offsetX);
            int screenY = (int) (getHeight() / 2.0 + offsetY);
            
            return new Point2D.Double(screenX, screenY);
            
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to calculate screen position for station: " + station.getName() + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Indica si una posición está dentro del panel visible (con margen opcional)
     */
    private boolean isOnScreen(Point2D pos, int marginPx) {
        int w = getWidth();
        int h = getHeight();
        double x = pos.getX();
        double y = pos.getY();
        return x >= -marginPx && x <= (w + marginPx) && y >= -marginPx && y <= (h + marginPx);
    }

    /**
     * Actualiza la lista de estaciones visibles según el viewport actual
     */
    private void updateVisibleStations() {
        visibleStations.clear();
        if (allStations.isEmpty()) return;
        for (WeatherStation s : allStations) {
            Point2D p = getScreenPosition(s);
            if (p != null && isOnScreen(p, 0)) {
                visibleStations.add(s);
            }
        }
        // Si la estación activa ya no es visible, deseleccionarla
        if (activeStation != null) {
            Point2D ap = getScreenPosition(activeStation);
            if (ap == null || !isOnScreen(ap, 0)) {
                setActiveStation(null);
            }
        }
    }
    
    /**
     * Convierte kilómetros a píxeles en pantalla según el zoom actual
     * Usa el mismo sistema de coordenadas que el mapa para ser consistente
     */
    private double kmToPixels(double km, double latitude) {
        try {
            // Convertir km a grados de latitud (aproximación: 1 grado ≈ 111 km)
            double deltaLat = km / 111.0;
            
            // Crear dos posiciones: el punto original y un punto a 'km' kilómetros al norte
            GeoPosition point1 = new GeoPosition(latitude, 0.0);
            GeoPosition point2 = new GeoPosition(latitude + deltaLat, 0.0);
            
            // Convertir ambos puntos a píxeles en el sistema de coordenadas del mundo
            Point2D worldPoint1 = mapViewer.getTileFactory().geoToPixel(point1, mapViewer.getZoom());
            Point2D worldPoint2 = mapViewer.getTileFactory().geoToPixel(point2, mapViewer.getZoom());
            
            // La diferencia en píxeles es el radio que necesitamos
            double radiusPixels = Math.abs(worldPoint2.getY() - worldPoint1.getY());
            
            return radiusPixels;
            
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to calculate km to pixels: " + e.getMessage());
            // Fallback a cálculo aproximado
            int zoom = mapViewer.getZoom();
            double metersPerPixel = (40075000.0 * Math.cos(Math.toRadians(latitude))) / (256.0 * Math.pow(2, zoom));
            double meters = km * 1000.0;
            return meters / metersPerPixel;
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        if (allStations.isEmpty()) {
            return;
        }
        
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Verificar si cambió el zoom
        int currentZoom = mapViewer.getZoom();
        if (currentZoom != lastZoom) {
            clustersDirty = true;
            lastZoom = currentZoom;
        }
        
        // Recalcular clusters solo si es necesario
        if (clustersDirty) {
            updateVisibleStations();
            cachedClusters = createClusters();
            clustersDirty = false;
        }
        
        // Primero dibujar circunferencias de influencia (solo para estaciones individuales o activas)
        for (StationCluster cluster : cachedClusters) {
            if (cluster.getCount() == 1) {
                WeatherStation station = cluster.stations.get(0);
                Point2D screenPos = getScreenPosition(station);
                if (screenPos == null) continue;
                
                int x = (int) screenPos.getX();
                int y = (int) screenPos.getY();
                
                // Calcular radio en píxeles
                double radiusPixels = kmToPixels(influenceRadiusKm, station.getLatitude());
                
                // Dibujar circunferencia de influencia con relleno gris y borde negro
                Ellipse2D influenceCircle = new Ellipse2D.Double(
                    x - radiusPixels,
                    y - radiusPixels,
                    radiusPixels * 2,
                    radiusPixels * 2
                );
                
                // Primero el relleno gris semi-transparente
                g2.setColor(new Color(128, 128, 128, 40));
                g2.fill(influenceCircle);
                
                // Luego el borde negro grueso
                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(3.0f));
                g2.draw(influenceCircle);
            }
        }
        
        // Ahora dibujar marcadores (clusters o estaciones individuales)
        for (StationCluster cluster : cachedClusters) {
            if (cluster.getCount() > 1) {
                // Calcular centro del cluster en cada repintado (sigue el drag/pan)
                double sumX = 0.0;
                double sumY = 0.0;
                int validCount = 0;
                for (WeatherStation s : cluster.stations) {
                    Point2D pos = getScreenPosition(s);
                    if (pos != null) {
                        sumX += pos.getX();
                        sumY += pos.getY();
                        validCount++;
                    }
                }
                if (validCount == 0) {
                    continue;
                }
                int x = (int) Math.round(sumX / validCount);
                int y = (int) Math.round(sumY / validCount);
                int size = 40;

                // Cuadrado con relleno azul semi-transparente
                g2.setColor(new Color(50, 100, 200, 150));
                Rectangle2D clusterRect = new Rectangle2D.Double(x - size/2, y - size/2, size, size);
                g2.fill(clusterRect);

                // Borde negro
                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(2.0f));
                g2.draw(clusterRect);

                // Número de estaciones en el cluster
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Arial", Font.BOLD, 14));
                String count = String.valueOf(cluster.getCount());
                FontMetrics fm = g2.getFontMetrics();
                int textWidth = fm.stringWidth(count);
                int textHeight = fm.getAscent();
                g2.drawString(count, x - textWidth/2, y + textHeight/2 - 2);

            } else {
                // Dibujar estación individual (círculo)
                WeatherStation station = cluster.stations.get(0);
                Point2D screenPos = getScreenPosition(station);
                if (screenPos == null) continue;
                
                int x = (int) screenPos.getX();
                int y = (int) screenPos.getY();
                
                boolean isActive = station.equals(activeStation);
                
                if (isActive) {
                    // Dibujar circunferencia exterior solo para la estación activa
                    int outerRadius = 50;
                    g2.setColor(new Color(0, 255, 0, 80)); // Verde semi-transparente
                    g2.setStroke(new BasicStroke(3.0f));
                    Ellipse2D outerCircle = new Ellipse2D.Double(
                        x - outerRadius, 
                        y - outerRadius, 
                        outerRadius * 2, 
                        outerRadius * 2
                    );
                    g2.draw(outerCircle);
                    
                    // Dibujar circunferencia interior
                    int innerRadius = 25;
                    g2.setColor(new Color(100, 255, 100, 120)); // Verde claro semi-transparente
                    Ellipse2D innerCircle = new Ellipse2D.Double(
                        x - innerRadius, 
                        y - innerRadius, 
                        innerRadius * 2, 
                        innerRadius * 2
                    );
                    g2.fill(innerCircle);
                    g2.setColor(new Color(0, 200, 0, 200));
                    g2.draw(innerCircle);
                }
                
                // Dibujar punto central (marcador) - para todas las estaciones
                int markerRadius = isActive ? 10 : 8;
                g2.setColor(new Color(255, 255, 255, 255)); // Blanco sólido
                Ellipse2D markerOuter = new Ellipse2D.Double(
                    x - markerRadius, 
                    y - markerRadius, 
                    markerRadius * 2, 
                    markerRadius * 2
                );
                g2.fill(markerOuter);
                
                // Punto central
                int centerRadius = isActive ? 6 : 5;
                g2.setColor(isActive ? new Color(0, 180, 0, 255) : new Color(0, 150, 0, 255)); // Verde
                Ellipse2D markerCenter = new Ellipse2D.Double(
                    x - centerRadius, 
                    y - centerRadius, 
                    centerRadius * 2, 
                    centerRadius * 2
                );
                g2.fill(markerCenter);
            }
        }
        
        g2.dispose();
    }
}
