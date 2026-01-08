package com.javamid.ui;

import com.javamid.flyweight.ParticleStyle;
import com.javamid.flyweight.WeatherFlyweightFactory;
import com.javamid.model.WeatherStation;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.AlphaComposite;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Transparent overlay panel que renderiza partículas de viento animadas.
 * Panel optimizado con Object Pooling, Spatial Grid, Velocity Cache y Batch Rendering.
 * 
 * OPTIMIZACIONES IMPLEMENTADAS:
 * - Object Pool: Reutiliza WindParticle para reducir GC pressure
 * - Velocity Cache: Cachea cálculos trigonométricos costosos
 * - Spatial Grid: Búsqueda O(1) de estaciones cercanas
 * - Batch Rendering: Renderiza a BufferedImage antes de mostrar
 * - CopyOnWriteArrayList: Sin locks contenciosos
 * - Square Distance: Evita sqrt innecesarios
 * - Lock-Free donde posible
 */
public class WindOverlayPanel extends JPanel {
    
    private static final Logger LOGGER = Logger.getLogger(WindOverlayPanel.class.getName());
    
    private final JXMapViewer mapViewer;
    private List<WeatherStation> stations = new ArrayList<>();
    private double influenceRadiusKm = 5.0;
    private java.util.Map<String, WindData> stationWindData = new java.util.HashMap<>();
    
