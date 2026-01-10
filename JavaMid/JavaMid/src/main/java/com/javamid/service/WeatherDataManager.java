package com.javamid.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javamid.client.OpenMeteoClient;
import com.javamid.config.MapConfig;
import com.javamid.model.WeatherStation;
import com.javamid.ui.WindOverlayPanel;
import com.javamid.util.GeoUtils;

import javax.swing.*;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestor centralizado de datos meteorológicos.
 * Responsable de obtener, cachear y actualizar información del clima.
 */
public class WeatherDataManager {
    
    private static final Logger LOGGER = Logger.getLogger(WeatherDataManager.class.getName());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    
    private final OpenMeteoClient openMeteoClient;
    private final ExecutorService weatherDataExecutor;
    
    private JsonNode currentWeatherData;
    private int currentTimeIndex = -1;
    private boolean fetchInProgress = false;
    
    // Callbacks
    private Consumer<String> onWeatherTextUpdate;
    private Consumer<WeatherSnapshot> onWeatherSnapshotUpdate;
    
    public WeatherDataManager() {
        this.openMeteoClient = new OpenMeteoClient();
        this.weatherDataExecutor = Executors.newFixedThreadPool(MapConfig.WEATHER_DATA_THREAD_POOL_SIZE);
    }
    
    /**
     * Establece el callback para actualizar el texto de datos meteorológicos.
     */
    public void setOnWeatherTextUpdate(Consumer<String> callback) {
        this.onWeatherTextUpdate = callback;
    }
    
    /**
     * Establece el callback para actualizar snapshot de datos meteorológicos.
     */
    public void setOnWeatherSnapshotUpdate(Consumer<WeatherSnapshot> callback) {
        this.onWeatherSnapshotUpdate = callback;
    }
    
    /**
     * Obtiene datos meteorológicos para una estación.
     */
    public void fetchWeatherForStation(WeatherStation station) {
        if (station == null || fetchInProgress) {
            return;
        }
        
        fetchInProgress = true;
        
        if (onWeatherTextUpdate != null) {
            onWeatherTextUpdate.accept("Cargando datos meteorológicos...");
        }
        
        SwingWorker<JsonNode, Void> worker = new SwingWorker<JsonNode, Void>() {
            @Override
            protected JsonNode doInBackground() {
                LocalDate end = LocalDate.now().minusDays(MapConfig.HISTORICAL_DATA_DELAY_DAYS);
                LocalDate start = end.minusDays(MapConfig.HISTORICAL_DATA_RANGE_DAYS);
                
                return openMeteoClient.getHistoricalWeather(
                    station.getLatitude(), 
                    station.getLongitude(), 
                    start, 
                    end
                );
            }
            
            @Override
            protected void done() {
                try {
                    JsonNode response = get();
                    currentWeatherData = response;
                    
                    if (onWeatherTextUpdate != null) {
                        onWeatherTextUpdate.accept(prettyJson(response));
                    }
                    
                    if (response != null && response.has("hourly")) {
                        JsonNode hourly = response.get("hourly");
                        if (hourly.has("time")) {
                            JsonNode timeArray = hourly.get("time");
                            int lastIndex = timeArray.size() - 1;
                            currentTimeIndex = lastIndex;
                            
                            // Actualizar con el último índice
                            updateWeatherAtIndex(lastIndex);
                        }
                    }
                } catch (Exception e) {
                    if (onWeatherTextUpdate != null) {
                        onWeatherTextUpdate.accept("Error al obtener datos:\n" + e.getMessage());
                    }
                    LOGGER.log(Level.WARNING, "Error fetching weather data", e);
                } finally {
                    fetchInProgress = false;
                }
            }
        };
        
        worker.execute();
    }
    
    /**
     * Actualiza los datos meteorológicos para un índice temporal específico.
     */
    public void updateWeatherAtIndex(int index) {
        if (currentWeatherData == null || !currentWeatherData.has("hourly")) {
            return;
        }
        
        JsonNode hourly = currentWeatherData.get("hourly");
        if (!hourly.has("time")) {
            return;
        }
        
        JsonNode timeArray = hourly.get("time");
        if (index < 0 || index >= timeArray.size()) {
            return;
        }
        
        currentTimeIndex = index;
        
        String timeStr = timeArray.get(index).asText();
        
        // Extraer datos
        Double temperature = extractValue(hourly, "temperature_2m", index);
        Double humidity = extractValue(hourly, "relative_humidity_2m", index);
        Double windSpeedKmh = extractValue(hourly, "wind_speed_10m", index);
        Double windDirection = extractValue(hourly, "wind_direction_10m", index);
        // Precipitación: algunos esquemas usan "precipitation" o "rain"
        Double precipitation = extractValue(hourly, "precipitation", index);
        if (precipitation == null) {
            precipitation = extractValue(hourly, "rain", index);
        }
        
        Double windSpeedMs = windSpeedKmh != null ? GeoUtils.kmhToMs(windSpeedKmh) : null;
        
        WeatherSnapshot snapshot = new WeatherSnapshot(
            timeStr, temperature, humidity, windSpeedMs, windDirection, precipitation
        );
        
        if (onWeatherSnapshotUpdate != null) {
            onWeatherSnapshotUpdate.accept(snapshot);
        }
    }
    
