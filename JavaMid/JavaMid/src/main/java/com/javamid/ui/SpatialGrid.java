package com.javamid.ui;

import com.javamid.model.WeatherStation;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Grid espacial para búsqueda O(1) de estaciones cercanas a una posición.
 * Divide la pantalla en una malla de celdas para localización rápida.
 */
public class SpatialGrid {
    private static final Logger LOGGER = Logger.getLogger(SpatialGrid.class.getName());
    
    private static final int GRID_COLS = 8;
    private static final int GRID_ROWS = 8;
    
    private final Cell[][] grid;
    private final int cellWidth;
    private final int cellHeight;
    private final int screenWidth;
    private final int screenHeight;
    private int updateCount = 0;
    // Últimas posiciones en pantalla usadas para el update (clave: stationId)
    private java.util.Map<String, Point2D> lastScreenPositions = new java.util.HashMap<>();
    
    /**
     * Construye la grid espacial.
     */
    public SpatialGrid(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.cellWidth = Math.max(1, screenWidth / GRID_COLS);
        this.cellHeight = Math.max(1, screenHeight / GRID_ROWS);
        this.grid = new Cell[GRID_ROWS][GRID_COLS];
        
        // Inicializar celdas
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                grid[row][col] = new Cell();
            }
        }
        
        LOGGER.info(String.format("[GRID] SpatialGrid created: %dx%d (%d cells of %dx%d px)", 
            screenWidth, screenHeight, GRID_ROWS * GRID_COLS, cellWidth, cellHeight));
    }
    
    /**
     * Actualiza la grid con nuevas posiciones de estaciones.
     * CORREGIDO: Ahora inserta todas las estaciones, incluso las que están
     * ligeramente fuera del viewport (se asignan a celdas de borde).
     */
    public void update(List<WeatherStation> stations, java.util.Map<String, Point2D> screenPositions) {
        // Guardar referencia de posiciones para consultas posteriores
        if (screenPositions != null) {
            lastScreenPositions.clear();
            lastScreenPositions.putAll(screenPositions);
        } else {
            lastScreenPositions.clear();
        }
        // Limpiar grid anterior
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                grid[row][col].stations.clear();
            }
        }
        
        int insertedCount = 0;
        int skippedCount = 0;
        
        // Insertar estaciones en sus celdas
        for (WeatherStation station : stations) {
            Point2D screenPos = screenPositions.get(station.getId());
            if (screenPos != null) {
                int[] cell = getCellCoordinates(screenPos.getX(), screenPos.getY());
                // getCellCoordinates nunca retorna null ahora, siempre inserta
                grid[cell[0]][cell[1]].stations.add(station);
                insertedCount++;
            } else {
                skippedCount++;
            }
        }
        
        updateCount++;
        
        if (updateCount % 10 == 0) {
            LOGGER.fine(String.format("[GRID] Update #%d: Inserted %d stations, skipped %d (no screen pos)",
                updateCount, insertedCount, skippedCount));
        }
    }
    
    /**
     * Obtiene las estaciones cercanas a una posición dentro de un radio.
     * CORREGIDO: Ya no retorna lista vacía si el punto está fuera del viewport.
     */
    public List<WeatherStation> getStationsNear(double x, double y, double radiusPixels) {
        List<WeatherStation> result = new ArrayList<>();
        
        // Determinar qué celdas cubrir
        int[] minCell = getCellCoordinates(x - radiusPixels, y - radiusPixels);
        int[] maxCell = getCellCoordinates(x + radiusPixels, y + radiusPixels);
        
        // Ya no necesitamos verificar null porque getCellCoordinates siempre retorna valores válidos
        
        double radiusSquared = radiusPixels * radiusPixels;
        
        // Iterar sobre celdas relevantes
        for (int row = minCell[0]; row <= maxCell[0]; row++) {
            for (int col = minCell[1]; col <= maxCell[1]; col++) {
                if (row >= 0 && row < GRID_ROWS && col >= 0 && col < GRID_COLS) {
                    Cell cell = grid[row][col];
                    for (WeatherStation station : cell.stations) {
                        // Usar la posición en pantalla cacheada para el cálculo de distancia
                        Point2D stationScreen = lastScreenPositions.get(station.getId());
                        if (stationScreen == null) continue;
                        double stationX = stationScreen.getX();
                        double stationY = stationScreen.getY();
                        double dx = x - stationX;
                        double dy = y - stationY;
                        double distSquared = dx * dx + dy * dy;

                        if (distSquared <= radiusSquared) {
                            result.add(station);
                        }
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Obtiene la estación más cercana en un radio, o null si no hay ninguna.
     */
    public WeatherStation getClosestStation(double x, double y, double radiusPixels) {
        List<WeatherStation> nearby = getStationsNear(x, y, radiusPixels);
        if (nearby.isEmpty()) {
            return null;
        }
        
        // Encontrar la más cercana
        WeatherStation closest = nearby.get(0);
        double closestDistSq = Double.MAX_VALUE;
        
        for (WeatherStation station : nearby) {
            Point2D stationScreen = lastScreenPositions.get(station.getId());
            if (stationScreen == null) continue;
            double dx = x - stationScreen.getX();
            double dy = y - stationScreen.getY();
            double distSq = dx * dx + dy * dy;

            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = station;
            }
        }
        
        return closest;
    }
    
    /**
     * Obtiene las coordenadas de celda para una posición en pantalla.
     * CORREGIDO: Ya no retorna null para coordenadas fuera del viewport.
     * En su lugar, clampea las coordenadas a las celdas de borde.
     * Esto permite que estaciones cercanas pero fuera del viewport
     * sigan generando partículas que entran al viewport.
     */
    private int[] getCellCoordinates(double screenX, double screenY) {
        // Clampear coordenadas al rango válido en lugar de retornar null
        // Esto permite que estaciones ligeramente fuera del viewport
        // se asignen a celdas de borde
        int col = (int) (screenX / cellWidth);
        int row = (int) (screenY / cellHeight);
        
        // Clampear a rango válido [0, GRID_COLS-1] y [0, GRID_ROWS-1]
        col = Math.max(0, Math.min(col, GRID_COLS - 1));
        row = Math.max(0, Math.min(row, GRID_ROWS - 1));
        
        return new int[]{row, col};
    }
    
    /**
     * Redimensiona la grid cuando la pantalla cambia.
     */
    public void resize(int newWidth, int newHeight) {
        if (newWidth != screenWidth || newHeight != screenHeight) {
            LOGGER.info(String.format("[GRID] Resizing from %dx%d to %dx%d", 
                screenWidth, screenHeight, newWidth, newHeight));
            // Sería necesario reconstruir, pero por ahora dejamos como está
        }
    }
    
    /**
     * Obtiene estadísticas de la grid.
     */
    public GridStatistics getStatistics() {
        int totalStations = 0;
        int filledCells = 0;
        
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                if (!grid[row][col].stations.isEmpty()) {
                    filledCells++;
                    totalStations += grid[row][col].stations.size();
                }
            }
        }
        
        return new GridStatistics(totalStations, filledCells, GRID_ROWS * GRID_COLS, updateCount);
    }
    
    /**
     * Celda interna de la grid.
     */
    private static class Cell {
        List<WeatherStation> stations = new ArrayList<>();
    }
    
    /**
     * Estadísticas de la grid.
     */
    public static class GridStatistics {
        public final int totalStations;
        public final int filledCells;
        public final int totalCells;
        public final int updateCount;
        public final double fillRatio;
        
        public GridStatistics(int totalStations, int filledCells, int totalCells, int updateCount) {
            this.totalStations = totalStations;
            this.filledCells = filledCells;
            this.totalCells = totalCells;
            this.updateCount = updateCount;
            this.fillRatio = (double) filledCells / totalCells;
        }
        
        @Override
        public String toString() {
            return String.format("[GRID] Stations: %d, Filled: %d/%d (%.0f%%), Updates: %d", 
                totalStations, filledCells, totalCells, fillRatio * 100, updateCount);
        }
    }
}
