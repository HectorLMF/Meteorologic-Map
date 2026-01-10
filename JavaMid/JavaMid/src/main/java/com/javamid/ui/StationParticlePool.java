package com.javamid.ui;

import com.javamid.flyweight.ParticleStyle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Pool de partículas específico para una estación meteorológica.
 * Cada estación tiene su propio pool y su propio límite de partículas.
 */
public class StationParticlePool {
    private final String stationId;
    private int maxParticles;
    private final List<WindParticle> pool;
    private int activeCount = 0;
    private int totalAcquired = 0;
    private int totalReleased = 0;

    public StationParticlePool(String stationId, int maxParticles) {
        this.stationId = stationId;
        this.maxParticles = maxParticles;
        this.pool = new ArrayList<>(maxParticles);
    }

    /**
     * Adquiere una partícula del pool si no se ha alcanzado el máximo.
     * Retorna null si se alcanzó el límite de partículas para esta estación.
     */
    public WindParticle acquire(Point2D.Double start, double vx, double vy, ParticleStyle style) {
        if (activeCount >= maxParticles) {
            return null; // Esta estación ya alcanzó su límite
        }

        WindParticle particle;
        
        if (!pool.isEmpty()) {
            // Reutilizar partícula del pool
            particle = pool.remove(pool.size() - 1);
            particle.reset(start, vx, vy, style, stationId);
        } else {
            // Crear nueva partícula
            particle = new WindParticle(start, vx, vy, style, stationId);
        }
        
        activeCount++;
        totalAcquired++;
        return particle;
    }

    /**
     * Devuelve una partícula al pool.
     */
    public void release(WindParticle particle) {
        if (particle == null) return;
        
        particle.resetToDefaults();
        particle.setStationId(stationId); // Mantener asociación con estación
        
        if (pool.size() < maxParticles) {
            pool.add(particle);
        }
        
        activeCount--;
        totalReleased++;
    }

    public int getActiveCount() {
        return activeCount;
    }

    public int getMaxParticles() {
        return maxParticles;
    }

    /**
     * Actualiza el máximo de partículas permitido para esta estación.
     * Si el nuevo máximo es menor que el número activo, las partículas
     * se irán liberando progresivamente en los siguientes frames.
     */
    public void setMaxParticles(int newMax) {
        if (newMax < 1) newMax = 1;
        this.maxParticles = newMax;
    }

    public String getStationId() {
        return stationId;
    }

    public PoolStatistics getStatistics() {
        return new PoolStatistics(stationId, activeCount, maxParticles, pool.size(), totalAcquired, totalReleased);
    }

    /**
     * Estadísticas del pool de una estación.
     */
    public static class PoolStatistics {
        public final String stationId;
        public final int activeCount;
        public final int maxParticles;
        public final int pooledCount;
        public final int totalAcquired;
        public final int totalReleased;

        public PoolStatistics(String stationId, int activeCount, int maxParticles, int pooledCount, int totalAcquired, int totalReleased) {
            this.stationId = stationId;
            this.activeCount = activeCount;
            this.maxParticles = maxParticles;
            this.pooledCount = pooledCount;
            this.totalAcquired = totalAcquired;
            this.totalReleased = totalReleased;
        }

        @Override
        public String toString() {
            return String.format("[Pool:%s] Active:%d/%d Pooled:%d Acquired:%d Released:%d", 
                stationId, activeCount, maxParticles, pooledCount, totalAcquired, totalReleased);
        }
    }
}
