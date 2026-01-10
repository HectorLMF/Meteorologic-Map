package com.javamid.ui.presenter;

import com.fasterxml.jackson.databind.JsonNode;
import com.javamid.client.WeatherClient;
import com.javamid.config.MapConfig;
import com.javamid.model.WeatherStation;
import com.javamid.service.AsyncExecutor;
import com.javamid.service.StationManager;
import com.javamid.service.WeatherDataManager;
import com.javamid.ui.LegendPanel;
import com.javamid.ui.UIComponentFactory;
import com.javamid.ui.HumidityOverlayPanel;
import com.javamid.ui.TemperatureOverlayPanel;
import com.javamid.ui.WindOverlayPanel;
import com.javamid.ui.overlay.OverlayManager;
import com.javamid.ui.overlay.OverlayMode;

import javax.swing.*;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Presenter to coordinate overlay mode and orchestrate data loads/updates.
 */
public class WeatherMapPresenter {
    private static final Logger LOGGER = Logger.getLogger(WeatherMapPresenter.class.getName());

    private final OverlayManager overlayManager;
    private final WeatherDataManager weatherDataManager;
    private final StationManager stationManager;
    private final WindOverlayPanel windOverlayPanel;
    private final HumidityOverlayPanel humidityOverlayPanel;
    private final TemperatureOverlayPanel temperatureOverlayPanel;
    private final UIComponentFactory.TimeSelectorPanelComponents timeSelectorComponents;
    private final UIComponentFactory.WeatherInfoPanelComponents weatherInfoComponents;
    private final WeatherClient weatherClient;

    public WeatherMapPresenter(OverlayManager overlayManager,
                               WeatherDataManager weatherDataManager,
                               StationManager stationManager,
                               WindOverlayPanel windOverlayPanel,
                               HumidityOverlayPanel humidityOverlayPanel,
                               TemperatureOverlayPanel temperatureOverlayPanel,
                               UIComponentFactory.TimeSelectorPanelComponents timeSelectorComponents,
                               UIComponentFactory.WeatherInfoPanelComponents weatherInfoComponents,
                               WeatherClient weatherClient) {
        this.overlayManager = overlayManager;
        this.weatherDataManager = weatherDataManager;
        this.stationManager = stationManager;
        this.windOverlayPanel = windOverlayPanel;
        this.humidityOverlayPanel = humidityOverlayPanel;
        this.temperatureOverlayPanel = temperatureOverlayPanel;
        this.timeSelectorComponents = timeSelectorComponents;
        this.weatherInfoComponents = weatherInfoComponents;
        this.weatherClient = weatherClient;
    }

    public void updateOverlayMode(boolean humiditySelected, boolean temperatureSelected, LegendPanel legendPanel) {
        OverlayMode mode = OverlayMode.NONE;
        if (humiditySelected) mode = OverlayMode.HUMIDITY;
        else if (temperatureSelected) mode = OverlayMode.TEMPERATURE;
        overlayManager.setActiveMode(mode);

        if (legendPanel != null) {
            switch (mode) {
                case HUMIDITY:
                    legendPanel.setMode(LegendPanel.Mode.HUMIDITY);
                    legendPanel.setVisible(true);
                    break;
                case TEMPERATURE:
                    legendPanel.setMode(LegendPanel.Mode.TEMPERATURE);
                    legendPanel.setVisible(true);
                    break;
                default:
                    legendPanel.setVisible(false);
            }
        }
    }

    // Called when stations are loaded to set overlays and trigger batch loads
    public void onStationsLoaded(List<WeatherStation> stations) {
        windOverlayPanel.setStations(stations);
        humidityOverlayPanel.setStations(stations);
        temperatureOverlayPanel.setStations(stations);
        // batch loads
        loadHumidityForAllStations(stations);
        loadTemperatureForAllStations(stations);
    }

