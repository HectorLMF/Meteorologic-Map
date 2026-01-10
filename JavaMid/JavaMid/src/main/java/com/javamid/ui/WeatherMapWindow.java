package com.javamid.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.javamid.config.MapConfig;
import com.javamid.model.WeatherStation;
import com.javamid.service.StationManager;
import com.javamid.service.WeatherDataManager;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.*;
import org.jxmapviewer.input.*;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ventana principal de la aplicación con visor de OpenStreetMap.
 * REFACTORIZADA: Usa managers dedicados y UIComponentFactory.
 */
public class WeatherMapWindow extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(WeatherMapWindow.class.getName());
    
    // Core components
    private final JXMapViewer mapViewer;
    private final StationManager stationManager;
    private final WeatherDataManager weatherDataManager;
    
    // UI Components
    private final WindOverlayPanel windOverlayPanel;
    private final HumidityOverlayPanel humidityOverlayPanel;
    private final TemperatureOverlayPanel temperatureOverlayPanel;
    private final StationMarkerPanel stationMarkerPanel;
    private final LegendPanel legendPanel;
    
    // UI Component references (created by factory)
    private UIComponentFactory.StationInfoPanelComponents stationInfoComponents;
    private UIComponentFactory.WeatherInfoPanelComponents weatherInfoComponents;
    private UIComponentFactory.TimeSelectorPanelComponents timeSelectorComponents;
    private UIComponentFactory.LayersPanelComponents layersComponents;
    
    private final JTextArea weatherDataArea;
    
    // State
    private volatile double lastWindSpeed = MapConfig.DEFAULT_WIND_SPEED_MS;
    private volatile double lastWindDeg = MapConfig.DEFAULT_WIND_DIRECTION_DEG;
    
    private final Timer mapMoveDebounceTimer;
    
    public WeatherMapWindow() {
        super("Weather Station Map Viewer");
        
        // Set global User-Agent for HTTP requests
        System.setProperty("http.agent", "JavaMidWeatherMap/1.0 (educational use)");
        
        // Initialize managers
        stationManager = new StationManager();
        weatherDataManager = new WeatherDataManager();
        
        setSize(MapConfig.DEFAULT_WINDOW_WIDTH, MapConfig.DEFAULT_WINDOW_HEIGHT);
        setMinimumSize(new Dimension(MapConfig.MIN_WINDOW_WIDTH, MapConfig.MIN_WINDOW_HEIGHT));
        
        // Create map viewer
        mapViewer = createMapViewer();
        
        // Debounce timer for map movements
        mapMoveDebounceTimer = new Timer(MapConfig.MAP_MOVE_DEBOUNCE_MS, e -> onMapMoved());
        mapMoveDebounceTimer.setRepeats(false);
        
        // Create overlays
        windOverlayPanel = new WindOverlayPanel(mapViewer);
        // Registrar listener para actualizar brújula cuando cambia el viento
        windOverlayPanel.setWindChangeListener((speedMs, directionDeg) -> {
            updateWindDirectionLabel(directionDeg, speedMs);
        });
        
        humidityOverlayPanel = new HumidityOverlayPanel(mapViewer);
        temperatureOverlayPanel = new TemperatureOverlayPanel(mapViewer);
        stationMarkerPanel = new StationMarkerPanel(mapViewer);
        // Wire overlay panels to marker panel to filter rendering to unclustered stations
        humidityOverlayPanel.setStationMarkerPanel(stationMarkerPanel);
        temperatureOverlayPanel.setStationMarkerPanel(stationMarkerPanel);
        // Al hacer click en el mapa, actualizamos primero la estación activa en StationManager
        // para que las cargas de datos usen siempre la estación correcta.
        stationMarkerPanel.setOnStationSelected(stationManager::selectStation);
        // Legend (create early so layered pane can place it)
        legendPanel = new LegendPanel();
        legendPanel.setVisible(false);
        
        // Setup manager callbacks
        setupManagerCallbacks();
        
        // Create UI
        JLayeredPane layeredPane = createLayeredPane();
        stationInfoComponents = UIComponentFactory.createStationInfoPanel();
        weatherInfoComponents = UIComponentFactory.createWeatherInfoPanel();
        timeSelectorComponents = UIComponentFactory.createTimeSelectorPanel(this::onTimeSliderChanged);
        
        // Setup time selector button actions
        setupTimeSelectorButtons();
        
        // Create weather data area
        weatherDataArea = new JTextArea(20, 30);
        weatherDataArea.setEditable(false);
        weatherDataArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        // Create layers panel
        layersComponents = UIComponentFactory.createLayersPanel(windOverlayPanel, humidityOverlayPanel, 
                                    this::onWindLayerToggled, this::onHumidityLayerToggled);

        // Hook temperature toggle for mutual exclusivity
        layersComponents.temperatureButton.addActionListener(e -> onTemperatureLayerToggled());

        JPanel infoPanel = createInfoPanel();
        JPanel mapContainer = createMapContainer(layeredPane);
        
        add(mapContainer, BorderLayout.CENTER);
        add(infoPanel, BorderLayout.EAST);

        // Initialize overlays/legend state
        SwingUtilities.invokeLater(this::updateOverlayMode);
        
        // Add map listeners
        mapViewer.addPropertyChangeListener("center", (PropertyChangeListener) evt -> scheduleMapMoved());
        mapViewer.addPropertyChangeListener("zoom", (PropertyChangeListener) evt -> scheduleMapMoved());
        
        // Initial station search
        SwingUtilities.invokeLater(this::scheduleMapMoved);
    }
    
    private JXMapViewer createMapViewer() {
        JXMapViewer viewer = new JXMapViewer();
        
        // Setup OpenStreetMap tile factory
        TileFactoryInfo info = new LoggingOSMTileFactoryInfo();
        DefaultTileFactory tileFactory = new DefaultTileFactory(info);
        tileFactory.setThreadPoolSize(MapConfig.MAP_TILE_THREAD_POOL_SIZE);
        tileFactory.setUserAgent(MapConfig.OSM_USER_AGENT);
        viewer.setTileFactory(tileFactory);
        
        // Set initial position
        GeoPosition initialPos = new GeoPosition(MapConfig.DEFAULT_LATITUDE, MapConfig.DEFAULT_LONGITUDE);
        viewer.setZoom(MapConfig.DEFAULT_MAP_ZOOM);
        viewer.setAddressLocation(initialPos);
        
        // Add mouse interaction
        PanMouseInputListener panListener = new PanMouseInputListener(viewer);
        viewer.addMouseListener(panListener);
        viewer.addMouseMotionListener(panListener);
        
        ZoomMouseWheelListenerCursor zoomListener = new ZoomMouseWheelListenerCursor(viewer);
        viewer.addMouseWheelListener(zoomListener);
        
        return viewer;
    }
    
    private JLayeredPane createLayeredPane() {
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setLayout(null);
        
        // Add layers
        mapViewer.setBounds(0, 0, 850, 800);
        layeredPane.add(mapViewer, JLayeredPane.DEFAULT_LAYER);
        
        windOverlayPanel.setBounds(0, 0, 850, 800);
        layeredPane.add(windOverlayPanel, Integer.valueOf(1000));
        humidityOverlayPanel.setBounds(0, 0, 850, 800);
        layeredPane.add(humidityOverlayPanel, Integer.valueOf(900));
        temperatureOverlayPanel.setBounds(0, 0, 850, 800);
        layeredPane.add(temperatureOverlayPanel, Integer.valueOf(900));
        
        
        stationMarkerPanel.setBounds(0, 0, 850, 800);
        layeredPane.add(stationMarkerPanel, Integer.valueOf(2000));
        
        // Add influence control panel
        UIComponentFactory.InfluencePanelComponents influenceComponents = 
            UIComponentFactory.createInfluenceControlPanel(stationMarkerPanel, windOverlayPanel);

        // Inicializar radios de influencia de todas las capas con el valor del slider al arrancar
        int initialRadiusKm = influenceComponents.slider.getValue();
        stationMarkerPanel.setInfluenceRadiusKm(initialRadiusKm);
        windOverlayPanel.setInfluenceRadiusKm(initialRadiusKm);
        humidityOverlayPanel.setInfluenceRadiusKm(initialRadiusKm);
        temperatureOverlayPanel.setInfluenceRadiusKm(initialRadiusKm);
        
        // Actualizar también el panel de humedad cuando cambie el radio
        influenceComponents.slider.addChangeListener(e -> {
            int radiusKm = influenceComponents.slider.getValue();
            humidityOverlayPanel.setInfluenceRadiusKm(radiusKm);
            temperatureOverlayPanel.setInfluenceRadiusKm(radiusKm);
        });
        
        influenceComponents.panel.setBounds(275, 740, 300, 50);
        layeredPane.add(influenceComponents.panel, Integer.valueOf(3000));
        
        // Listen to resize events
        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = layeredPane.getWidth();
                int h = layeredPane.getHeight();
                mapViewer.setBounds(0, 0, w, h);
                windOverlayPanel.setBounds(0, 0, w, h);
                humidityOverlayPanel.setBounds(0, 0, w, h);
                temperatureOverlayPanel.setBounds(0, 0, w, h);
                stationMarkerPanel.setBounds(0, 0, w, h);
                windOverlayPanel.onMapChanged();
                humidityOverlayPanel.onMapChanged();
                temperatureOverlayPanel.onMapChanged();
                stationMarkerPanel.repaint();
                
                // Reposition influence panel
                int influenceX = (w - 300) / 2;
                int influenceY = h - 60;
                influenceComponents.panel.setBounds(influenceX, influenceY, 300, 50);
            }
        });
        
        // Listen to map changes
        mapViewer.addPropertyChangeListener("zoom", evt -> {
            windOverlayPanel.onMapChanged();
            humidityOverlayPanel.onMapChanged();
            temperatureOverlayPanel.onMapChanged();
            stationMarkerPanel.repaint();
        });
        
        mapViewer.addPropertyChangeListener("center", evt -> {
            windOverlayPanel.onMapChanged();
            humidityOverlayPanel.onMapChanged();
            temperatureOverlayPanel.onMapChanged();
            stationMarkerPanel.repaint();
        });

        // Legend position (top-left)
        legendPanel.setBounds(12, 12, 160, 140);
        layeredPane.add(legendPanel, Integer.valueOf(2500));
        
        return layeredPane;
    }
    
    private JPanel createMapContainer(JLayeredPane layeredPane) {
        JPanel mapContainer = new JPanel(new BorderLayout());
        
        // Create bottom info panel - weather info on top, time selector on bottom
        JPanel bottomInfoPanel = new JPanel();
        bottomInfoPanel.setLayout(new BorderLayout());
        bottomInfoPanel.setOpaque(false);
        
        // Weather info panel (temperature, wind, humidity)
        bottomInfoPanel.add(weatherInfoComponents.panel, BorderLayout.NORTH);
        
        // Time selector panel below it
        bottomInfoPanel.add(timeSelectorComponents.panel, BorderLayout.SOUTH);
        
        mapContainer.add(layeredPane, BorderLayout.CENTER);
        mapContainer.add(bottomInfoPanel, BorderLayout.SOUTH);
        
        return mapContainer;
    }
    
    private JPanel createInfoPanel() {
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setPreferredSize(new Dimension(MapConfig.INFO_PANEL_WIDTH, 600));
        infoPanel.setMinimumSize(new Dimension(300, 400));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Weather data panel
        JPanel weatherPanel = new JPanel(new BorderLayout());
        weatherPanel.setBorder(BorderFactory.createTitledBorder("Datos Meteorológicos"));
        JScrollPane scrollPane = new JScrollPane(weatherDataArea);
        weatherPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Info source panel
        JPanel infoSourcePanel = new JPanel();
        infoSourcePanel.setBorder(BorderFactory.createTitledBorder("Fuente de Datos"));
        JLabel sourceLabel = new JLabel("<html><b>Open-Meteo Historical Weather API</b><br/>Datos históricos gratuitos sin necesidad de API key</html>");
        infoSourcePanel.add(sourceLabel);
        
        // Particle controls
        JPanel particleControlsPanel = UIComponentFactory.createParticleControlPanel(windOverlayPanel);
        
        // Refresh button
        JButton refreshButton = new JButton("Actualizar Datos");
        refreshButton.addActionListener(e -> updateWeatherData());
        
        // Assemble bottom panel
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.add(infoSourcePanel);
        bottomPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        bottomPanel.add(layersComponents.panel);
        bottomPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        bottomPanel.add(particleControlsPanel);
        bottomPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        bottomPanel.add(refreshButton);
        
        infoPanel.add(stationInfoComponents.panel, BorderLayout.NORTH);
        infoPanel.add(weatherPanel, BorderLayout.CENTER);
        infoPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        return infoPanel;
    }
    
    private void setupManagerCallbacks() {
        // Station manager callbacks
        stationManager.setOnStationsLoaded(this::onStationsLoaded);
        stationManager.setOnStationSelected(this::onStationSelected);
        stationManager.setOnStatusUpdate(msg -> stationInfoComponents.stationLabel.setText(msg));
        
        // Weather data manager callbacks
        weatherDataManager.setOnWeatherTextUpdate(text -> weatherDataArea.setText(text));
        weatherDataManager.setOnWeatherSnapshotUpdate(this::updateWeatherSnapshot);
    }
    
    private void setupTimeSelectorButtons() {
        timeSelectorComponents.prevButton.addActionListener(e -> {
            if (timeSelectorComponents.slider.isEnabled() && 
                timeSelectorComponents.slider.getValue() > timeSelectorComponents.slider.getMinimum()) {
                int newValue = timeSelectorComponents.slider.getValue() - 1;
                timeSelectorComponents.slider.setValue(newValue);
                weatherDataManager.updateWeatherAtIndex(newValue);
            }
        });
        
        timeSelectorComponents.nextButton.addActionListener(e -> {
            if (timeSelectorComponents.slider.isEnabled() && 
                timeSelectorComponents.slider.getValue() < timeSelectorComponents.slider.getMaximum()) {
                int newValue = timeSelectorComponents.slider.getValue() + 1;
                timeSelectorComponents.slider.setValue(newValue);
                weatherDataManager.updateWeatherAtIndex(newValue);
            }
        });
        
        timeSelectorComponents.latestButton.addActionListener(e -> {
            if (timeSelectorComponents.slider.isEnabled()) {
                int maxValue = timeSelectorComponents.slider.getMaximum();
                timeSelectorComponents.slider.setValue(maxValue);
                weatherDataManager.updateWeatherAtIndex(maxValue);
            }
        });
    }
    
    private void scheduleMapMoved() {
        mapMoveDebounceTimer.restart();
    }
    
    private void onMapMoved() {
        GeoPosition center = mapViewer.getCenterPosition();
        double lat = center.getLatitude();
        double lon = center.getLongitude();
        
        stationInfoComponents.coordsLabel.setText(String.format("Centro: %.4f, %.4f", lat, lon));
        
        if (!stationManager.isAreaCovered(lat, lon)) {
            stationManager.loadStationsForArea(lat, lon);
        }
    }
    
    private void onStationsLoaded(List<WeatherStation> stations) {
        if (stations == null || stations.isEmpty()) {
            stationInfoComponents.stationLabel.setText("No se encontraron estaciones cercanas");
            stationInfoComponents.dataArea.setText("");
            weatherDataArea.setText("");
            stationMarkerPanel.clearStations();
            windOverlayPanel.setStations(new java.util.ArrayList<>());
            humidityOverlayPanel.setStations(new java.util.ArrayList<>());
            temperatureOverlayPanel.setStations(new java.util.ArrayList<>());
            return;
        }
        
        stationMarkerPanel.setStations(stations);
        windOverlayPanel.setStations(stations);
        humidityOverlayPanel.setStations(stations);
        temperatureOverlayPanel.setStations(stations);
        
        // Cargar humedad de todas las estaciones en segundo plano
        loadHumidityForAllStations(stations);
        // (Opcional) temperatura y precipitación pueden cargarse de forma similar si se requiere
        
        WeatherStation activeStation = stationMarkerPanel.getActiveStation();
        if (activeStation != null) {
            stationManager.selectStation(activeStation);
        }
    }
    
    private void loadHumidityForAllStations(List<WeatherStation> stations) {
        if (stations == null || stations.isEmpty()) {
            LOGGER.info("No stations to load humidity for");
            return;
        }
        
        LOGGER.info("Starting humidity load for " + stations.size() + " stations");
        
        int loadedCount = 0;
        for (WeatherStation station : stations) {
            // Skip if already loaded
            if (humidityOverlayPanel.hasHumidityData(station.getId())) {
                continue;
            }
            
            loadedCount++;
            new SwingWorker<Double, Void>() {
                @Override
                protected Double doInBackground() throws Exception {
                    try {
                        LocalDate end = LocalDate.now().minusDays(1);
                        LocalDate start = end;
                        JsonNode response = new com.javamid.client.OpenMeteoClient().getHistoricalWeather(
                            station.getLatitude(), station.getLongitude(), start, end);
                        
                        if (response != null && response.has("hourly")) {
                            JsonNode hourly = response.get("hourly");
                            if (hourly.has("relative_humidity_2m")) {
                                JsonNode humidityArray = hourly.get("relative_humidity_2m");
                                if (humidityArray.size() > 0) {
                                    int lastIndex = humidityArray.size() - 1;
                                    return humidityArray.get(lastIndex).asDouble();
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error fetching humidity for station " + station.getId() + " (" + 
                                   station.getLatitude() + "," + station.getLongitude() + ")", e);
                    }
                    return null;
                }
                
                @Override
                protected void done() {
                    try {
                        Double humidity = get();
                        if (humidity != null) {
                            LOGGER.info("Humidity loaded for station " + station.getId() + ": " + 
                                       String.format("%.1f%%", humidity));
                            humidityOverlayPanel.setStationHumidity(station.getId(), humidity);
                        } else {
                            LOGGER.warning("No humidity data returned for station " + station.getId());
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error loading humidity for station " + station.getId(), e);
                    }
                }
            }.execute();
        }
        
        LOGGER.info("Queued humidity load for " + loadedCount + " new stations");
    }
    
    private void onStationSelected(WeatherStation station) {
        if (station == null) {
            LOGGER.warning("onStationSelected: estación NULL");
            return;
        }
        
        LOGGER.info("========== ESTACIÓN SELECCIONADA ==========");
        LOGGER.info("ID: " + station.getId());
        LOGGER.info("Nombre: " + station.getName());
        LOGGER.info("País: " + station.getCountry());
        LOGGER.info("Lat: " + station.getLatitude());
        LOGGER.info("Lon: " + station.getLongitude());
        LOGGER.info("=========================================");
        
        Double km = station.getDistanceKm();
        String distanceSuffix = (km == null) ? "" : String.format(" - %.1f km", km);
        String country = station.getCountry() == null ? "N/A" : station.getCountry();
        String source = station.getSource() == null ? "" : (" - " + station.getSource());
        
        stationInfoComponents.stationLabel.setText(
            "<html><b>" + station.getName() + "</b> (" + country + ")" + distanceSuffix + source + "</html>");
        
        displayStationData(station);
        
        // Actualizar el panel superior inmediatamente con datos de la estación
        LOGGER.info("Llamando a updateTopPanelForStation con: " + station.getId());
        updateTopPanelForStation(station);
        
        // Cargar datos meteorológicos en background
        LOGGER.info("Llamando a updateWeatherData");
        updateWeatherData();
    }
    
    private void updateTopPanelForStation(WeatherStation station) {
        LOGGER.info("[UPDATE_TOP_PANEL] Estación: " + station.getId() + " - " + station.getName());
        
        // Mostrar datos disponibles inmediatamente
        weatherInfoComponents.temperatureLabel.setText("Temp: --");
        weatherInfoComponents.humidityLabel.setText("Humedad: --%");
        weatherInfoComponents.windLabel.setText(String.format("Viento: %.0f° (cargando...)", lastWindDeg));
        weatherInfoComponents.windCompass.setWindDirection(lastWindDeg);
        
        LOGGER.info("[UPDATE_TOP_PANEL] Reseté labels, ahora cargando datos...");
        
        // CRÍTICO: Hacer copia final de la estación para evitar problemas de closure
        final String stationId = station.getId();
        final String stationName = station.getName();
        final double lat = station.getLatitude();
        final double lon = station.getLongitude();
        
        LOGGER.info("[UPDATE_TOP_PANEL] Variables finales: id=" + stationId + ", name=" + stationName + 
                   ", lat=" + lat + ", lon=" + lon);
        
        // Cargar temperatura y humedad de la estación en background
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    LocalDate end = LocalDate.now().minusDays(1);
                    LocalDate start = end.minusDays(6);  // Últimos 7 días para mejor datos
                    
                    LOGGER.info("[WORKER] Iniciando carga para: " + stationId + 
                               " (" + stationName + ") " +
                               "(" + lat + ", " + lon + ")" +
                               " del " + start + " al " + end);
                    
                    JsonNode response = new com.javamid.client.OpenMeteoClient().getHistoricalWeather(
                        lat, lon, start, end);
                    
                    if (response != null && response.has("hourly")) {
                        LOGGER.info("[WORKER] Respuesta recibida para " + stationId + ", procesando...");
                        JsonNode hourly = response.get("hourly");
                        
                        // Obtener temperatura - promediar los últimos 6 valores disponibles
                        if (hourly.has("temperature_2m")) {
                            JsonNode tempArray = hourly.get("temperature_2m");
                            LOGGER.info("[WORKER] Temperatura array size: " + tempArray.size() + " para " + stationId);
                            if (tempArray.size() > 0) {
                                double tempSum = 0;
                                int tempCount = 0;
                                int startIdx = Math.max(0, tempArray.size() - 6);
                                for (int i = startIdx; i < tempArray.size(); i++) {
                                    tempSum += tempArray.get(i).asDouble();
                                    tempCount++;
                                }
                                Double temperature = tempCount > 0 ? tempSum / tempCount : null;
                                if (temperature != null) {
                                    LOGGER.info("[WORKER] Temperatura calculada para " + stationId + " (" + stationName + "): " + 
                                               String.format("%.1f°C", temperature));
                                    SwingUtilities.invokeLater(() -> {
                                        LOGGER.info("[WORKER-EDT] Actualizando UI con temperatura: " + 
                                                   String.format("%.1f°C para %s", temperature, stationId));
                                        weatherInfoComponents.temperatureLabel.setText(
                                            String.format("Temp: %.1f°C", temperature));
                                        temperatureOverlayPanel.setStationTemperature(stationId, temperature);
                                    });
                                }
                            }
                        }
                        
                        // Obtener humedad - promediar los últimos 6 valores disponibles
                        if (hourly.has("relative_humidity_2m")) {
                            JsonNode humidityArray = hourly.get("relative_humidity_2m");
                            LOGGER.info("[WORKER] Humedad array size: " + humidityArray.size() + " para " + stationId);
                            if (humidityArray.size() > 0) {
                                double humiditySum = 0;
                                int humidityCount = 0;
                                int startIdx = Math.max(0, humidityArray.size() - 6);
                                for (int i = startIdx; i < humidityArray.size(); i++) {
                                    humiditySum += humidityArray.get(i).asDouble();
                                    humidityCount++;
                                }
                                Double humidity = humidityCount > 0 ? humiditySum / humidityCount : null;
                                if (humidity != null) {
                                    LOGGER.info("[WORKER] Humedad calculada para " + stationId + " (" + stationName + "): " + 
                                               String.format("%.0f%%", humidity));
                                    SwingUtilities.invokeLater(() -> {
                                        LOGGER.info("[WORKER-EDT] Actualizando UI con humedad: " + 
                                                   String.format("%.0f%% para %s", humidity, stationId));
                                        weatherInfoComponents.humidityLabel.setText(
                                            String.format("Humedad: %.0f%%", humidity));
                                        humidityOverlayPanel.setStationHumidity(stationId, humidity);
                                    });
                                }
                            }
                        }

                    } else {
                        LOGGER.warning("[WORKER] No se obtuvieron datos de OpenMeteo para " + stationId);
                    }

                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error loading weather data for station " + station.getId(), e);
                }
                return null;
            }
        }.execute();
    }
    
    private void displayStationData(WeatherStation station) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("ID: ").append(station.getId()).append("\n");
        sb.append("Nombre: ").append(station.getName()).append("\n");
        if (station.getSource() != null) {
            sb.append("Fuente: ").append(station.getSource()).append("\n");
        }
        sb.append("País: ").append(station.getCountry() != null ? station.getCountry() : "N/A").append("\n");
        if (station.getState() != null) {
            sb.append("Región: ").append(station.getState()).append("\n");
        }
        sb.append("Lat: ").append(String.format("%.4f", station.getLatitude())).append("°\n");
        sb.append("Lon: ").append(String.format("%.4f", station.getLongitude())).append("°");
        
        if (station.getElevationMeters() != null) {
            sb.append("\nAltitud: ").append(station.getElevationMeters()).append(" m");
        }
        if (station.getTimezone() != null) {
            sb.append("\nTZ: ").append(station.getTimezone());
        }
        if (station.getDistanceKm() != null) {
            sb.append("\nDistancia al centro: ").append(String.format("%.2f", station.getDistanceKm())).append(" km");
        }
        
        stationInfoComponents.dataArea.setText(sb.toString());
    }
    
    private void updateWeatherData() {
        WeatherStation currentStation = stationManager.getActiveStation();
        if (currentStation == null) return;
        
        weatherDataManager.fetchWeatherForStation(currentStation);
        
        // Load wind data for visible stations
        List<WeatherStation> visibleStations = windOverlayPanel.getVisibleStations();
        if (visibleStations != null && !visibleStations.isEmpty()) {
            List<WeatherStation> limitedStations = visibleStations.stream()
                .limit(MapConfig.MAX_WIND_DATA_STATIONS)
                .collect(java.util.stream.Collectors.toList());
            
            weatherDataManager.fetchWindDataForStations(limitedStations, windOverlayPanel);
        }
        
        // Update time slider if data is available
        if (weatherDataManager.hasWeatherData()) {
            int maxIndex = weatherDataManager.getMaxTimeIndex();
            timeSelectorComponents.slider.setMinimum(0);
            timeSelectorComponents.slider.setMaximum(maxIndex);
            timeSelectorComponents.slider.setValue(maxIndex);
            timeSelectorComponents.slider.setEnabled(true);
            timeSelectorComponents.panel.setVisible(true);
        }
    }
    
    private void updateWeatherSnapshot(WeatherDataManager.WeatherSnapshot snapshot) {
        // Asegurar que todas las actualizaciones de UI y overlays ocurren en EDT
        SwingUtilities.invokeLater(() -> {
            timeSelectorComponents.label.setText("Hora: " + snapshot.time);

            if (snapshot.temperature != null) {
                weatherInfoComponents.temperatureLabel.setText(
                    String.format("Temp: %.1f°C", snapshot.temperature));
                // Actualizar temperatura en overlay si está activo
                WeatherStation currentStationTemp = stationManager.getActiveStation();
                if (currentStationTemp != null) {
                    temperatureOverlayPanel.setStationTemperature(currentStationTemp.getId(), snapshot.temperature);
                }
            } else {
                weatherInfoComponents.temperatureLabel.setText("Temp: N/A");
            }

            if (snapshot.humidity != null) {
                weatherInfoComponents.humidityLabel.setText(
                    String.format("Humedad: %.0f%%", snapshot.humidity));

                // Actualizar humedad en el overlay si está activo
                WeatherStation currentStation = stationManager.getActiveStation();
                if (currentStation != null) {
                    humidityOverlayPanel.setStationHumidity(currentStation.getId(), snapshot.humidity);
                }
            } else {
                weatherInfoComponents.humidityLabel.setText("Humedad: N/A");
            }

            if (snapshot.hasWindData()) {
                lastWindSpeed = snapshot.windSpeedMs;
                lastWindDeg = snapshot.windDirectionDeg;
                updateWindDirectionLabel(snapshot.windDirectionDeg, snapshot.windSpeedMs);

                WeatherStation currentStation = stationManager.getActiveStation();
                if (currentStation != null) {
                    windOverlayPanel.setStationWind(currentStation.getId(),
                        snapshot.windSpeedMs, snapshot.windDirectionDeg);
                }

                // Reiniciar animación de partículas con la nueva dirección/velocidad si la capa está activa
                if (layersComponents.windButton.isSelected()) {
                    windOverlayPanel.stopAnimation();
                    windOverlayPanel.setWind(lastWindSpeed, lastWindDeg);
                    windOverlayPanel.startAnimation();
                }
            } else {
                updateWindDirectionLabel(0, 0);
            }

            // Precipitación: opción eliminada
        });
    }
    
    private void updateWindDirectionLabel(double deg, double speedMs) {
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = (int) Math.round(((deg % 360) / 45.0)) % 8;
        String direction = directions[index];
        
        weatherInfoComponents.windLabel.setText(
            String.format("Viento: %.0f° %s (%.1f m/s)", deg, direction, speedMs));
        
        // Actualizar la brújula visual
        if (weatherInfoComponents.windCompass != null) {
            weatherInfoComponents.windCompass.setWindDirection(deg);
        }
    }
    
    private void onWindLayerToggled() {
        if (layersComponents.windButton.isSelected()) {
            windOverlayPanel.setWind(lastWindSpeed, lastWindDeg);
            windOverlayPanel.startAnimation();
        } else {
            windOverlayPanel.stopAnimation();
        }
    }
    
    private void onHumidityLayerToggled() {
        // Mutual exclusivity among humidity/temperature/precipitation
        updateOverlayMode();
    }

    private void onTemperatureLayerToggled() {
        updateOverlayMode();
    }

    /**
     * Enforce single active overlay (humidity/temperature) and update legend.
     */
    private void updateOverlayMode() {
        // If one is selected, deselect the others
        boolean h = layersComponents.humidityButton.isSelected();
        boolean t = layersComponents.temperatureButton.isSelected();
        // Prevent multiple selections: keep the last clicked; ensure exclusivity
        if (h) { layersComponents.temperatureButton.setSelected(false); }
        if (t) { layersComponents.humidityButton.setSelected(false); }

        // Apply visibility
        humidityOverlayPanel.setActive(h);
        temperatureOverlayPanel.setActive(t);

        // Legend
        if (h) { legendPanel.setMode(LegendPanel.Mode.HUMIDITY); legendPanel.setVisible(true); }
        else if (t) { legendPanel.setMode(LegendPanel.Mode.TEMPERATURE); legendPanel.setVisible(true); }
        else { legendPanel.setVisible(false); }
    }
    
    private void onTimeSliderChanged() {
        if (weatherDataManager.hasWeatherData()) {
            weatherDataManager.updateWeatherAtIndex(timeSelectorComponents.slider.getValue());
        }
    }
    
    @Override
    public void dispose() {
        weatherDataManager.dispose();
        super.dispose();
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            WeatherMapWindow window = new WeatherMapWindow();
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setLocationRelativeTo(null);
            window.setVisible(true);
        });
    }
}
