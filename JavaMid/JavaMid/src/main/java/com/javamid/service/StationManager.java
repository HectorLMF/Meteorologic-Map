package com.javamid.service;

import com.javamid.client.OpenMeteoClient;
import com.javamid.config.MapConfig;
import com.javamid.model.WeatherStation;
import com.javamid.util.GeoUtils;

import javax.swing.SwingWorker;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestor de estaciones meteorológicas.
 * Responsable de generar, cachear y seleccionar estaciones.
 */
public class StationManager {
    
    private static final Logger LOGGER = Logger.getLogger(StationManager.class.getName());
    
    private final OpenMeteoClient openMeteoClient;
    
    private List<WeatherStation> visibleStations = new ArrayList<>();
    private Set<String> coveredAreas = new HashSet<>();
    private WeatherStation activeStation;
    private boolean lookupInProgress = false;
    
    // Callbacks
    private Consumer<List<WeatherStation>> onStationsLoaded;
    private Consumer<WeatherStation> onStationSelected;
    private Consumer<String> onStatusUpdate;
    
    public StationManager() {
        this.openMeteoClient = new OpenMeteoClient();
    }
    
    /**
     * Establece el callback para cuando se cargan estaciones.
     */
    public void setOnStationsLoaded(Consumer<List<WeatherStation>> callback) {
        this.onStationsLoaded = callback;
    }
    
    /**
     * Establece el callback para cuando se selecciona una estación.
     */
    public void setOnStationSelected(Consumer<WeatherStation> callback) {
        this.onStationSelected = callback;
    }
    
    /**
     * Establece el callback para actualizaciones de estado.
     */
    public void setOnStatusUpdate(Consumer<String> callback) {
        this.onStatusUpdate = callback;
    }
    
    /**
     * Verifica si un área ya ha sido cubierta con estaciones.
     */
    public boolean isAreaCovered(double lat, double lon) {
        String areaId = GeoUtils.getAreaId(lat, lon, MapConfig.AREA_GRID_SIZE_KM);
        return coveredAreas.contains(areaId);
    }
    
    /**
     * Carga estaciones para un área geográfica si no ha sido cubierta.
     */
    public void loadStationsForArea(double lat, double lon) {
        String areaId = GeoUtils.getAreaId(lat, lon, MapConfig.AREA_GRID_SIZE_KM);
        
        if (coveredAreas.contains(areaId) || lookupInProgress) {
            return;
        }
        
        coveredAreas.add(areaId);
        lookupInProgress = true;
        
        SwingWorker<List<WeatherStation>, Void> worker = new SwingWorker<List<WeatherStation>, Void>() {
            @Override
            protected List<WeatherStation> doInBackground() {
                try {
                    // Generar puntos virtuales alrededor del centro
                    List<OpenMeteoClient.VirtualStation> virtualStations = 
                        openMeteoClient.generateNearbyPoints(
                            lat, lon, 
                            MapConfig.STATION_GENERATION_RADIUS_KM, 
                            MapConfig.VIRTUAL_STATIONS_COUNT
                        );
                    
                    // Convertir a WeatherStation
                    List<WeatherStation> stations = new ArrayList<>();
                    for (OpenMeteoClient.VirtualStation vs : virtualStations) {
                        WeatherStation station = new WeatherStation();
                        station.setId(vs.getId());
                        station.setName(vs.getName());
                        station.setLatitude(vs.getLatitude());
                        station.setLongitude(vs.getLongitude());
                        station.setDistanceKm(vs.getDistanceKm());
                        station.setSource("Open-Meteo");
                        station.setCountry("Grid Point");
                        stations.add(station);
                    }
                    return stations;
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error generating virtual stations", e);
                    return new ArrayList<>();
                }
            }

            @Override
            protected void done() {
                try {
                    List<WeatherStation> newStations = get();
                    
                    // Agregar nuevas estaciones sin duplicar
                    for (WeatherStation newStation : newStations) {
                        if (!isDuplicate(newStation)) {
                            visibleStations.add(newStation);
                        }
                    }
                    
                    LOGGER.info(String.format("[STATIONS] Total: %d (added %d new)", 
                        visibleStations.size(), newStations.size()));
                    
                    if (onStationsLoaded != null) {
                        onStationsLoaded.accept(visibleStations);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (onStatusUpdate != null) {
                        onStatusUpdate.accept("Búsqueda interrumpida");
                    }
                } catch (Exception e) {
                    if (onStatusUpdate != null) {
                        onStatusUpdate.accept("Error al generar estaciones");
                    }
                    LOGGER.log(Level.WARNING, "Error loading stations", e);
                } finally {
                    lookupInProgress = false;
                }
            }
        };

        worker.execute();
    }
    
    /**
     * Verifica si una estación es duplicada (existe una muy cercana).
     */
    private boolean isDuplicate(WeatherStation newStation) {
        for (WeatherStation existing : visibleStations) {
            double distance = GeoUtils.calculateDistanceKm(
                existing.getLatitude(), existing.getLongitude(),
                newStation.getLatitude(), newStation.getLongitude()
            );
            if (distance < MapConfig.MIN_STATION_DISTANCE_KM) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Selecciona una estación activa.
     */
    public void selectStation(WeatherStation station) {
        if (station == null) {
            return;
        }
        
        this.activeStation = station;
        
        if (onStationSelected != null) {
            onStationSelected.accept(station);
        }
    }
    
    /**
     * Obtiene la estación activa.
     */
    public WeatherStation getActiveStation() {
        return activeStation;
    }
    
    /**
     * Obtiene todas las estaciones visibles.
     */
    public List<WeatherStation> getVisibleStations() {
        return new ArrayList<>(visibleStations);
    }
    
    /**
     * Limpia todas las estaciones y áreas cubiertas.
     */
    public void clear() {
        visibleStations.clear();
        coveredAreas.clear();
        activeStation = null;
    }
}
