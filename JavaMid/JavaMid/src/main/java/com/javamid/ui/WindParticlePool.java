package com.javamid.ui;

import com.javamid.flyweight.ParticleStyle;
import java.awt.geom.Point2D;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Logger;

/**
 * Object Pool para reciclar WindParticle y reducir presión en GC.
 * Mantiene una cola de partículas disponibles para reutilizar.
 */
public class WindParticlePool {
    private static final Logger LOGGER = Logger.getLogger(WindParticlePool.class.getName());
    
    private final Queue<WindParticle> available;
    private final int maxPoolSize;
    private int createdCount = 0;
    private int reusedCount = 0;
    
    public WindParticlePool(int initialSize, int maxSize) {
        this.available = new LinkedList<>();
        this.maxPoolSize = maxSize;
        
        // Pre-allocate initial particles
        for (int i = 0; i < initialSize; i++) {
            available.offer(new WindParticle(new Point2D.Double(0, 0), 0, 0, null, null));
            createdCount++;
        }
        
        LOGGER.info("[POOL] WindParticlePool created with " + initialSize + " initial particles");
    }
    
    /**
     * Obtiene una partícula del pool o crea una nueva si el pool está vacío.
     */
    public WindParticle acquire(Point2D.Double start, double vx, double vy, ParticleStyle style) {
        WindParticle particle = available.poll();
        
        if (particle == null) {
            // Pool vacío, crear nueva partícula
            if (createdCount < maxPoolSize) {
                particle = new WindParticle(start, vx, vy, style, null); // null stationId para pool global
                createdCount++;
            } else {
                LOGGER.warning("[POOL] Pool exhausted (" + maxPoolSize + " particles). Reusing oldest.");
                // En caso extremo, reutilizar de todas formas (no debería suceder)
                particle = new WindParticle(start, vx, vy, style, null);
            }
        } else {
            // Reutilizar partícula del pool
            particle.reset(start, vx, vy, style, null);
            reusedCount++;
        }
        
        return particle;
    }
    
    /**
     * Devuelve una partícula al pool para reutilización.
     */
    public void release(WindParticle particle) {
        if (particle != null && available.size() < maxPoolSize) {
            particle.resetToDefaults();
            available.offer(particle);
        }
    }
    
    /**
     * Devuelve múltiples partículas al pool de una vez.
     */
    public void releaseAll(java.util.Collection<WindParticle> particles) {
        for (WindParticle p : particles) {
            release(p);
        }
    }
    
    /**
     * Obtiene estadísticas del pool.
     */
    public PoolStatistics getStatistics() {
        return new PoolStatistics(createdCount, reusedCount, available.size(), maxPoolSize);
    }
    
    /**
     * Reinicia las estadísticas.
     */
    public void resetStatistics() {
        reusedCount = 0;
    }
    
    /**
     * Clase interna para estadísticas del pool.
     */
    public static class PoolStatistics {
        public final int totalCreated;
        public final int totalReused;
        public final int currentAvailable;
        public final int maxSize;
        
        public PoolStatistics(int totalCreated, int totalReused, int currentAvailable, int maxSize) {
            this.totalCreated = totalCreated;
            this.totalReused = totalReused;
            this.currentAvailable = currentAvailable;
            this.maxSize = maxSize;
        }
        
        @Override
        public String toString() {
            return String.format("[POOL] Created: %d, Reused: %d, Available: %d/%d", 
                totalCreated, totalReused, currentAvailable, maxSize);
        }
    }
}
