package com.javamid.util;

/**
 * Utilidades geográficas para cálculos de distancias y conversiones.
 */
public final class GeoUtils {
    
    private static final double EARTH_RADIUS_KM = 6371.0;
    
    private GeoUtils() {
        // Utility class - no instantiation
    }
    
    /**
     * Calcula la distancia en kilómetros entre dos puntos geográficos 
     * usando la fórmula de Haversine.
     * 
     * @param lat1 Latitud del primer punto en grados
     * @param lon1 Longitud del primer punto en grados
     * @param lat2 Latitud del segundo punto en grados
     * @param lon2 Longitud del segundo punto en grados
     * @return Distancia en kilómetros
     */
    public static double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
    
    /**
     * Genera un ID único para un área geográfica basado en un grid.
     * Útil para evitar regenerar estaciones en áreas ya cubiertas.
     * 
     * @param lat Latitud del punto en grados
     * @param lon Longitud del punto en grados
     * @param gridSizeKm Tamaño del grid en kilómetros
     * @return ID único del grid
     */
    public static String getAreaId(double lat, double lon, double gridSizeKm) {
        // Convertir km a grados aproximadamente (1 grado ≈ 111 km)
        double gridDegrees = gridSizeKm / 111.0;
        
        // Redondear coordenadas al grid más cercano
        long latGrid = Math.round(lat / gridDegrees);
        long lonGrid = Math.round(lon / gridDegrees);
        
        return latGrid + "," + lonGrid;
    }
    
    /**
     * Convierte kilómetros por hora a metros por segundo.
     * 
     * @param kmh Velocidad en km/h
     * @return Velocidad en m/s
     */
    public static double kmhToMs(double kmh) {
        return kmh / 3.6;
    }
}
