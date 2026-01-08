package com.javamid.ui;

import java.util.logging.Logger;

/**
 * Recopila métricas de rendimiento del sistema de partículas.
 * Permite monitorear mejoras reales de optimizaciones.
 */
public class PerformanceMetrics {
    private static final Logger LOGGER = Logger.getLogger(PerformanceMetrics.class.getName());
    
    // Frame timing
    private long lastFrameTime;
    private double averageFrameTime = 0.0;
    private double maxFrameTime = 0.0;
    private long frameCount = 0;
    private static final int FRAME_SAMPLE_SIZE = 60;  // Promediar 60 frames
    private final double[] frameTimeSamples = new double[FRAME_SAMPLE_SIZE];
    private int frameSampleIndex = 0;
    
    // Particle metrics
    private int particlesUpdated = 0;
    private int particlesRemoved = 0;
    private int particlesAdded = 0;
    private int peakParticleCount = 0;
    
    // Memory metrics
    private long gcCount = 0;
    private long totalGcTime = 0;
    private long lastGcTime = 0;
    
    // Cache metrics
    private int cacheHits = 0;
    private int cacheMisses = 0;
    
    // Spatial grid metrics
    private int gridUpdates = 0;
    private double gridFillRatio = 0.0;
    
    private final long startTime = System.currentTimeMillis();
    
    /**
     * Registra el inicio de un frame.
     */
    public void frameStart() {
        lastFrameTime = System.nanoTime();
    }
    
    /**
     * Registra el fin de un frame y actualiza estadísticas.
     */
    public void frameEnd() {
        long endTime = System.nanoTime();
        double frameDelta = (endTime - lastFrameTime) / 1_000_000.0;  // Convertir a ms
        
        // Actualizar máximo
        maxFrameTime = Math.max(maxFrameTime, frameDelta);
        
        // Agregar a muestras para promedio móvil
        frameTimeSamples[frameSampleIndex] = frameDelta;
        frameSampleIndex = (frameSampleIndex + 1) % FRAME_SAMPLE_SIZE;
        
        // Recalcular promedio
        double sum = 0;
        for (double time : frameTimeSamples) {
            sum += time;
        }
        averageFrameTime = sum / FRAME_SAMPLE_SIZE;
        
        frameCount++;
    }
    
    /**
     * Registra partículas actualizadas en un frame.
     */
    public void recordParticleUpdate(int count) {
        particlesUpdated += count;
    }
    
    /**
     * Registra partículas removidas en un frame.
     */
    public void recordParticleRemoval(int count) {
        particlesRemoved += count;
    }
    
    /**
     * Registra partículas añadidas en un frame.
     */
    public void recordParticleAddition(int count) {
        particlesAdded += count;
    }
    
    /**
     * Registra el conteo pico de partículas.
     */
    public void recordPeakParticleCount(int count) {
        peakParticleCount = Math.max(peakParticleCount, count);
    }
    
    /**
     * Registra una recopilación de basura.
     */
    public void recordGarbageCollection(long duration) {
        gcCount++;
        totalGcTime += duration;
    }
    
    /**
     * Actualiza métricas de caché.
     */
    public void updateCacheMetrics(int hits, int misses) {
        cacheHits = hits;
        cacheMisses = misses;
    }
    
    /**
     * Actualiza métricas de grid.
     */
    public void updateGridMetrics(int updates, double fillRatio) {
        gridUpdates = updates;
        gridFillRatio = fillRatio;
    }
    
    /**
     * Obtiene un reporte completo de métricas.
     */
    public PerformanceReport getReport() {
        return new PerformanceReport(this);
    }
    
    /**
     * Reinicia todas las métricas.
     */
    public void reset() {
        particlesUpdated = 0;
        particlesRemoved = 0;
        particlesAdded = 0;
        cacheHits = 0;
        cacheMisses = 0;
        maxFrameTime = 0.0;
    }
    
    /**
     * Reporte de rendimiento.
     */
    public class PerformanceReport {
        public final long elapsedTime;
        public final long frameCount;
        public final double averageFrameTime;
        public final double maxFrameTime;
        public final double fps;
        public final int particlesUpdated;
        public final int particlesRemoved;
        public final int particlesAdded;
        public final int peakParticleCount;
        public final long gcCount;
        public final long totalGcTime;
        public final double cacheHitRate;
        public final int gridUpdates;
        public final double gridFillRatio;
        
        private PerformanceReport(PerformanceMetrics metrics) {
            this.elapsedTime = System.currentTimeMillis() - metrics.startTime;
            this.frameCount = metrics.frameCount;
            this.averageFrameTime = metrics.averageFrameTime;
            this.maxFrameTime = metrics.maxFrameTime;
            this.fps = averageFrameTime > 0 ? 1000.0 / averageFrameTime : 0;
            this.particlesUpdated = metrics.particlesUpdated;
            this.particlesRemoved = metrics.particlesRemoved;
            this.particlesAdded = metrics.particlesAdded;
            this.peakParticleCount = metrics.peakParticleCount;
            this.gcCount = metrics.gcCount;
            this.totalGcTime = metrics.totalGcTime;
            this.cacheHitRate = (metrics.cacheHits + metrics.cacheMisses) > 0 
                ? (double) metrics.cacheHits / (metrics.cacheHits + metrics.cacheMisses) 
                : 0;
            this.gridUpdates = metrics.gridUpdates;
            this.gridFillRatio = metrics.gridFillRatio;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n========== PERFORMANCE REPORT ==========\n");
            sb.append(String.format("Elapsed: %dms | Frames: %d | FPS: %.1f\n", 
                elapsedTime, frameCount, fps));
            sb.append(String.format("Frame Time: avg=%.2fms, max=%.2fms\n", 
                averageFrameTime, maxFrameTime));
            sb.append(String.format("Particles: updated=%d, added=%d, removed=%d, peak=%d\n", 
                particlesUpdated, particlesAdded, particlesRemoved, peakParticleCount));
            sb.append(String.format("GC: count=%d, totalTime=%dms\n", 
                gcCount, totalGcTime));
            sb.append(String.format("Cache: hitRate=%.1f%%\n", 
                cacheHitRate * 100));
            sb.append(String.format("Grid: updates=%d, fillRatio=%.0f%%\n", 
                gridUpdates, gridFillRatio * 100));
            sb.append("==========================================\n");
            return sb.toString();
        }
    }
}