    /**
     * Carga datos de viento para múltiples estaciones en paralelo.
     */
    public void fetchWindDataForStations(List<WeatherStation> stations, WindOverlayPanel windOverlay) {
        if (stations == null || stations.isEmpty() || windOverlay == null) {
            return;
        }
        
        LocalDate end = LocalDate.now().minusDays(MapConfig.HISTORICAL_DATA_DELAY_DAYS);
        LocalDate start = end.minusDays(MapConfig.HISTORICAL_DATA_RANGE_DAYS);
        
        for (WeatherStation station : stations) {
            weatherDataExecutor.execute(() -> {
                try {
                    JsonNode response = openMeteoClient.getHistoricalWeather(
                        station.getLatitude(), 
                        station.getLongitude(), 
                        start, 
                        end
                    );
                    
                    if (response != null && response.has("hourly")) {
                        JsonNode hourly = response.get("hourly");
                        if (hourly.has("time") && hourly.has("wind_speed_10m") && hourly.has("wind_direction_10m")) {
                            JsonNode timeArray = hourly.get("time");
                            int lastIndex = timeArray.size() - 1;
                            
                            if (lastIndex >= 0) {
                                JsonNode speedArray = hourly.get("wind_speed_10m");
                                JsonNode dirArray = hourly.get("wind_direction_10m");
                                
                                if (speedArray.size() > lastIndex && !speedArray.get(lastIndex).isNull() &&
                                    dirArray.size() > lastIndex && !dirArray.get(lastIndex).isNull()) {
                                    double speedKmh = speedArray.get(lastIndex).asDouble();
                                    double speedMs = GeoUtils.kmhToMs(speedKmh);
                                    double deg = dirArray.get(lastIndex).asDouble();
                                    
                                    windOverlay.setStationWind(station.getId(), speedMs, deg);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error loading wind data for station " + station.getId(), e);
                }
            });
        }
    }
    
    /**
     * Extrae un valor numérico de un array JSON en un índice específico.
     */
    private Double extractValue(JsonNode hourly, String fieldName, int index) {
        if (!hourly.has(fieldName)) {
            return null;
        }
        
        JsonNode array = hourly.get(fieldName);
        if (array.size() <= index || array.get(index).isNull()) {
            return null;
        }
        
        return array.get(index).asDouble();
    }
    
    /**
     * Formatea JSON de manera legible.
     */
    private String prettyJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return "(sin datos)";
        }
        try {
            return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return node.toString();
        }
    }
    
    /**
     * Obtiene el índice temporal actual.
     */
    public int getCurrentTimeIndex() {
        return currentTimeIndex;
    }
    
    /**
     * Obtiene el número máximo de índices temporales disponibles.
     */
    public int getMaxTimeIndex() {
        if (currentWeatherData == null || !currentWeatherData.has("hourly")) {
            return -1;
        }
        
        JsonNode hourly = currentWeatherData.get("hourly");
        if (!hourly.has("time")) {
            return -1;
        }
        
        return hourly.get("time").size() - 1;
    }
    
    /**
     * Verifica si hay datos meteorológicos disponibles.
     */
    public boolean hasWeatherData() {
        return currentWeatherData != null && currentWeatherData.has("hourly");
    }
    
    /**
     * Libera recursos.
     */
    public void dispose() {
        weatherDataExecutor.shutdown();
        try {
            if (!weatherDataExecutor.awaitTermination(MapConfig.WIND_DATA_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                weatherDataExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            weatherDataExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Snapshot inmutable de datos meteorológicos en un momento específico.
     */
    public static class WeatherSnapshot {
        public final String time;
        public final Double temperature;
        public final Double humidity;
        public final Double windSpeedMs;
        public final Double windDirectionDeg;
        public final Double precipitationMm;
        
        public WeatherSnapshot(String time, Double temperature, Double humidity, 
                             Double windSpeedMs, Double windDirectionDeg, Double precipitationMm) {
            this.time = time;
            this.temperature = temperature;
            this.humidity = humidity;
            this.windSpeedMs = windSpeedMs;
            this.windDirectionDeg = windDirectionDeg;
            this.precipitationMm = precipitationMm;
        }
        
        public boolean hasWindData() {
            return windSpeedMs != null && windDirectionDeg != null;
        }
    }
}
