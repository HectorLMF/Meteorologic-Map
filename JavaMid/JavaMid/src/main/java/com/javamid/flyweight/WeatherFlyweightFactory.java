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
        // Colores base por velocidad
        Color baseColor;
        float baseStroke;
        
        switch (speedBucket) {
            case 0: 
                baseColor = new Color(0x80,0xCC,0xFF);
                baseStroke = 0.8f;
                break;
            case 1: 
                baseColor = new Color(0x66,0xB2,0xFF);
                baseStroke = 1.2f;
                break;
            case 2: 
                baseColor = new Color(0x33,0x99,0xFF);
                baseStroke = 1.6f;
                break;
            case 3: 
                baseColor = new Color(0x00,0x66,0xCC);
                baseStroke = 2.2f;
                break;
            case 4: 
                baseColor = new Color(0x00,0x44,0x99);
                baseStroke = 3.0f;
                break;
            default: 
                baseColor = new Color(0x00,0x22,0x66);
                baseStroke = 4.0f;
                break;
        }
        
        // Variación sutil de color por dirección para mejor visualización
        int r = baseColor.getRed();
        int g = baseColor.getGreen();
        int b = baseColor.getBlue();
        
        // Ajustar ligeramente el color según la dirección
        int variation = directionBucket * 5;
        r = Math.min(255, Math.max(0, r + variation));
        g = Math.min(255, Math.max(0, g - variation / 2));
        
        return new ParticleStyle(new Color(r, g, b), baseStroke);
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