    public void updateWeatherData() {
        WeatherStation currentStation = stationManager.getActiveStation();
        if (currentStation == null) return;

        weatherDataManager.fetchWeatherForStation(currentStation);

        List<WeatherStation> visibleStations = windOverlayPanel.getVisibleStations();
        if (visibleStations != null && !visibleStations.isEmpty()) {
            List<WeatherStation> limitedStations = visibleStations.stream()
                .limit(MapConfig.MAX_WIND_DATA_STATIONS)
                .collect(java.util.stream.Collectors.toList());
            weatherDataManager.fetchWindDataForStations(limitedStations, windOverlayPanel);
        }

        if (weatherDataManager.hasWeatherData()) {
            int maxIndex = weatherDataManager.getMaxTimeIndex();
            int prevValue = timeSelectorComponents.slider.getValue();
            timeSelectorComponents.slider.setMinimum(0);
            timeSelectorComponents.slider.setMaximum(maxIndex);
            // No reiniciar la barra al seleccionar otra estación: mantener el valor previo (clampeado)
            int clamped = Math.max(0, Math.min(prevValue, maxIndex));
            timeSelectorComponents.slider.setEnabled(true);
            timeSelectorComponents.panel.setVisible(true);
            if (timeSelectorComponents.slider.getValue() != clamped) {
                timeSelectorComponents.slider.setValue(clamped);
            }
        }
    }

