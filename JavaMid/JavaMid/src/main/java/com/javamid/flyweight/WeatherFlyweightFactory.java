package com.javamid.flyweight;

import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flyweight factory que retorna instancias compartidas de ParticleStyle.
 * Optimizado para agrupar por velocidad Y dirección del viento.
 */
public class WeatherFlyweightFactory {

    private static final Map<String, ParticleStyle> cache = new ConcurrentHashMap<>();
    
    // 8 direcciones cardinales para agrupar
    private static final int DIRECTION_BUCKETS = 8;

    /**
     * Retorna un ParticleStyle compartido basado en velocidad y dirección del viento.
     * @param speedMetersPerSecond Velocidad en m/s
     * @param directionDegrees Dirección en grados (0-360)
     */
    public static ParticleStyle getStyleForWind(double speedMetersPerSecond, double directionDegrees) {
        int speedBucket = bucketForSpeed(speedMetersPerSecond);
        int dirBucket = bucketForDirection(directionDegrees);
        String key = speedBucket + "_" + dirBucket;
        return cache.computeIfAbsent(key, k -> createStyle(speedBucket, dirBucket));
    }
    
    /**
     * Version simplificada para compatibilidad (solo velocidad)
     */
    public static ParticleStyle getStyleForWind(double speedMetersPerSecond) {
        return getStyleForWind(speedMetersPerSecond, 0);
    }

    private static int bucketForSpeed(double speed) {
        if (speed <= 0.5) return 0;
        if (speed <= 2) return 1;
        if (speed <= 5) return 2;
        if (speed <= 8) return 3;
        if (speed <= 12) return 4;
        return 5;
    }
    
    private static int bucketForDirection(double degrees) {
        // Agrupar en 8 direcciones: N, NE, E, SE, S, SW, W, NW
        // Cada bucket representa 45 grados
        int normalized = (int) ((degrees % 360 + 360) % 360); // Normalizar a 0-360
        return (normalized + 22) / 45; // +22 para centrar en las direcciones cardinales
    }

    private static ParticleStyle createStyle(int speedBucket, int directionBucket) {
        // Todas las partículas tienen cuerpo blanco y borde negro
        Color fillColor = Color.WHITE;
        Color strokeColor = Color.BLACK;
        float strokeWidth;
        
        // Grosor del borde según velocidad
        switch (speedBucket) {
            case 0: 
                strokeWidth = 1.0f;
                break;
            case 1: 
                strokeWidth = 1.3f;
                break;
            case 2: 
                strokeWidth = 1.7f;
                break;
            case 3: 
                strokeWidth = 2.2f;
                break;
            case 4: 
                strokeWidth = 2.8f;
                break;
            default: 
                strokeWidth = 3.5f;
                break;
        }
        
        return new ParticleStyle(fillColor, strokeColor, strokeWidth);
    }
    
    /**
     * Retorna el número de estilos únicos en caché (para monitoreo)
     */
    public static int getCacheSize() {
        return cache.size();
    }
    
    /**
     * Limpia la caché (útil si se necesita liberar memoria)
     */
    public static void clearCache() {
        cache.clear();
    }
}