    // Caché geográfico optimizado con límite de tamaño
    private static final int MAX_CACHE_SIZE = 200; // Límite para evitar memory leak
    private java.util.Map<String, Point2D> screenPosCache = new java.util.LinkedHashMap<String, Point2D>(MAX_CACHE_SIZE + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, Point2D> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };
    private java.util.Map<Integer, Double> kmToPixelsCache = new java.util.LinkedHashMap<Integer, Double>(100, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Integer, Double> eldest) {
            return size() > 100;
        }
    };
    private int lastZoom = -1;
    private GeoPosition lastCenterPosition = null; // Detectar pan del mapa
    
    // Round-robin para distribución equitativa de partículas
    private int nextStationIndex = 0;
    private List<WeatherStation> cachedVisibleStations = new ArrayList<>();
    private long lastVisibleStationsUpdate = 0;
    
    // === NUEVAS OPTIMIZACIONES ===
    
    // Pool de partículas POR ESTACIÓN (cada estación tiene su propio pool y máximo)
    private java.util.Map<String, StationParticlePool> stationPools = new java.util.HashMap<>();
    
    // Lock-free particle list (CopyOnWriteArrayList para renders sin sincronización)
    private final CopyOnWriteArrayList<WindParticle> particles;
    
    // Velocity Cache: Cachear trigonometría
    private final VelocityCache velocityCache;
    
    // Spatial Grid: Búsqueda O(1) de estaciones
    private SpatialGrid spatialGrid;
    
    // Performance Metrics: Monitorear mejoras
    private final PerformanceMetrics performanceMetrics;
    
    // Batch Rendering: BufferedImage para renderizado más suave
    private BufferedImage renderBuffer;
    private Graphics2D bufferGraphics;
    
    // Reutilizar objetos para evitar allocations
    private final BasicStroke[] strokeCache = new BasicStroke[10];
    private Point2D.Double tmpPoint = new Point2D.Double(0, 0);
    
    // ===
    
    private final Timer animationTimer;
    private volatile double windSpeed = 0.0; // m/s
    private volatile double windDeg = 0.0; // degrees
    private final Random rnd = new Random();
    private boolean isRunning = false;
    private int paintCallCount = 0;
    private int maxParticlesPerStation = 120;  // Máximo POR ESTACIÓN
    private float sizeScale = 1.0f;
    private float speedScale = 50.0f;
    
    // Stats para debug
    private long lastStatsTime = 0;
    private static final long STATS_INTERVAL_MS = 1000;

    public WindOverlayPanel(JXMapViewer mapViewer) {
        this.mapViewer = mapViewer;
        this.particles = new CopyOnWriteArrayList<>();
        this.velocityCache = new VelocityCache();
        this.performanceMetrics = new PerformanceMetrics();
        
        setOpaque(false);
        setLayout(null);
        
        // Pre-create stroke cache
        for (int i = 0; i < strokeCache.length; i++) {
            float width = 0.8f + (i * 0.5f);
            strokeCache[i] = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        }
        
        // 30 FPS animation timer
        animationTimer = new Timer(33, e -> {
            performanceMetrics.frameStart();
            step(0.033);
            repaint();
            performanceMetrics.frameEnd();
        });
        animationTimer.setCoalesce(true);
        
        LOGGER.info("[WIND] WindOverlayPanel optimizado inicializado con pools por estación");
    }

    public void startAnimation() {
        if (!isRunning) {
            LOGGER.info("[WIND] Iniciando animación de viento con optimizaciones...");
            populateInitialParticles();
            spatialGrid = new SpatialGrid(getWidth(), getHeight());
            animationTimer.start();
            isRunning = true;
            LOGGER.info("[WIND] Animación iniciada. Partículas creadas: " + particles.size());
        }
    }

    public void stopAnimation() {
        if (isRunning) {
            LOGGER.info("[WIND] Deteniendo animación de viento...");
            animationTimer.stop();
            
            // Devolver todas las partículas a sus pools respectivos
            for (WindParticle p : particles) {
                String stationId = p.getStationId();
                if (stationId != null) {
                    StationParticlePool pool = stationPools.get(stationId);
                    if (pool != null) {
                        pool.release(p);
                    }
                }
            }
            particles.clear();
            
            // Limpiar pools
            stationPools.clear();
            
            if (bufferGraphics != null) {
                bufferGraphics.dispose();
                bufferGraphics = null;
            }
            
            isRunning = false;
            repaint();
            
            // Log stats finales
            LOGGER.info(performanceMetrics.getReport().toString());
        }
    }

    public void setParticleCount(int count) {
        this.maxParticlesPerStation = Math.max(10, Math.min(500, count));
        // No eliminamos partículas existentes, dejamos que el sistema se ajuste naturalmente
    }
    
    public void setStations(List<WeatherStation> stations) {
        // Optimización: Solo cargar estaciones que podrían ser visibles
        // Para evitar mantener miles de estaciones en memoria
        if (stations == null) {
            this.stations = new ArrayList<>();
            return;
        }
        
        // NUEVO: Filtrar estaciones por región visible actual para optimizar memoria
        List<WeatherStation> filteredStations = new ArrayList<>();
        
        // Si el mapViewer está disponible, filtrar por región amplia
        if (mapViewer != null && mapViewer.getCenterPosition() != null) {
            try {
                GeoPosition center = mapViewer.getCenterPosition();
                double centerLat = center.getLatitude();
                double centerLon = center.getLongitude();
                
                // Calcular un radio de búsqueda generoso basado en el zoom
                // Zoom más alejado = radio más grande
                int zoom = mapViewer.getZoom();
                // A zoom 5, buscar en ~500km de radio; a zoom 10, ~50km
                double searchRadiusKm = 1000.0 / Math.pow(2, zoom - 4);
                searchRadiusKm = Math.max(50, Math.min(searchRadiusKm, 1000)); // Entre 50km y 1000km
                
                for (WeatherStation station : stations) {
                    double distance = calculateDistance(
                        centerLat, centerLon,
                        station.getLatitude(), station.getLongitude()
                    );
                    
                    if (distance <= searchRadiusKm) {
                        filteredStations.add(station);
                    }
                }
                
                LOGGER.info(String.format("[WIND] Filtered stations: %d visible within %.0fkm of %d total stations (zoom=%d)",
                    filteredStations.size(), searchRadiusKm, stations.size(), zoom));
            } catch (Exception e) {
                // Si falla el filtrado, usar todas las estaciones
                LOGGER.warning("[WIND] Failed to filter stations, using all: " + e.getMessage());
                filteredStations = new ArrayList<>(stations);
            }
        } else {
            // Si no hay mapViewer, usar todas las estaciones
            filteredStations = new ArrayList<>(stations);
        }
        
        this.stations = filteredStations;
        
        // Invalidar grid para que se reconstruya con nuevas estaciones
        if (spatialGrid != null) {
            spatialGrid = new SpatialGrid(getWidth(), getHeight());
        }
        
        // Invalidar cachés
        screenPosCache.clear();
        kmToPixelsCache.clear();
        cachedVisibleStations.clear();
        lastVisibleStationsUpdate = 0;
    }
    
    /**
     * Calcula la distancia entre dos puntos geográficos usando la fórmula de Haversine.
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371 * c; // Radio de la Tierra en km
    }
    
    public List<WeatherStation> getVisibleStations() {
        updateVisibleStations();
        return new ArrayList<>(cachedVisibleStations);
    }

    public void setInfluenceRadiusKm(double radiusKm) {
        this.influenceRadiusKm = radiusKm;
    }
    
    public void onMapChanged() {
        screenPosCache.clear();
        kmToPixelsCache.clear();
        lastZoom = -1;
        
        // Invalidar caché de estaciones visibles
        cachedVisibleStations.clear();
        lastVisibleStationsUpdate = 0; // FORZAR actualización inmediata
        nextStationIndex = 0;
        
        // CRÍTICO: Forzar actualización de estaciones visibles AHORA
        updateVisibleStations();
        
        // Limpiar partículas y pools para regenerarse
        for (WindParticle p : particles) {
            String stationId = p.getStationId();
            if (stationId != null) {
                StationParticlePool pool = stationPools.get(stationId);
                if (pool != null) {
                    pool.release(p);
                }
            }
        }
        particles.clear();
        stationPools.clear();
        
        LOGGER.info("[WIND] Map changed - cleared all particles and pools");
        
        repaint();
    }
    
    public void setSizeScale(float scale) {
        this.sizeScale = Math.max(0.1f, Math.min(5.0f, scale));
        repaint();
    }
    
    public void setSpeedScale(float scale) {
        this.speedScale = Math.max(1.0f, Math.min(100.0f, scale));
        velocityCache.clear();  // Limpiar caché de velocidades
    }
    
    public double getAverageParticleSpeed() {
        if (particles.isEmpty()) return 0.0;
        double totalSpeed = 0.0;
        for (WindParticle p : particles) {
            double vx = p.getVx();
            double vy = p.getVy();
            totalSpeed += Math.sqrt(vx * vx + vy * vy);
        }
        return totalSpeed / particles.size();
    }
    
    /**
     * Verifica si un punto está dentro de algún círculo de influencia VISIBLE.
     * Optimizado con square distance (sin sqrt) y solo verifica estaciones visibles.
     */
    private boolean isInsideInfluenceCircle(double screenX, double screenY) {
        updateVisibleStations();
        
        if (cachedVisibleStations.isEmpty()) {
            return false;
        }
        
        for (WeatherStation station : cachedVisibleStations) {
            Point2D screenPos = getScreenPosition(station);
            if (screenPos == null) continue;
            
            double radiusPixels = kmToPixels(influenceRadiusKm, station.getLatitude());
            if (radiusPixels <= 0) continue;
            
            // Square distance: evitar sqrt costoso
            double dx = screenX - screenPos.getX();
            double dy = screenY - screenPos.getY();
            double distanceSquared = dx * dx + dy * dy;
            double radiusSquared = radiusPixels * radiusPixels;
            
            if (distanceSquared <= radiusSquared) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Obtiene posición en pantalla con caché.
     * IMPORTANTE: La invalidación de caché se hace en updateVisibleStations() y onMapChanged().
     */
    private Point2D getScreenPosition(WeatherStation station) {
        if (station == null || mapViewer == null) {
            return null;
        }
        
        String cacheKey = station.getId();
        Point2D cached = screenPosCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        try {
            GeoPosition stationPos = new GeoPosition(station.getLatitude(), station.getLongitude());
            Point2D worldPoint = mapViewer.getTileFactory().geoToPixel(stationPos, mapViewer.getZoom());
            GeoPosition mapCenter = mapViewer.getCenterPosition();
            Point2D centerWorldPoint = mapViewer.getTileFactory().geoToPixel(mapCenter, mapViewer.getZoom());
            
            double offsetX = worldPoint.getX() - centerWorldPoint.getX();
            double offsetY = worldPoint.getY() - centerWorldPoint.getY();
            
            int screenX = (int) (getWidth() / 2.0 + offsetX);
            int screenY = (int) (getHeight() / 2.0 + offsetY);
            
            Point2D result = new Point2D.Double(screenX, screenY);
            screenPosCache.put(cacheKey, result);
            return result;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Convierte km a píxeles (caché optimizado con Integer key en lugar de String).
     */
    private double kmToPixels(double km, double latitude) {
        // Crear clave numérica en lugar de String
        int cacheKey = (int) (km * 10) * 1000 + (int) (latitude * 10);
        Double cached = kmToPixelsCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        try {
            double deltaLat = km / 111.0;
            GeoPosition point1 = new GeoPosition(latitude, 0.0);
            GeoPosition point2 = new GeoPosition(latitude + deltaLat, 0.0);
            
            Point2D worldPoint1 = mapViewer.getTileFactory().geoToPixel(point1, mapViewer.getZoom());
            Point2D worldPoint2 = mapViewer.getTileFactory().geoToPixel(point2, mapViewer.getZoom());
            
            double result = Math.abs(worldPoint2.getY() - worldPoint1.getY());
            kmToPixelsCache.put(cacheKey, result);
            return result;
        } catch (Exception e) {
            int zoom = mapViewer.getZoom();
            double metersPerPixel = (40075000.0 * Math.cos(Math.toRadians(latitude))) / (256.0 * Math.pow(2, zoom));
            return (km * 1000.0) / metersPerPixel;
        }
    }
    
    /**
     * Datos de viento por estación.
     */
    private static class WindData {
        double speedMs;
        double directionDeg;
        
        WindData(double speedMs, double directionDeg) {
            this.speedMs = speedMs;
            this.directionDeg = directionDeg;
        }
    }
    
    public void setStationWind(String stationId, double speedMs, double directionDeg) {
        stationWindData.put(stationId, new WindData(speedMs, directionDeg));
    }
    
    /**
     * Obtiene la estación más cercana dentro del radio de influencia.
     * Ahora usa Spatial Grid para O(1) búsqueda.
     */
    private WeatherStation getControllingStation(double screenX, double screenY) {
        if (spatialGrid == null || stations.isEmpty()) {
            return null;
        }
        
        double radiusPixels = kmToPixels(influenceRadiusKm, 45.0);  // Aproximación
        return spatialGrid.getClosestStation(screenX, screenY, radiusPixels);
    }

    public void setWind(double speedMetersPerSecond, double deg) {
        this.windSpeed = speedMetersPerSecond;
        this.windDeg = deg;
        velocityCache.clear();  // Limpiar caché cuando cambia viento
        
        LOGGER.fine(String.format("[WIND] Viento actualizado: %.2f m/s a %.1f°", 
            speedMetersPerSecond, deg));
    }

    private void populateInitialParticles() {
        particles.clear();
        int w = getWidth();
        int h = getHeight();
        LOGGER.info(String.format("[WIND] Tamaño de panel: %dx%d", w, h));
        
        if (w <= 0 || h <= 0) {
            LOGGER.warning("[WIND] El panel no tiene tamaño válido aún");
        }
    }

    /**
     * Actualiza la lista de estaciones visibles (caché con TTL de 100ms).
     * Solo estaciones cuyos círculos de influencia estén en pantalla.
     * IMPORTANTE: Asigna viento global por defecto a estaciones sin datos específicos.
     */
    private void updateVisibleStations() {
        long now = System.currentTimeMillis();
        // CORREGIDO: TTL reducido a 100ms para respuesta rápida
        if (now - lastVisibleStationsUpdate < 100 && !cachedVisibleStations.isEmpty()) {
            return; // Caché válido
        }
        
        // NUEVO: Verificar si el zoom o centro cambió y forzar invalidación de caché
        int currentZoom = mapViewer.getZoom();
        GeoPosition currentCenter = mapViewer.getCenterPosition();
        
        if (currentZoom != lastZoom || lastCenterPosition == null || 
            (currentCenter != null && lastCenterPosition != null &&
             (Math.abs(currentCenter.getLatitude() - lastCenterPosition.getLatitude()) > 0.001 ||
              Math.abs(currentCenter.getLongitude() - lastCenterPosition.getLongitude()) > 0.001))) {
            // El mapa cambió significativamente, invalidar TODO
            screenPosCache.clear();
            kmToPixelsCache.clear();
            lastZoom = currentZoom;
            lastCenterPosition = currentCenter;
        }
        
        int previousVisibleCount = cachedVisibleStations.size();
        cachedVisibleStations.clear();
        int w = getWidth();
        int h = getHeight();
        
        int totalChecked = 0;
        int markedVisible = 0;
        int outOfBounds = 0;
        int nullPositions = 0;
        
        // Debug: Log primera estación para diagnosticar
        boolean debugLogged = false;
        
        for (WeatherStation station : stations) {
            totalChecked++;
            Point2D screenPos = getScreenPosition(station);
            if (screenPos == null) {
                nullPositions++;
                outOfBounds++;
                continue;
            }
            
            double x = screenPos.getX();
            double y = screenPos.getY();
            
            // NUEVO: Rechazar posiciones claramente erróneas (probablemente de caché corrupto)
            // Si la posición está a más de 100x el tamaño del viewport, es claramente incorrecta
            double maxReasonableDistance = Math.max(w, h) * 100;
            if (Math.abs(x) > maxReasonableDistance || Math.abs(y) > maxReasonableDistance) {
                // Posición claramente incorrecta, remover de caché y recalcular
                screenPosCache.remove(station.getId());
                // Intentar recalcular
                screenPos = getScreenPosition(station);
                if (screenPos == null) {
                    nullPositions++;
                    outOfBounds++;
                    continue;
                }
                x = screenPos.getX();
                y = screenPos.getY();
                
                // Si sigue siendo inválida, saltar esta estación
                if (Math.abs(x) > maxReasonableDistance || Math.abs(y) > maxReasonableDistance) {
                    outOfBounds++;
                    continue;
                }
            }
            
            double radiusPixels = kmToPixels(influenceRadiusKm, station.getLatitude());
            
            // NUEVO: Usar un margen generoso que funcione en todos los niveles de zoom
            // Margen mínimo de 100 píxeles o 2× el radio de influencia (lo que sea mayor)
            double margin = Math.max(100.0, radiusPixels * 2.0);
            
            // Debug: Log primera estación para entender el problema
            if (!debugLogged && totalChecked == 1) {
                try {
                    GeoPosition stationGeo = new GeoPosition(station.getLatitude(), station.getLongitude());
                    GeoPosition mapCenter = mapViewer.getCenterPosition();
                    Point2D worldPoint = mapViewer.getTileFactory().geoToPixel(stationGeo, currentZoom);
                    Point2D centerWorldPoint = mapViewer.getTileFactory().geoToPixel(mapCenter, currentZoom);
                    double offsetX = worldPoint.getX() - centerWorldPoint.getX();
                    double offsetY = worldPoint.getY() - centerWorldPoint.getY();
                    
                    LOGGER.info(String.format("[WIND DEBUG] First station: geo=(%.4f,%.4f) mapCenter=(%.4f,%.4f) worldOffset=(%.1f,%.1f) screenPos=(%.1f,%.1f) viewport=%dx%d margin=%.1f",
                        station.getLatitude(), station.getLongitude(),
                        mapCenter.getLatitude(), mapCenter.getLongitude(),
                        offsetX, offsetY,
                        x, y, w, h, margin));
                } catch (Exception e) {
                    LOGGER.warning("[WIND DEBUG] Failed to log station details: " + e.getMessage());
                }
                debugLogged = true;
            }
            
            // Margen más amplio para considerar estaciones visibles
            if (x < -margin || x > w + margin || y < -margin || y > h + margin) {
                outOfBounds++;
                continue;
            }
            
            // Con la nueva lógica, las partículas se generan en los bordes y viajan
            // por todo el mapa, así que necesitamos considerar visibles a todas las
            // estaciones que estén razonablemente cerca del viewport
            cachedVisibleStations.add(station);
            markedVisible++;
            
            // CRÍTICO: Asegurar que esta estación tiene datos de viento
            // Si no tiene datos específicos, usar el viento global como fallback
            if (!stationWindData.containsKey(station.getId())) {
                stationWindData.put(station.getId(), new WindData(windSpeed, windDeg));
            }
        }
        
        lastVisibleStationsUpdate = now;
        
        // Reset round-robin si cambió la lista
        if (nextStationIndex >= cachedVisibleStations.size()) {
            nextStationIndex = 0;
        }
        
        // Log cambios significativos
        if (Math.abs(markedVisible - previousVisibleCount) > 5 || markedVisible == 0) {
            LOGGER.info(String.format("[WIND] Visible stations update: %d visible (was %d), checked %d total, %d out of bounds, %d null positions, zoom=%d",
                markedVisible, previousVisibleCount, totalChecked, outOfBounds, nullPositions, mapViewer.getZoom()));
            
            // Log primeras 5 estaciones visibles para debug
            if (markedVisible > 0 && markedVisible <= 5) {
                for (WeatherStation s : cachedVisibleStations) {
                    Point2D pos = getScreenPosition(s);
                    LOGGER.info(String.format("  Station %s at screen pos (%.1f, %.1f) viewport: %dx%d",
                        s.getId(), pos != null ? pos.getX() : -1, pos != null ? pos.getY() : -1, w, h));
                }
            }
        }
        
        // CRÍTICO: Limpiar pools de estaciones que ya no son visibles
        java.util.Set<String> visibleStationIds = new java.util.HashSet<>();
        for (WeatherStation s : cachedVisibleStations) {
            visibleStationIds.add(s.getId());
        }
        
        // Solo limpiar pools, las partículas se limpian en step() cada 10 frames
        int poolsRemoved = 0;
        java.util.Iterator<String> poolIterator = stationPools.keySet().iterator();
        while (poolIterator.hasNext()) {
            String poolId = poolIterator.next();
            if (!visibleStationIds.contains(poolId)) {
                poolIterator.remove();
                poolsRemoved++;
            }
        }
        
        if (poolsRemoved > 0) {
            LOGGER.fine(String.format("[WIND] Removed %d pools for invisible stations", poolsRemoved));
        }
    }
    
    /**
     * Genera un punto de inicio para una partícula con distribución uniforme.
     * Estrategia híbrida:
     * - 50% en bordes del mapa (para flujo continuo)
     * - 50% distribuidas uniformemente en todo el mapa
     * Retorna null si no hay estaciones visibles.
     */
    private Point2D.Double generateParticleStartAtEdge() {
        updateVisibleStations();
        
        if (cachedVisibleStations.isEmpty()) {
            if (paintCallCount % 30 == 0) {
                LOGGER.warning(String.format("[WIND] No visible stations for particle generation! Total stations: %d, Viewport: %dx%d",
                    stations.size(), getWidth(), getHeight()));
            }
            return null;
        }
        
        int w = getWidth();
        int h = getHeight();
        
        if (w <= 0 || h <= 0) {
            return null;
        }
        
        double x, y;
        
        // 50% de las partículas se generan en los bordes, 50% en todo el mapa
        if (rnd.nextBoolean()) {
            // Generar en los bordes (entrada de flujo)
            int edge = rnd.nextInt(4); // 0=arriba, 1=derecha, 2=abajo, 3=izquierda
            
            switch (edge) {
                case 0: // Borde superior
                    x = rnd.nextDouble() * w;
                    y = 0;
                    break;
                case 1: // Borde derecho
                    x = w;
                    y = rnd.nextDouble() * h;
                    break;
                case 2: // Borde inferior
                    x = rnd.nextDouble() * w;
                    y = h;
                    break;
                default: // Borde izquierdo
                    x = 0;
                    y = rnd.nextDouble() * h;
                    break;
            }
        } else {
            // Generar en posición aleatoria uniforme en todo el viewport
            // Usar grid para mejor distribución
            int gridSize = 20; // Dividir el mapa en grid de 20x20
            int gridX = rnd.nextInt(gridSize);
            int gridY = rnd.nextInt(gridSize);
            
            // Posición dentro de la celda del grid
            double cellWidth = (double) w / gridSize;
            double cellHeight = (double) h / gridSize;
            
            x = gridX * cellWidth + rnd.nextDouble() * cellWidth;
            y = gridY * cellHeight + rnd.nextDouble() * cellHeight;
        }
        
        return new Point2D.Double(x, y);
    }
    
    /**
     * Encuentra la estación más cercana a un punto en pantalla.
     * Retorna null si no hay estaciones visibles.
     */
    private WeatherStation getClosestStation(double screenX, double screenY) {
        updateVisibleStations();
        
        if (cachedVisibleStations.isEmpty()) {
            return null;
        }
        
        WeatherStation closest = null;
        double minDistanceSquared = Double.MAX_VALUE;
        
        for (WeatherStation station : cachedVisibleStations) {
            Point2D screenPos = getScreenPosition(station);
            if (screenPos == null) continue;
            
            double dx = screenX - screenPos.getX();
            double dy = screenY - screenPos.getY();
            double distanceSquared = dx * dx + dy * dy;
            
            if (distanceSquared < minDistanceSquared) {
                minDistanceSquared = distanceSquared;
                closest = station;
            }
        }
        
        return closest;
    }

    /**
     * Actualiza partículas cada frame.
     * Cada estación tiene su propio pool y máximo de partículas.
     */
    private void step(double deltaSeconds) {
        int w = getWidth();
        int h = getHeight();
        
        if (w <= 0 || h <= 0) return;
        
        // Actualizar spatial grid cada ciertos frames
        if (paintCallCount % 10 == 0 && spatialGrid != null) {
            spatialGrid.update(stations, screenPosCache);
        }
        
        // CRÍTICO: Actualizar estaciones visibles ANTES de generar partículas
        updateVisibleStations();
        
        int particlesAddedThisFrame = 0;
        int particlesRemovedThisFrame = 0;
        int particlesUpdatedThisFrame = 0;
        
        // Log temporal para debug
        if (paintCallCount % 30 == 0 && !cachedVisibleStations.isEmpty()) {
            LOGGER.info(String.format("[WIND STEP] Frame:%d, VisibleStations:%d, ActivePools:%d, TotalParticles:%d",
                paintCallCount, cachedVisibleStations.size(), stationPools.size(), particles.size()));
        }
        
        // Distribución uniforme: Calcular partículas objetivo por estación
        int targetParticlesTotal = Math.min(
            maxParticlesPerStation * cachedVisibleStations.size(),
            500  // Límite absoluto para performance
        );
        
        // Agregar partículas distribuyendo equitativamente entre estaciones
        int currentTotal = particles.size();
        int toAdd = Math.min(8, targetParticlesTotal - currentTotal); // Hasta 8 partículas por frame
        
        // Contar partículas actuales por estación para balanceo
        java.util.Map<String, Integer> particleCountByStation = new java.util.HashMap<>();
        for (WindParticle p : particles) {
            String sid = p.getStationId();
            if (sid != null) {
                particleCountByStation.put(sid, particleCountByStation.getOrDefault(sid, 0) + 1);
            }
        }
        
        // Identificar estaciones con menos partículas (para priorizar)
        List<WeatherStation> underservedStations = new ArrayList<>();
        int avgParticlesPerStation = cachedVisibleStations.isEmpty() ? 0 : 
            currentTotal / cachedVisibleStations.size();
        
        for (WeatherStation station : cachedVisibleStations) {
            int count = particleCountByStation.getOrDefault(station.getId(), 0);
            if (count < avgParticlesPerStation || count < 5) {
                underservedStations.add(station);
            }
        }
        
        for (int i = 0; i < toAdd; i++) {
            // Generar posición distribuyendo uniformemente
            Point2D.Double start = generateParticleStartAtEdge();
            if (start == null) {
                continue;
            }
            
            // Priorizar estaciones con menos partículas
            WeatherStation targetStation;
            if (!underservedStations.isEmpty() && rnd.nextDouble() < 0.7) {
                // 70% de probabilidad de usar una estación con pocas partículas
                targetStation = underservedStations.get(rnd.nextInt(underservedStations.size()));
            } else {
                // 30% usar la estación más cercana normalmente
                targetStation = getClosestStation(start.x, start.y);
            }
            
            if (targetStation == null) {
                continue;
            }
            
            String stationId = targetStation.getId();
            
            // Obtener o crear pool para esta estación
            StationParticlePool pool = stationPools.computeIfAbsent(stationId, 
                id -> new StationParticlePool(id, maxParticlesPerStation));
            
            // Obtener datos de viento de la estación más cercana
            double direction = windDeg;
            double velocity = windSpeed;
            
            WindData windData = stationWindData.get(stationId);
            if (windData != null) {
                direction = windData.directionDeg;
                velocity = windData.speedMs;
            }
            
            ParticleStyle style = WeatherFlyweightFactory.getStyleForWind(velocity, direction);
            
            // Calcular velocidad usando velocity cache basada en la dirección de la estación
            double speed = w * (speedScale / 100.0);
            double[] baseVel = velocityCache.getVelocity(direction, speed);
            double vx = baseVel[0] + (rnd.nextDouble() - 0.5) * 2.0;
            double vy = baseVel[1] + (rnd.nextDouble() - 0.5) * 2.0;
            
            // Adquirir partícula del pool de esta estación
            WindParticle p = pool.acquire(start, vx, vy, style);
            if (p != null) {
                particles.add(p);
                particlesAddedThisFrame++;
            }
        }
        
        // Actualizar y limpiar partículas existentes
        List<WindParticle> toRemove = new ArrayList<>();
        
        for (WindParticle p : particles) {
            Point2D.Double pos = p.getPos();
            
            // Nueva lógica: Siempre actualizar la dirección basándose en la estación más cercana
            WeatherStation closestStation = getClosestStation(pos.x, pos.y);
            if (closestStation != null) {
                WindData windData = stationWindData.get(closestStation.getId());
                if (windData != null) {
                    // Obtener la dirección actual de la partícula
                    double currentVx = p.getVx();
                    double currentVy = p.getVy();
                    double currentSpeed = Math.sqrt(currentVx * currentVx + currentVy * currentVy);
                    
                    // Calcular la nueva dirección basada en la estación más cercana
                    double speed = w * (speedScale / 100.0);
                    double[] baseVel = velocityCache.getVelocity(windData.directionDeg, speed);
                    double targetVx = baseVel[0];
                    double targetVy = baseVel[1];
                    
                    // Interpolar suavemente hacia la nueva dirección (transición suave)
                    double interpolationFactor = 0.1; // 10% de cambio por frame (ajustar para más/menos suavidad)
                    double newVx = currentVx + (targetVx - currentVx) * interpolationFactor;
                    double newVy = currentVy + (targetVy - currentVy) * interpolationFactor;
                    
                    // Añadir variación aleatoria pequeña
                    newVx += (rnd.nextDouble() - 0.5) * 1.0;
                    newVy += (rnd.nextDouble() - 0.5) * 1.0;
                    
                    p.setVelocity(newVx, newVy);
                    
                    // Actualizar el stationId si cambió la estación más cercana
                    if (!closestStation.getId().equals(p.getStationId())) {
                        p.setStationId(closestStation.getId());
                    }
                }
            }
            
            // Actualizar posición
            p.update(deltaSeconds);
            particlesUpdatedThisFrame++;
            
            // Marcar para eliminación si salió del viewport (con margen)
            boolean farFromViewport = pos.x < -w*0.5 || pos.x > w*1.5 || pos.y < -h*0.5 || pos.y > h*1.5;
            
            if (farFromViewport) {
                toRemove.add(p);
            }
        }
        
        // Remover partículas fuera de influencia y devolverlas a su pool
        for (WindParticle p : toRemove) {
            particles.remove(p);
            String stationId = p.getStationId();
            if (stationId != null) {
                StationParticlePool pool = stationPools.get(stationId);
                if (pool != null) {
                    pool.release(p);
                }
            }
            particlesRemovedThisFrame++;
        }
        
        // NUEVO: Limpiar partículas huérfanas o de estaciones no visibles
        if (paintCallCount % 10 == 0) { // Cada 10 frames
            java.util.Set<String> visibleStationIds = new java.util.HashSet<>();
            for (WeatherStation s : cachedVisibleStations) {
                visibleStationIds.add(s.getId());
            }
            
            List<WindParticle> orphanParticles = new ArrayList<>();
            for (WindParticle p : particles) {
                String stationId = p.getStationId();
                // Eliminar si: no tiene stationId O la estación ya no es visible
                if (stationId == null || !visibleStationIds.contains(stationId)) {
                    orphanParticles.add(p);
                }
            }
            
            if (!orphanParticles.isEmpty()) {
                for (WindParticle p : orphanParticles) {
                    particles.remove(p);
                    String stationId = p.getStationId();
                    if (stationId != null) {
                        StationParticlePool pool = stationPools.get(stationId);
                        if (pool != null) {
                            pool.release(p);
                        }
                    }
                }
                LOGGER.info(String.format("[WIND] Cleaned %d orphan/invisible particles", orphanParticles.size()));
            }
        }
        
        // Limpieza periódica agresiva cada 5 segundos para evitar acumulación
        if (paintCallCount % 300 == 0) {
            int cleanedUp = 0;
            List<WindParticle> staleParticles = new ArrayList<>();
            for (WindParticle p : particles) {
                Point2D.Double pos = p.getPos();
                if (pos.x < -w*2 || pos.x > w*3 || pos.y < -h*2 || pos.y > h*3) {
                    staleParticles.add(p);
                }
            }
            for (WindParticle p : staleParticles) {
                particles.remove(p);
                String stationId = p.getStationId();
                if (stationId != null) {
                    StationParticlePool pool = stationPools.get(stationId);
                    if (pool != null) {
                        pool.release(p);
                    }
                }
                cleanedUp++;
            }
            if (cleanedUp > 0) {
                LOGGER.fine(String.format("[WIND] Limpieza periódica: %d partículas perdidas removidas", cleanedUp));
            }
        }
        
        // Registrar metrics
        performanceMetrics.recordParticleUpdate(particlesUpdatedThisFrame);
        performanceMetrics.recordParticleAddition(particlesAddedThisFrame);
        performanceMetrics.recordParticleRemoval(particlesRemovedThisFrame);
        performanceMetrics.recordPeakParticleCount(particles.size());
    }

    /**
     * Renderizado con Batch Rendering (BufferedImage).
     * Fallback a renderizado directo si el buffer falla.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        paintCallCount++;
        int w = getWidth();
        int h = getHeight();
        
        if (w <= 0 || h <= 0) return;
        
        Graphics2D targetGraphics = null;
        boolean useDirectRendering = false;
        
        // Intentar crear o recrear buffer si es necesario
        if (renderBuffer == null || renderBuffer.getWidth() != w || renderBuffer.getHeight() != h) {
            // Limpiar buffer anterior
            if (bufferGraphics != null) {
                try {
                    bufferGraphics.dispose();
                } catch (Exception e) {
                    // Ignorar errores de dispose
                }
                bufferGraphics = null;
            }
            
            // Intentar crear nuevo buffer
            try {
                renderBuffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                bufferGraphics = renderBuffer.createGraphics();
                bufferGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            } catch (Exception e) {
                // Si falla la creación del buffer, usar renderizado directo
                LOGGER.fine("[WIND] No se pudo crear buffer (" + w + "x" + h + "), usando renderizado directo");
                renderBuffer = null;
                bufferGraphics = null;
                useDirectRendering = true;
            }
        }
        
        // Decidir dónde renderizar
        if (useDirectRendering || bufferGraphics == null) {
            // Renderizado directo a Graphics
            targetGraphics = (Graphics2D) g.create();
            targetGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        } else {
            // Renderizado a buffer
            targetGraphics = bufferGraphics;
            // Limpiar buffer (transparente)
            targetGraphics.setComposite(AlphaComposite.Clear);
            targetGraphics.fillRect(0, 0, w, h);
            targetGraphics.setComposite(AlphaComposite.SrcOver);
        }
        
        // Renderizar partículas
        int renderedCount = 0;
        for (WindParticle p : particles) {
            Point2D.Double pos = p.getPos();
            ParticleStyle s = p.getStyle();
            
            if (s == null) continue;
            
            targetGraphics.setColor(s.getColor());
            
            // Reutilizar stroke del caché
            int strokeIndex = Math.min((int)(s.getStrokeWidth() * 1.5f * sizeScale), strokeCache.length - 1);
            BasicStroke stroke = strokeCache[strokeIndex];
            targetGraphics.setStroke(stroke);
            
            double vx = p.getVx();
            double vy = p.getVy();
            double speed = Math.sqrt(vx * vx + vy * vy);
            
            if (speed > 0.1) {
                // Partículas más cortas: reducido de (12 + s.getStrokeWidth() * 3) a (6 + s.getStrokeWidth() * 1.5)
                double len = (6 + s.getStrokeWidth() * 1.5) * sizeScale;
                double dx = (vx / speed) * len;
                double dy = (vy / speed) * len;
                
                targetGraphics.drawLine((int)pos.x, (int)pos.y, (int)(pos.x + dx), (int)(pos.y + dy));
            } else {
                int dotSize = (int)(4 * sizeScale);
                targetGraphics.fillOval((int)(pos.x - dotSize/2), (int)(pos.y - dotSize/2), dotSize, dotSize);
            }
            renderedCount++;
        }
        
        // Si usamos buffer, dibujar al panel; si no, ya está dibujado directamente
        if (!useDirectRendering && renderBuffer != null) {
            Graphics2D g2 = (Graphics2D) g;
            g2.drawImage(renderBuffer, 0, 0, null);
        } else if (useDirectRendering) {
            targetGraphics.dispose();
        }
        
        // Logs periódicos
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastStatsTime >= STATS_INTERVAL_MS) {
            lastStatsTime = currentTime;
            
            VelocityCache.CacheStatistics cacheStats = velocityCache.getStatistics();
            SpatialGrid.GridStatistics gridStats = spatialGrid != null ? spatialGrid.getStatistics() : null;
            
            performanceMetrics.updateCacheMetrics(cacheStats.hits, cacheStats.misses);
            if (gridStats != null) {
                performanceMetrics.updateGridMetrics(gridStats.updateCount, gridStats.fillRatio);
            }
            
            // Calcular total de partículas activas en todos los pools
            int totalActiveParticles = 0;
            int totalPools = stationPools.size();
            for (StationParticlePool pool : stationPools.values()) {
                totalActiveParticles += pool.getActiveCount();
            }
            
            // Logging con información de memoria
            Runtime runtime = Runtime.getRuntime();
            long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            long totalMemoryMB = runtime.totalMemory() / (1024 * 1024);
            
            LOGGER.info(String.format(
                "[WIND] Frame:%d Particles:%d (across %d pools) Max/pool:%d Rendered:%d VisibleStations:%d/%d | Cache:Screen=%d,Km=%d | Memory:%dMB/%dMB | %s | %s", 
                paintCallCount, totalActiveParticles, totalPools, maxParticlesPerStation, renderedCount,
                cachedVisibleStations.size(), stations.size(),
                screenPosCache.size(), kmToPixelsCache.size(),
                usedMemoryMB, totalMemoryMB,
                cacheStats, gridStats));
        }
    }
}
