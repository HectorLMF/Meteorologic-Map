package com.javamid.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Cachea velocidades precalculadas (vx, vy) para evitar recalcular
 * trigonometría costosa (toRadians, cos, sin) múltiples veces.
 */
public class VelocityCache {
    private static final Logger LOGGER = Logger.getLogger(VelocityCache.class.getName());
    
    private final Map<String, double[]> cache = new HashMap<>();
    private int hitCount = 0;
    private int missCount = 0;
    
    // Buckets para granularidad razonable sin explotar el caché
    private static final int DIRECTION_BUCKETS = 16;  // Cada 22.5 grados
    private static final int SPEED_BUCKETS = 6;
    
    /**
     * Obtiene la velocidad (vx, vy) cacheada para una dirección y velocidad.
     * Si no está en caché, la calcula y la almacena.
     * 
     * @param direction Dirección en grados (0-360)
     * @param speed Velocidad en píxeles/segundo
     * @return Array [vx, vy]
     */
    public double[] getVelocity(double direction, double speed) {
        int dirBucket = bucketDirection(direction);
        int speedBucket = bucketSpeed(speed);
        String key = dirBucket + "_" + speedBucket;
        
        double[] velocity = cache.get(key);
        if (velocity != null) {
            hitCount++;
            return velocity;
        }
        
        // Cache miss: calcular y almacenar
        missCount++;
        velocity = computeVelocity(direction, speed);
        cache.put(key, velocity);
        return velocity;
    }
    
    /**
     * Computa la velocidad (vx, vy) a partir de dirección y magnitud.
     * En meteorología: 0° = Norte, 90° = Este, 180° = Sur, 270° = Oeste
     * En matemáticas: 0° = Este, 90° = Norte, 180° = Oeste, 270° = Sur
     * Conversión: matemáticas = 90° - meteorología
     */
    private double[] computeVelocity(double direction, double speed) {
        double rad = Math.toRadians(90 - direction);  // Convertir de grados meteorológicos a radianes matemáticos
        double vx = Math.cos(rad) * speed;
        double vy = -Math.sin(rad) * speed;  // Negativo porque en pantalla Y crece hacia abajo
        return new double[]{vx, vy};
    }
    
    /**
     * Agrupa direcciones en buckets de 22.5 grados cada uno.
     */
    private int bucketDirection(double degrees) {
        int normalized = (int) ((degrees % 360 + 360) % 360);
        return (normalized + 11) / 23;  // Cada bucket cubre ~22.5 grados
    }
    
    /**
     * Agrupa velocidades en 6 buckets logarítmicos.
     */
    private int bucketSpeed(double speed) {
        if (speed <= 0.5) return 0;
        if (speed <= 1.0) return 1;
        if (speed <= 2.0) return 2;
        if (speed <= 5.0) return 3;
        if (speed <= 10.0) return 4;
        return 5;
    }
    
    /**
     * Obtiene estadísticas de hits/misses del caché.
     */
    public CacheStatistics getStatistics() {
        return new CacheStatistics(hitCount, missCount, cache.size());
    }
    
    /**
     * Reinicia las estadísticas.
     */
    public void resetStatistics() {
        hitCount = 0;
        missCount = 0;
    }
    
    /**
     * Limpia el caché completamente.
     */
    public void clear() {
        cache.clear();
        resetStatistics();
        LOGGER.info("[CACHE] VelocityCache cleared");
    }
    
    /**
     * Estadísticas del caché.
     */
    public static class CacheStatistics {
        public final int hits;
        public final int misses;
        public final int cacheSize;
        public final double hitRate;
        
        public CacheStatistics(int hits, int misses, int cacheSize) {
            this.hits = hits;
            this.misses = misses;
            this.cacheSize = cacheSize;
            this.hitRate = (hits + misses) > 0 ? (double) hits / (hits + misses) : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("[CACHE] Hits: %d, Misses: %d, HitRate: %.1f%%, Size: %d", 
                hits, misses, hitRate * 100, cacheSize);
        }
    }
}
