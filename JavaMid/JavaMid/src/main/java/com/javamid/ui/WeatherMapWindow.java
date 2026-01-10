package com.javamid.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.javamid.config.MapConfig;
import com.javamid.model.WeatherStation;
import com.javamid.service.StationManager;
import com.javamid.service.WeatherDataManager;
import com.javamid.client.WeatherClient;
import com.javamid.client.WeatherClientFactory;
import com.javamid.ui.overlay.OverlayManager;
import com.javamid.ui.overlay.OverlayMode;
import com.javamid.ui.presenter.WeatherMapPresenter;
import com.javamid.service.AsyncExecutor;
import com.javamid.util.EventBus;
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
    private final OverlayManager overlayManager;
    private final WeatherMapPresenter presenter;
    private final WeatherClient weatherClient;
    private final EventBus eventBus = new EventBus();
    
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

        // Overlay manager + presenter + client factory
        overlayManager = new OverlayManager();
        overlayManager.register(OverlayMode.HUMIDITY, humidityOverlayPanel);
        overlayManager.register(OverlayMode.TEMPERATURE, temperatureOverlayPanel);
        weatherClient = WeatherClientFactory.getDefault();
        
        // Setup manager callbacks
        setupManagerCallbacks();
        
        // Create UI
        JLayeredPane layeredPane = createLayeredPane();
        stationInfoComponents = UIComponentFactory.createStationInfoPanel();
        weatherInfoComponents = UIComponentFactory.createWeatherInfoPanel();
        timeSelectorComponents = UIComponentFactory.createTimeSelectorPanel(this::onTimeSliderChanged);

        // Now that UI component references exist, create presenter
        presenter = new WeatherMapPresenter(
            overlayManager,
            weatherDataManager,
            stationManager,
            windOverlayPanel,
            humidityOverlayPanel,
            temperatureOverlayPanel,
            timeSelectorComponents,
            weatherInfoComponents,
            weatherClient
        );
        
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

        // Event subscriptions
        eventBus.subscribe("StationsLoaded", evt -> {
            @SuppressWarnings("unchecked")
            List<WeatherStation> stations = (List<WeatherStation>) evt.getNewValue();
            presenter.onStationsLoaded(stations);
            // try to keep active selection consistent
            WeatherStation activeStation = stationMarkerPanel.getActiveStation();
            if (activeStation != null) {
                stationManager.selectStation(activeStation);
            }
        });
        eventBus.subscribe("WeatherSnapshotUpdated", evt -> {
            WeatherDataManager.WeatherSnapshot snapshot = (WeatherDataManager.WeatherSnapshot) evt.getNewValue();
            presenter.onWeatherSnapshotUpdated(snapshot);
        });
        
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
        stationManager.setOnStationsLoaded(stations -> {
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
            eventBus.publish("StationsLoaded", stations);
        });
        stationManager.setOnStationSelected(this::onStationSelected);
        stationManager.setOnStatusUpdate(msg -> stationInfoComponents.stationLabel.setText(msg));
        
        // Weather data manager callbacks
        weatherDataManager.setOnWeatherTextUpdate(text -> weatherDataArea.setText(text));
        weatherDataManager.setOnWeatherSnapshotUpdate(snapshot -> eventBus.publish("WeatherSnapshotUpdated", snapshot));
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
    
    // StationsLoaded handled by event subscription above
    
    // Batch loaders and overlay station wiring now handled by presenter
    
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
        presenter.updateTopPanelForStation(station);
        
        // Cargar datos meteorológicos en background
        LOGGER.info("Llamando a updateWeatherData");
        updateWeatherData();
    }
    
    // moved to presenter.updateTopPanelForStation
    
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
    
    private void updateWeatherData() { presenter.updateWeatherData(); }
    
    // updateWeatherSnapshot moved to presenter.onWeatherSnapshotUpdated
    
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

        // Delegate to presenter/manager
        presenter.updateOverlayMode(h, t, legendPanel);
    }
    
    private void onTimeSliderChanged() {
        if (weatherDataManager.hasWeatherData()) {
            int idx = timeSelectorComponents.slider.getValue();
            // Actualizar snapshot de la estación activa
            weatherDataManager.updateWeatherAtIndex(idx);
            // Ajustar overlay de viento de TODAS las estaciones al periodo seleccionado
            weatherDataManager.updateWindDataAtIndexForStations(idx, windOverlayPanel);
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
