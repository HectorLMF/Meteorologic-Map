package com.javamid.config;

/**
 * Configuración centralizada para parámetros del mapa y estaciones meteorológicas.
 */
public final class MapConfig {
    
    private MapConfig() {
        // Utility class - no instantiation
    }
    
    // === CONFIGURACIÓN DE ESTACIONES ===
    
    /** Radio en km para generar estaciones virtuales alrededor de un punto */
    public static final double STATION_GENERATION_RADIUS_KM = 150.0;
    
    /** Número de puntos virtuales a generar por área */
    public static final int VIRTUAL_STATIONS_COUNT = 200;
    
    /** Tamaño del grid en km para evitar regenerar estaciones en áreas ya cubiertas */
    public static final double AREA_GRID_SIZE_KM = 50.0;
    
    /** Distancia mínima en km entre estaciones para evitar duplicados */
    public static final double MIN_STATION_DISTANCE_KM = 1.0;
    
    /** Número máximo de estaciones visibles a cargar datos de viento simultáneamente */
    public static final int MAX_WIND_DATA_STATIONS = 10;
    
    /** Número mínimo de estaciones a usar si no hay estaciones visibles */
    public static final int MIN_FALLBACK_STATIONS = 5;
    
    // === CONFIGURACIÓN DE DATOS METEOROLÓGICOS ===
    
    /** Delay en días para datos históricos (Open-Meteo tiene un retraso) */
    public static final int HISTORICAL_DATA_DELAY_DAYS = 5;
    
    /** Rango de días históricos a consultar */
    public static final int HISTORICAL_DATA_RANGE_DAYS = 7;
    
    /** Timeout en segundos para cargar datos de viento de múltiples estaciones */
    public static final int WIND_DATA_TIMEOUT_SECONDS = 30;
    
    /** Número de threads para el pool de carga de datos meteorológicos */
    public static final int WEATHER_DATA_THREAD_POOL_SIZE = 2;
    
    // === CONFIGURACIÓN DE INTERFAZ ===
    
    /** Delay en ms para debounce de movimientos del mapa (evitar spam a APIs) */
    public static final int MAP_MOVE_DEBOUNCE_MS = 350;
    
    /** Tamaño mínimo de ventana (ancho) */
    public static final int MIN_WINDOW_WIDTH = 800;
    
    /** Tamaño mínimo de ventana (alto) */
    public static final int MIN_WINDOW_HEIGHT = 600;
    
    /** Tamaño inicial de ventana (ancho) */
    public static final int DEFAULT_WINDOW_WIDTH = 1200;
    
    /** Tamaño inicial de ventana (alto) */
    public static final int DEFAULT_WINDOW_HEIGHT = 800;
    
    /** Tamaño del panel de información lateral */
    public static final int INFO_PANEL_WIDTH = 350;
    
    // === CONFIGURACIÓN DE MAPA ===
    
    /** Zoom inicial del mapa */
    public static final int DEFAULT_MAP_ZOOM = 5;

    /**
     * Zoom mínimo para mostrar overlays de temperatura/humedad.
     * A zooms más bajos (más alejados), las capas no se renderizan
     * para evitar ruido visual cuando las estaciones están agrupadas.
     */
    public static final int MIN_OVERLAY_ZOOM = 4;
    
    /** Latitud inicial (Madrid, España) */
    public static final double DEFAULT_LATITUDE = 40.4168;
    
    /** Longitud inicial (Madrid, España) */
    public static final double DEFAULT_LONGITUDE = -3.7038;
    
    /** Número de threads para cargar tiles del mapa */
    public static final int MAP_TILE_THREAD_POOL_SIZE = 4;
    
    /** User-Agent para peticiones HTTP a OpenStreetMap */
    public static final String OSM_USER_AGENT = "JavaMidWeatherMap/1.0 (educational; contact: you@example.com)";
    
    // === CONFIGURACIÓN DE PARTÍCULAS DE VIENTO ===
    
    /** Velocidad de viento por defecto en m/s */
    public static final double DEFAULT_WIND_SPEED_MS = 5.0;
    
    /** Dirección de viento por defecto en grados (90° = Este) */
    public static final double DEFAULT_WIND_DIRECTION_DEG = 90.0;
    
    /** Radio de influencia inicial en km */
    public static final double DEFAULT_INFLUENCE_RADIUS_KM = 5.0;
    
    /** Mínimo radio de influencia en km */
    public static final double MIN_INFLUENCE_RADIUS_KM = 5.0;
    
    /** Máximo radio de influencia en km */
    public static final double MAX_INFLUENCE_RADIUS_KM = 40.0;
    
    /** Número inicial de partículas por estación */
    public static final int DEFAULT_PARTICLES_PER_STATION = 120;
    
    /** Mínimo número de partículas por estación */
    public static final int MIN_PARTICLES_PER_STATION = 10;
    
    /** Máximo número de partículas por estación */
    public static final int MAX_PARTICLES_PER_STATION = 5000;

    /**
     * Velocidad de viento (m/s) a la que se considera "viento fuerte"
     * y se asigna el máximo de partículas por estación.
     */
    public static final double WIND_SPEED_MAX_FOR_FULL_PARTICLES = 15.0;

    /**
     * Fracción mínima de partículas por estación incluso con viento bajo.
     * Evita que la animación desaparezca totalmente a velocidades pequeñas.
     */
    public static final double MIN_PARTICLE_FRACTION = 0.3; // 30% del máximo
}