    public void updateTopPanelForStation(WeatherStation station) {
        weatherInfoComponents.temperatureLabel.setText("Temp: --");
        weatherInfoComponents.humidityLabel.setText("Humedad: --%");
        weatherInfoComponents.windLabel.setText("Viento: (cargando...)");

        final String stationId = station.getId();
        final String stationName = station.getName();
        final double lat = station.getLatitude();
        final double lon = station.getLongitude();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    LocalDate end = LocalDate.now().minusDays(1);
                    LocalDate start = end.minusDays(6);

                    JsonNode response = weatherClient.getHistoricalWeather(lat, lon, start, end);
                    if (response != null && response.has("hourly")) {
                        JsonNode hourly = response.get("hourly");

                        if (hourly.has("temperature_2m")) {
                            JsonNode tempArray = hourly.get("temperature_2m");
                            if (tempArray.size() > 0) {
                                double tempSum = 0; int tempCount = 0;
                                int sIdx = Math.max(0, tempArray.size() - 6);
                                for (int i = sIdx; i < tempArray.size(); i++) { tempSum += tempArray.get(i).asDouble(); tempCount++; }
                                Double temperature = tempCount > 0 ? tempSum / tempCount : null;
                                if (temperature != null) {
                                    SwingUtilities.invokeLater(() -> {
                                        weatherInfoComponents.temperatureLabel.setText(String.format("Temp: %.1f°C", temperature));
                                        temperatureOverlayPanel.setStationTemperature(stationId, temperature);
                                    });
                                }
                            }
                        }

                        if (hourly.has("relative_humidity_2m")) {
                            JsonNode humidityArray = hourly.get("relative_humidity_2m");
                            if (humidityArray.size() > 0) {
                                double hSum = 0; int hCount = 0;
                                int sIdx = Math.max(0, humidityArray.size() - 6);
                                for (int i = sIdx; i < humidityArray.size(); i++) { hSum += humidityArray.get(i).asDouble(); hCount++; }
                                Double humidity = hCount > 0 ? hSum / hCount : null;
                                if (humidity != null) {
                                    SwingUtilities.invokeLater(() -> {
                                        weatherInfoComponents.humidityLabel.setText(String.format("Humedad: %.0f%%", humidity));
                                        humidityOverlayPanel.setStationHumidity(stationId, humidity);
                                    });
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error loading weather data for station " + stationName, e);
                }
                return null;
            }
        }.execute();
    }

    private void loadHumidityForAllStations(List<WeatherStation> stations) {
        int loadedCount = 0;
        for (WeatherStation station : stations) {
            if (humidityOverlayPanel.hasHumidityData(station.getId())) continue;
            loadedCount++;
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    LocalDate end = LocalDate.now().minusDays(1);
                    LocalDate start = end;
                    JsonNode response = weatherClient.getHistoricalWeather(
                        station.getLatitude(), station.getLongitude(), start, end);
                    if (response != null && response.has("hourly")) {
                        JsonNode hourly = response.get("hourly");
                        if (hourly.has("relative_humidity_2m")) {
                            JsonNode arr = hourly.get("relative_humidity_2m");
                            if (arr.size() > 0) { int last = arr.size() - 1; return arr.get(last).asDouble(); }
                        }
                    }
                } catch (Exception e) { LOGGER.log(Level.WARNING, "Error fetching humidity for " + station.getId(), e); }
                return null;
            }, AsyncExecutor.executor).thenAccept(hVal -> {
                if (hVal != null) {
                    SwingUtilities.invokeLater(() -> humidityOverlayPanel.setStationHumidity(station.getId(), hVal));
                }
            });
        }
        LOGGER.info("Queued humidity load for " + loadedCount + " new stations");
    }

    private void loadTemperatureForAllStations(List<WeatherStation> stations) {
        int queued = 0;
        for (WeatherStation station : stations) {
            if (temperatureOverlayPanel.hasTemperatureData(station.getId())) continue;
            queued++;
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    LocalDate end = LocalDate.now().minusDays(1);
                    LocalDate start = end;
                    JsonNode response = weatherClient.getHistoricalWeather(
                        station.getLatitude(), station.getLongitude(), start, end);
                    if (response != null && response.has("hourly")) {
                        JsonNode hourly = response.get("hourly");
                        if (hourly.has("temperature_2m")) {
                            JsonNode arr = hourly.get("temperature_2m");
                            if (arr.size() > 0) { int last = arr.size() - 1; return arr.get(last).asDouble(); }
                        }
                    }
                } catch (Exception e) { LOGGER.log(Level.WARNING, "Error fetching temperature for " + station.getId(), e); }
                return null;
            }, AsyncExecutor.executor).thenAccept(tVal -> {
                if (tVal != null) {
                    SwingUtilities.invokeLater(() -> temperatureOverlayPanel.setStationTemperature(station.getId(), tVal));
                }
            });
        }
        LOGGER.info("Queued temperature load for " + queued + " new stations");
    }

    public void onWeatherSnapshotUpdated(WeatherDataManager.WeatherSnapshot snapshot) {
        SwingUtilities.invokeLater(() -> {
            // Time label
            timeSelectorComponents.label.setText("Hora: " + snapshot.time);

            // Temperature
            if (snapshot.temperature != null) {
                weatherInfoComponents.temperatureLabel.setText(String.format("Temp: %.1f°C", snapshot.temperature));
                WeatherStation st = stationManager.getActiveStation();
                if (st != null) {
                    temperatureOverlayPanel.setStationTemperature(st.getId(), snapshot.temperature);
                }
            } else {
                weatherInfoComponents.temperatureLabel.setText("Temp: N/A");
            }

            // Humidity
            if (snapshot.humidity != null) {
                weatherInfoComponents.humidityLabel.setText(String.format("Humedad: %.0f%%", snapshot.humidity));
                WeatherStation st = stationManager.getActiveStation();
                if (st != null) {
                    humidityOverlayPanel.setStationHumidity(st.getId(), snapshot.humidity);
                }
            } else {
                weatherInfoComponents.humidityLabel.setText("Humedad: N/A");
            }

            // Wind
            if (snapshot.hasWindData()) {
                double deg = snapshot.windDirectionDeg;
                double speedMs = snapshot.windSpeedMs;
                weatherInfoComponents.windLabel.setText(String.format("Viento: %.0f° %s (%.1f m/s)", deg, dir8(deg), speedMs));
                if (weatherInfoComponents.windCompass != null) {
                    weatherInfoComponents.windCompass.setWindDirection(deg);
                }
                WeatherStation st = stationManager.getActiveStation();
                if (st != null) {
                    windOverlayPanel.setStationWind(st.getId(), speedMs, deg);
                }
            } else {
                weatherInfoComponents.windLabel.setText("Viento: N/A");
                if (weatherInfoComponents.windCompass != null) {
                    weatherInfoComponents.windCompass.setWindDirection(0);
                }
            }
        });
    }

    private String dir8(double deg) {
        String[] d = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int idx = (int) Math.round(((deg % 360) / 45.0)) % 8;
        return d[idx];
    }
}
