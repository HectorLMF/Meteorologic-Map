package com.javamid.ui;

import com.javamid.client.OpenMeteoClient;
import com.javamid.model.WeatherStation;
import com.javamid.service.WeatherStationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.*;
import org.jxmapviewer.input.*;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main GUI window with OpenStreetMap viewer
 * Finds nearest weather station when map is panned/scrolled
 */
public class WeatherMapWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(WeatherMapWindow.class.getName());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    
    private final JXMapViewer mapViewer;
    private final transient WeatherStationService stationService;
    private final transient OpenMeteoClient openMeteoClient;
    
    private final JPanel infoPanel;
    private final JLabel stationLabel;
    private final JLabel coordsLabel;
    private final JTextArea weatherDataArea;
    private final JTextArea stationDataArea;

    private final JToggleButton windLayerButton;
    private final JToggleButton precipitationLayerButton;
    private final JToggleButton temperatureLayerButton;
    
    private transient WeatherStation currentStation;
    private transient java.util.List<WeatherStation> visibleStations = new java.util.ArrayList<>();
    private transient java.util.Set<String> coveredAreas = new java.util.HashSet<>(); // Areas ya cubiertas con estaciones
    private boolean stationLookupInFlight = false;
    private boolean weatherFetchInFlight = false;
    // Wind overlay panel and last known wind
    private transient WindOverlayPanel windOverlayPanel;
    private transient StationMarkerPanel stationMarkerPanel;
    private volatile double lastWindSpeed = 5.0; // Default: 5 m/s
    private volatile double lastWindDeg = 90.0; // Default: East (90°)
    
    // Weather info display labels
    private JLabel windDirectionLabel;
    private JLabel temperatureLabel;
    private JLabel humidityLabel;
    
    // Time selector for historical data
    private JSlider timeSlider;
    private JLabel timeLabel;
    private transient JsonNode currentWeatherData; // Almacena la respuesta completa del API
    private int currentTimeIndex = -1; // Índice actual en el array hourly

    private final Timer mapMoveDebounceTimer;
    private static final int MAP_MOVE_DEBOUNCE_MS = 350;
    
    public WeatherMapWindow() {
        super("Weather Station Map Viewer");
        // Set global User-Agent for HTTP requests (needed by OpenStreetMap)
        System.setProperty("http.agent", "JavaMidWeatherMap/1.0 (educational use)");
        
        // Initialize services
        stationService = null;
        openMeteoClient = new OpenMeteoClient();
        
        setSize(1200, 800);
        setMinimumSize(new Dimension(800, 600));
        
        // Create map viewer
        mapViewer = new JXMapViewer();

        // Debounce for map movement to avoid spamming external APIs while panning/zooming
        mapMoveDebounceTimer = new Timer(MAP_MOVE_DEBOUNCE_MS, e -> onMapMoved());
        mapMoveDebounceTimer.setRepeats(false);
        
        // Setup OpenStreetMap tile factory with logging of tile URLs
        TileFactoryInfo info = new LoggingOSMTileFactoryInfo();
        DefaultTileFactory tileFactory = new DefaultTileFactory(info);
        tileFactory.setThreadPoolSize(4);
        // User-Agent descriptivo recomendado por OpenStreetMap policy
        tileFactory.setUserAgent("JavaMidWeatherMap/1.0 (educational; contact: you@example.com)");
        mapViewer.setTileFactory(tileFactory);
        
        // Set initial position (Madrid, Spain as example)
        GeoPosition initialPos = new GeoPosition(40.4168, -3.7038);
        mapViewer.setZoom(5);
        mapViewer.setAddressLocation(initialPos);
        
        // Add mouse interaction for pan and zoom
        PanMouseInputListener panListener = new PanMouseInputListener(mapViewer);
        mapViewer.addMouseListener(panListener);
        mapViewer.addMouseMotionListener(panListener);
        
        ZoomMouseWheelListenerCursor zoomListener = new ZoomMouseWheelListenerCursor(mapViewer);
        mapViewer.addMouseWheelListener(zoomListener);
        
        // Create wind overlay panel (transparent, sits on top of map)
        windOverlayPanel = new WindOverlayPanel(mapViewer);
        
        // Create station marker panel (transparent, sits on top of map)
        stationMarkerPanel = new StationMarkerPanel(mapViewer);
        stationMarkerPanel.setOnStationSelected(this::onStationSelected);
        
        // Use JLayeredPane to properly stack components
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setLayout(null); // Use absolute positioning for layers
        
        // Add map viewer at base layer
        mapViewer.setBounds(0, 0, 850, 800);
        layeredPane.add(mapViewer, JLayeredPane.DEFAULT_LAYER);
        
        // Add wind overlay on top (use highest layer to ensure visibility)
        windOverlayPanel.setBounds(0, 0, 850, 800);
        layeredPane.add(windOverlayPanel, Integer.valueOf(1000));
        
        // Add station marker on top of wind overlay
        stationMarkerPanel.setBounds(0, 0, 850, 800);
        layeredPane.add(stationMarkerPanel, Integer.valueOf(2000));
        
        // Create influence radius control panel at bottom of map (before weather info panel)
        JPanel influenceControlPanel = new JPanel();
        influenceControlPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 5));
        influenceControlPanel.setBackground(new Color(0, 0, 0, 180)); // Semi-transparent black
        influenceControlPanel.setOpaque(true);
        
        JLabel influenceLabel = new JLabel("Radio de influencia: 5 km");
        influenceLabel.setForeground(Color.WHITE);
        influenceLabel.setFont(new Font("Arial", Font.BOLD, 12));
        
        JSlider influenceSlider = new JSlider(5, 40, 5);
        influenceSlider.setPreferredSize(new Dimension(200, 30));
        influenceSlider.setOpaque(false);
        influenceSlider.addChangeListener(e -> {
            int radiusKm = influenceSlider.getValue();
            influenceLabel.setText("Radio de influencia: " + radiusKm + " km");
            stationMarkerPanel.setInfluenceRadiusKm(radiusKm);
            windOverlayPanel.setInfluenceRadiusKm(radiusKm);
        });
        
        influenceControlPanel.add(influenceLabel);
        influenceControlPanel.add(influenceSlider);
        
        // Set initial bounds for influence control panel
        influenceControlPanel.setPreferredSize(new Dimension(300, 50));
        influenceControlPanel.setBounds(275, 740, 300, 50);
        influenceControlPanel.setVisible(true);
        
        // Add influence control panel to layered pane
        layeredPane.add(influenceControlPanel, Integer.valueOf(3000));
        
        System.out.println("[INIT] Influence panel added at: 275, 740, 300x50");
        
        // Listen to layeredPane size changes to resize all overlays
        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = layeredPane.getWidth();
                int h = layeredPane.getHeight();
                
                // Resize all layers to fill the available space
                mapViewer.setBounds(0, 0, w, h);
                windOverlayPanel.setBounds(0, 0, w, h);
                stationMarkerPanel.setBounds(0, 0, w, h);
                windOverlayPanel.onMapChanged();
                stationMarkerPanel.repaint();
                
                // Position influence control panel at bottom center
                int influenceX = (w - 300) / 2;
                int influenceY = h - 60;
                influenceControlPanel.setBounds(influenceX, influenceY, 300, 50);
                System.out.println("[RESIZE] Map and overlays resized to: " + w + "x" + h + ", Influence panel at: " + influenceX + ", " + influenceY);
            }
        });
        
        // Listen to map pan/zoom changes to update particles
        mapViewer.addPropertyChangeListener("zoom", evt -> {
            windOverlayPanel.onMapChanged();
            stationMarkerPanel.repaint();
        });
        
        mapViewer.addPropertyChangeListener("center", evt -> {
            windOverlayPanel.onMapChanged();
            stationMarkerPanel.repaint();
        });
        
        // Create info panel
        infoPanel = new JPanel();
        infoPanel.setLayout(new BorderLayout());
        infoPanel.setPreferredSize(new Dimension(350, 600)); // Responsive height
        infoPanel.setMinimumSize(new Dimension(300, 400));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Station info panel
        JPanel stationInfoPanel = new JPanel();
        stationInfoPanel.setLayout(new BoxLayout(stationInfoPanel, BoxLayout.Y_AXIS));
        stationInfoPanel.setBorder(BorderFactory.createTitledBorder("Estación Meteorológica"));
        
        stationLabel = new JLabel("Buscando estación...");
        stationLabel.setFont(new Font("Arial", Font.BOLD, 14));
        
        coordsLabel = new JLabel("Coordenadas: -");
        coordsLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        
        stationInfoPanel.add(stationLabel);
        stationInfoPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        stationInfoPanel.add(coordsLabel);
        
        // Station data text area
        stationDataArea = new JTextArea(5, 30);
        stationDataArea.setEditable(false);
        stationDataArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        stationDataArea.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        stationInfoPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        stationInfoPanel.add(stationDataArea);
        
        // Weather data panel
        JPanel weatherPanel = new JPanel(new BorderLayout());
        weatherPanel.setBorder(BorderFactory.createTitledBorder("Datos Meteorológicos"));
        
        weatherDataArea = new JTextArea(20, 30);
        weatherDataArea.setEditable(false);
        weatherDataArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(weatherDataArea);
        
        weatherPanel.add(scrollPane, BorderLayout.CENTER);

        // Info panel about data source
        JPanel infoSourcePanel = new JPanel();
        infoSourcePanel.setBorder(BorderFactory.createTitledBorder("Fuente de Datos"));
        JLabel sourceLabel = new JLabel("<html><b>Open-Meteo Historical Weather API</b><br/>Datos históricos gratuitos sin necesidad de API key</html>");
        infoSourcePanel.add(sourceLabel);

        // Layers panel
        JPanel layersPanel = new JPanel();
        layersPanel.setLayout(new BoxLayout(layersPanel, BoxLayout.Y_AXIS));
        layersPanel.setBorder(BorderFactory.createTitledBorder("Capas"));

        windLayerButton = new JToggleButton("Viento");
        precipitationLayerButton = new JToggleButton("Precipitación");
        temperatureLayerButton = new JToggleButton("Temperatura");

        windLayerButton.addActionListener(e -> {
            if (windLayerButton.isSelected()) {
                windOverlayPanel.setWind(lastWindSpeed, lastWindDeg);
                windOverlayPanel.startAnimation();
            } else {
                windOverlayPanel.stopAnimation();
            }
        });

        layersPanel.add(windLayerButton);
        layersPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        layersPanel.add(precipitationLayerButton);
        layersPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        layersPanel.add(temperatureLayerButton);
        
        // Wind particle controls panel
        JPanel particleControlsPanel = new JPanel();
        particleControlsPanel.setLayout(new BoxLayout(particleControlsPanel, BoxLayout.Y_AXIS));
        particleControlsPanel.setBorder(BorderFactory.createTitledBorder("Control de Partículas"));
        
        // Particle count slider
        JPanel countPanel = new JPanel(new BorderLayout(5, 0));
        JLabel countLabel = new JLabel("Cantidad: 120");
        JSlider countSlider = new JSlider(10, 500, 120);
        countSlider.addChangeListener(e -> {
            int count = countSlider.getValue();
            countLabel.setText("Cantidad: " + count);
            windOverlayPanel.setParticleCount(count);
        });
        countPanel.add(countLabel, BorderLayout.NORTH);
        countPanel.add(countSlider, BorderLayout.CENTER);
        
        // Size scale slider
        JPanel sizePanel = new JPanel(new BorderLayout(5, 0));
        JLabel sizeLabel = new JLabel("Tamaño: 1.0x");
        JSlider sizeSlider = new JSlider(10, 300, 100);
        sizeSlider.addChangeListener(e -> {
            float scale = sizeSlider.getValue() / 100f;
            sizeLabel.setText(String.format("Tamaño: %.1fx", scale));
            windOverlayPanel.setSizeScale(scale);
        });
        sizePanel.add(sizeLabel, BorderLayout.NORTH);
        sizePanel.add(sizeSlider, BorderLayout.CENTER);
        
        // Speed scale slider
        JPanel speedPanel = new JPanel(new BorderLayout(5, 0));
        JLabel speedLabel = new JLabel("Velocidad: 50%");
        JSlider speedSlider = new JSlider(1, 100, 50);
        speedSlider.addChangeListener(e -> {
            int percent = speedSlider.getValue();
            speedLabel.setText(String.format("Velocidad: %d%%", percent));
            windOverlayPanel.setSpeedScale(percent); // Pass direct value
        });
        speedPanel.add(speedLabel, BorderLayout.NORTH);
        speedPanel.add(speedSlider, BorderLayout.CENTER);
        
        particleControlsPanel.add(countPanel);
        particleControlsPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        particleControlsPanel.add(sizePanel);
        particleControlsPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        particleControlsPanel.add(speedPanel);
        
        // Refresh button
        JButton refreshButton = new JButton("Actualizar Datos");
        refreshButton.addActionListener(e -> updateWeatherData());
        
        // Assemble info panel
        infoPanel.add(stationInfoPanel, BorderLayout.NORTH);
        infoPanel.add(weatherPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.add(infoSourcePanel);
        bottomPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        bottomPanel.add(layersPanel);
        bottomPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        bottomPanel.add(particleControlsPanel);
        bottomPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        bottomPanel.add(refreshButton);

        infoPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        // Create weather info panel (chivato) to display at top of map
        JPanel weatherInfoPanel = new JPanel();
        weatherInfoPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 5));
        weatherInfoPanel.setBackground(new Color(0, 0, 0, 180)); // Semi-transparent black
        weatherInfoPanel.setOpaque(true);
        
        windDirectionLabel = new JLabel("Viento: 90 E");
        windDirectionLabel.setForeground(Color.WHITE);
        windDirectionLabel.setFont(new Font("Arial", Font.BOLD, 14));
        
        temperatureLabel = new JLabel("Temp: --");
        temperatureLabel.setForeground(Color.WHITE);
        temperatureLabel.setFont(new Font("Arial", Font.BOLD, 14));
        
        humidityLabel = new JLabel("Humedad: --%");
        humidityLabel.setForeground(Color.WHITE);
        humidityLabel.setFont(new Font("Arial", Font.BOLD, 14));
        
        weatherInfoPanel.add(windDirectionLabel);
        weatherInfoPanel.add(temperatureLabel);
        weatherInfoPanel.add(humidityLabel);
        
        // Create time selector panel (initially hidden)
        JPanel timeSelectorPanel = new JPanel();
        timeSelectorPanel.setName("timeSelectorPanel");
        timeSelectorPanel.setLayout(new BorderLayout(10, 5));
        timeSelectorPanel.setBackground(new Color(0, 0, 0, 180));
        timeSelectorPanel.setOpaque(true);
        timeSelectorPanel.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));
        
        timeLabel = new JLabel("Hora: --");
        timeLabel.setForeground(Color.WHITE);
        timeLabel.setFont(new Font("Arial", Font.BOLD, 12));
        
        timeSlider = new JSlider(0, 100, 0);
        timeSlider.setOpaque(false);
        timeSlider.setPreferredSize(new Dimension(400, 30));
        timeSlider.setEnabled(false);
        timeSlider.addChangeListener(e -> {
            if (!timeSlider.getValueIsAdjusting() && currentWeatherData != null) {
                updateWeatherDataAtIndex(timeSlider.getValue());
            }
        });
        
        JPanel timeControlPanel = new JPanel(new BorderLayout(5, 0));
        timeControlPanel.setOpaque(false);
        timeControlPanel.add(timeLabel, BorderLayout.WEST);
        timeControlPanel.add(timeSlider, BorderLayout.CENTER);
        
        JPanel timeButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        timeButtonsPanel.setOpaque(false);
        
        JButton prevButton = new JButton("◄");
        prevButton.setPreferredSize(new Dimension(40, 25));
        prevButton.addActionListener(e -> {
            if (timeSlider.isEnabled() && timeSlider.getValue() > timeSlider.getMinimum()) {
                timeSlider.setValue(timeSlider.getValue() - 1);
                updateWeatherDataAtIndex(timeSlider.getValue());
            }
        });
        
        JButton nextButton = new JButton("►");
        nextButton.setPreferredSize(new Dimension(40, 25));
        nextButton.addActionListener(e -> {
            if (timeSlider.isEnabled() && timeSlider.getValue() < timeSlider.getMaximum()) {
                timeSlider.setValue(timeSlider.getValue() + 1);
                updateWeatherDataAtIndex(timeSlider.getValue());
            }
        });
        
        JButton latestButton = new JButton("Actual");
        latestButton.setPreferredSize(new Dimension(70, 25));
        latestButton.addActionListener(e -> {
            if (timeSlider.isEnabled()) {
                timeSlider.setValue(timeSlider.getMaximum());
                updateWeatherDataAtIndex(timeSlider.getMaximum());
            }
        });
        
        timeButtonsPanel.add(prevButton);
        timeButtonsPanel.add(nextButton);
        timeButtonsPanel.add(latestButton);
        
        timeSelectorPanel.add(timeControlPanel, BorderLayout.CENTER);
        timeSelectorPanel.add(timeButtonsPanel, BorderLayout.EAST);
        timeSelectorPanel.setVisible(false); // Initially hidden
        
        // Create a combined panel for weather info and time selector
        JPanel topInfoPanel = new JPanel();
        topInfoPanel.setLayout(new BoxLayout(topInfoPanel, BoxLayout.Y_AXIS));
        topInfoPanel.setOpaque(false);
        topInfoPanel.add(weatherInfoPanel);
        topInfoPanel.add(timeSelectorPanel);
        
        // Create a container panel for map + weather info
        JPanel mapContainer = new JPanel(new BorderLayout());
        mapContainer.add(topInfoPanel, BorderLayout.NORTH);
        mapContainer.add(layeredPane, BorderLayout.CENTER);
        
        // Add components to frame
        add(mapContainer, BorderLayout.CENTER);
        add(infoPanel, BorderLayout.EAST);
        
        // Add map movement listener
        mapViewer.addPropertyChangeListener("center", (PropertyChangeListener) evt -> scheduleMapMoved());
        
        // Add zoom listener
        mapViewer.addPropertyChangeListener("zoom", (PropertyChangeListener) evt -> scheduleMapMoved());
        
        // Initial station search
        SwingUtilities.invokeLater(this::scheduleMapMoved);
    }

    private void scheduleMapMoved() {
        mapMoveDebounceTimer.restart();
    }
    
    /**
     * Called when the map is panned or zoomed
     */
    private void onMapMoved() {
        // Get center coordinates
        GeoPosition center = mapViewer.getCenterPosition();
        double lat = center.getLatitude();
        double lon = center.getLongitude();
        
        coordsLabel.setText(String.format("Centro: %.4f, %.4f", lat, lon));
        
        // Generar ID del área actual (grid de ~50km)
        String areaId = getAreaId(lat, lon, 50.0);
        
        // Si esta área no ha sido cubierta, generar nuevas estaciones
        if (!coveredAreas.contains(areaId) && !stationLookupInFlight) {
            coveredAreas.add(areaId);
            startVisibleStationsLookup(lat, lon);
        }
    }
    
    /**
     * Genera un ID único para un área geográfica basado en un grid
     */
    private String getAreaId(double lat, double lon, double gridSizeKm) {
        // Convertir km a grados aproximadamente (1 grado ≈ 111 km)
        double gridDegrees = gridSizeKm / 111.0;
        
        // Redondear coordenadas al grid más cercano
        long latGrid = Math.round(lat / gridDegrees);
        long lonGrid = Math.round(lon / gridDegrees);
        
        return latGrid + "," + lonGrid;
    }

    private void startVisibleStationsLookup(double lat, double lon) {
        SwingWorker<java.util.List<WeatherStation>, Void> worker = new SwingWorker<java.util.List<WeatherStation>, Void>() {
            @Override
            protected java.util.List<WeatherStation> doInBackground() {
                stationLookupInFlight = true;
                try {
                    // Generar puntos virtuales aleatorios alrededor del centro
                    // 200 puntos en un radio de 150km para máxima cobertura
                    java.util.List<OpenMeteoClient.VirtualStation> virtualStations = 
                        openMeteoClient.generateNearbyPoints(lat, lon, 150.0, 200);
                    
                    // Convertir a WeatherStation
                    java.util.List<WeatherStation> stations = new java.util.ArrayList<>();
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
                    return new java.util.ArrayList<>();
                }
            }

            @Override
            protected void done() {
                try {
                    java.util.List<WeatherStation> newStations = get();
                    
                    // Agregar nuevas estaciones sin duplicar
                    for (WeatherStation newStation : newStations) {
                        boolean isDuplicate = false;
                        for (WeatherStation existing : visibleStations) {
                            // Verificar si ya existe una estación muy cerca (< 1km)
                            double distance = calculateDistance(
                                existing.getLatitude(), existing.getLongitude(),
                                newStation.getLatitude(), newStation.getLongitude()
                            );
                            if (distance < 1.0) {
                                isDuplicate = true;
                                break;
                            }
                        }
                        
                        if (!isDuplicate) {
                            visibleStations.add(newStation);
                        }
                    }
                    
                    System.out.println("[STATIONS] Total stations: " + visibleStations.size() + " (added " + newStations.size() + " new)");
                    onVisibleStationsLoaded(visibleStations);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    stationLabel.setText("Búsqueda interrumpida");
                } catch (Exception e) {
                    stationLabel.setText("Error al generar estaciones");
                    LOGGER.log(Level.WARNING, "Error while loading visible stations", e);
                } finally {
                    stationLookupInFlight = false;
                }
            }
        };

        worker.execute();
    }
    
    /**
     * Calcula distancia en km entre dos puntos (fórmula simplificada)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371 * c; // Radio de la Tierra en km
    }

    private void onVisibleStationsLoaded(java.util.List<WeatherStation> stations) {
        if (stations == null || stations.isEmpty()) {
            stationLabel.setText("No se encontraron estaciones cercanas");
            stationDataArea.setText("");
            weatherDataArea.setText("");
            stationMarkerPanel.clearStations();
            windOverlayPanel.setStations(new java.util.ArrayList<>());
            return;
        }

        // Actualizar el panel de marcadores con todas las estaciones
        stationMarkerPanel.setStations(stations);
        
        // Actualizar el wind overlay con las estaciones
        windOverlayPanel.setStations(stations);
        
        // La estación activa se selecciona automáticamente (la más cercana al centro)
        WeatherStation activeStation = stationMarkerPanel.getActiveStation();
        if (activeStation != null) {
            onStationSelected(activeStation);
        }
    }
    
    private void onStationSelected(WeatherStation station) {
        if (station == null) {
            return;
        }

        currentStation = station;
        Double km = station.getDistanceKm();
        String distanceSuffix = (km == null) ? "" : String.format(" - %.1f km", km);
        String country = station.getCountry() == null ? "N/A" : station.getCountry();
        String source = station.getSource() == null ? "" : (" - " + station.getSource());
        stationLabel.setText("<html><b>" + station.getName() + "</b> (" + country + ")" + distanceSuffix + source + "</html>");
        displayStationData(station);
        updateWeatherData();
    }
    
    /**
     * Display station data in the station text area
     */
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
        
        stationDataArea.setText(sb.toString());
    }
    
    /**
     * Fetch and display weather data for current station
     */
    private void updateWeatherData() {
        if (currentStation == null) {
            return;
        }

        if (weatherFetchInFlight) {
            return;
        }

        weatherFetchInFlight = true;
        weatherDataArea.setText("Cargando datos meteorológicos...");
        
        SwingWorker<JsonNode, Void> worker = new SwingWorker<JsonNode, Void>() {
            @Override
            protected JsonNode doInBackground() {
                // Fetch weather for current station AND all visible stations for wind data
                JsonNode currentResponse = fetchWeatherForCurrentStation();
                
                // Load wind data for all visible stations in background
                fetchWindDataForAllStations();
                
                return currentResponse;
            }
            
            @Override
            protected void done() {
                try {
                    JsonNode response = get();
                    weatherDataArea.setText(prettyJson(response));
                    
                    // Debug: print JSON structure
                    System.out.println("[WEATHER] ========== RAW DATA ==========");
                    System.out.println(prettyJson(response));
                    System.out.println("[WEATHER] ================================");
                    
                    // Extract weather info from Open-Meteo response
                    // Structure: { 
                    //   "latitude": 52.52, "longitude": 13.41,
                    //   "hourly": {
                    //     "time": ["2024-01-01T00:00", "2024-01-01T01:00", ...],
                    //     "temperature_2m": [10.5, 11.2, ...],
                    //     "relative_humidity_2m": [85, 83, ...],
                    //     "wind_speed_10m": [12.5, 13.2, ...],
                    //     "wind_direction_10m": [180, 185, ...]
                    //   }
                    // }
                    if (response != null && response.has("hourly")) {
                        JsonNode hourly = response.get("hourly");
                        if (hourly.has("time")) {
                            JsonNode timeArray = hourly.get("time");
                            int lastIndex = timeArray.size() - 1;
                            
                            // Almacenar la respuesta completa para el selector de hora
                            currentWeatherData = response;
                            currentTimeIndex = lastIndex;
                            
                            // Configurar el slider con el rango correcto
                            timeSlider.setMinimum(0);
                            timeSlider.setMaximum(lastIndex);
                            timeSlider.setValue(lastIndex);
                            timeSlider.setEnabled(true);
                            
                            // Mostrar el panel del selector si hay datos
                            Component timeSelectorPanel = findTimeSelectorPanel();
                            if (timeSelectorPanel != null) {
                                timeSelectorPanel.setVisible(true);
                            }
                            
                            // Actualizar los datos con el último índice
                            updateWeatherDataAtIndex(lastIndex);
                            // Actualizar los datos con el último índice
                            updateWeatherDataAtIndex(lastIndex);
                        } else {
                            System.out.println("[WEATHER] No 'time' field in hourly data");
                            temperatureLabel.setText("Temp: N/A");
                            humidityLabel.setText("Humedad: N/A");
                            updateWindDirectionLabel(0);
                        }
                    } else {
                        System.out.println("[WEATHER] No 'hourly' field in response");
                        temperatureLabel.setText("Temp: N/A");
                        humidityLabel.setText("Humedad: N/A");
                        updateWindDirectionLabel(0);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    weatherDataArea.setText("Actualización interrumpida");
                } catch (Exception e) {
                    weatherDataArea.setText("Error al obtener datos:\n" + e.getMessage());
                    LOGGER.log(Level.WARNING, "Error while fetching weather data", e);
                } finally {
                    weatherFetchInFlight = false;
                }
            }
        };
        
        worker.execute();
    }

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
    
    private void updateWindDirectionLabel(double deg) {
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = (int) Math.round(((deg % 360) / 45.0)) % 8;
        String direction = directions[index];
        
        String newText = String.format("Viento: %.0f %s (%.1f m/s)", deg, direction, lastWindSpeed);
        System.out.println("[UI] Updating wind label to: " + newText);
        windDirectionLabel.setText(newText);
        windDirectionLabel.repaint();
    }

    private JsonNode fetchWeatherForCurrentStation() {
        // Usar Open-Meteo para todos los puntos virtuales
        LocalDate end = LocalDate.now().minusDays(5); // 5-day delay for historical data
        LocalDate start = end.minusDays(7); // Get last week of data
        return openMeteoClient.getHistoricalWeather(
            currentStation.getLatitude(), 
            currentStation.getLongitude(), 
            start, 
            end
        );
    }
    
    /**
     * Fetch wind data for visible stations ONLY to populate particles efficiently
     * Límite: máximo 10 estaciones para evitar degradación de performance
     */
    private void fetchWindDataForAllStations() {
        java.util.List<WeatherStation> allStations = stationMarkerPanel.getStations();
        if (allStations == null || allStations.isEmpty()) {
            return;
        }
        
        // OPTIMIZACIÓN: Solo cargar datos para estaciones visibles (en pantalla)
        // y limitar a máximo 10 para evitar sobrecargar con requests
        java.util.List<WeatherStation> visibleStations = windOverlayPanel.getVisibleStations();
        
        if (visibleStations == null || visibleStations.isEmpty()) {
            // Si no hay estaciones visibles, usar las primeras 5 de todas
            visibleStations = allStations.stream()
                .limit(5)
                .collect(java.util.stream.Collectors.toList());
        } else {
            // Limitar a máximo 10 estaciones visibles
            visibleStations = visibleStations.stream()
                .limit(10)
                .collect(java.util.stream.Collectors.toList());
        }
        
        System.out.println("[WEATHER] Cargando datos de viento para " + visibleStations.size() + 
            " estaciones visibles (de " + allStations.size() + " totales)");
        
        LocalDate end = LocalDate.now().minusDays(5); // 5-day delay for historical data
        LocalDate start = end.minusDays(7); // Get last week of data
        
        // Ejecutar en background con ExecutorService
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        
        int successCount = 0;
        int attemptCount = 0;
        for (WeatherStation station : visibleStations) {
            attemptCount++;
            final WeatherStation finalStation = station;
            
            // Enviar tarea al thread pool para no bloquear UI
            executor.execute(() -> {
                try {
                    JsonNode response = openMeteoClient.getHistoricalWeather(
                        finalStation.getLatitude(), 
                        finalStation.getLongitude(), 
                        start, 
                        end
                    );
                    
                    // Extract wind data from response
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
                                    double speedMs = speedKmh / 3.6; // Convert km/h to m/s
                                    double deg = dirArray.get(lastIndex).asDouble();
                                    
                                    // Set wind data for this station
                                    windOverlayPanel.setStationWind(finalStation.getId(), speedMs, deg);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Skip stations with errors to avoid blocking
                    System.err.println("[WEATHER] Error al cargar datos para estación " + finalStation.getId() + ": " + e.getMessage());
                }
            });
        }
        
        // Shutdown executor después de enviar todas las tareas
        executor.shutdown();
        try {
            // Esperar máximo 30 segundos
            executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
            System.out.println("[WEATHER] Datos de viento cargados para " + attemptCount + " estaciones visibles");
        } catch (InterruptedException e) {
            System.out.println("[WEATHER] Timeout cargando datos de viento");
            executor.shutdownNow();
        }
    }

    // NOTE: We intentionally show raw (pretty) JSON in the weather text area.
    
    /**
     * Actualiza los datos meteorológicos mostrados para un índice específico del array hourly
     */
    private void updateWeatherDataAtIndex(int index) {
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
        
        // Actualizar label de tiempo
        String timeStr = timeArray.get(index).asText();
        timeLabel.setText("Hora: " + timeStr);
        
        System.out.println("[WEATHER] Mostrando datos del índice: " + index + " (" + timeStr + ")");
        
        // Extraer temperatura
        if (hourly.has("temperature_2m")) {
            JsonNode tempArray = hourly.get("temperature_2m");
            if (tempArray.size() > index && !tempArray.get(index).isNull()) {
                double temp = tempArray.get(index).asDouble();
                temperatureLabel.setText(String.format("Temp: %.1f°C", temp));
            } else {
                temperatureLabel.setText("Temp: N/A");
            }
        } else {
            temperatureLabel.setText("Temp: N/A");
        }
        
        // Extraer humedad
        if (hourly.has("relative_humidity_2m")) {
            JsonNode humArray = hourly.get("relative_humidity_2m");
            if (humArray.size() > index && !humArray.get(index).isNull()) {
                double humidity = humArray.get(index).asDouble();
                humidityLabel.setText(String.format("Humedad: %.0f%%", humidity));
            } else {
                humidityLabel.setText("Humedad: N/A");
            }
        } else {
            humidityLabel.setText("Humedad: N/A");
        }
        
        // Extraer datos de viento
        if (hourly.has("wind_speed_10m") && hourly.has("wind_direction_10m")) {
            JsonNode speedArray = hourly.get("wind_speed_10m");
            JsonNode dirArray = hourly.get("wind_direction_10m");
            
            if (speedArray.size() > index && !speedArray.get(index).isNull() &&
                dirArray.size() > index && !dirArray.get(index).isNull()) {
                double speedKmh = speedArray.get(index).asDouble();
                double speedMs = speedKmh / 3.6; // Convertir km/h a m/s
                double deg = dirArray.get(index).asDouble();
                
                lastWindSpeed = speedMs;
                lastWindDeg = deg;
                
                updateWindDirectionLabel(deg);
                
                // Actualizar datos de viento para la estación actual
                if (currentStation != null) {
                    windOverlayPanel.setStationWind(currentStation.getId(), speedMs, deg);
                }
                
                // Actualizar overlay de viento si está activo
                if (windOverlayPanel != null && windLayerButton.isSelected()) {
                    // Reiniciar animación para redibujar partículas con nuevos datos
                    windOverlayPanel.stopAnimation();
                    windOverlayPanel.setWind(lastWindSpeed, lastWindDeg);
                    windOverlayPanel.startAnimation();
                    System.out.println("[WEATHER] Partículas redibujadas para índice " + index + 
                        ": " + String.format("%.2f m/s a %.0f°", speedMs, deg));
                }
            } else {
                updateWindDirectionLabel(0);
            }
        } else {
            updateWindDirectionLabel(0);
        }
    }
    
    /**
     * Encuentra el panel del selector de tiempo en la jerarquía de componentes
     */
    private Component findTimeSelectorPanel() {
        // Buscar en el container principal
        Container contentPane = getContentPane();
        return findComponentByName(contentPane, "timeSelectorPanel");
    }
    
    /**
     * Método recursivo para buscar un componente por nombre
     */
    private Component findComponentByName(Container container, String name) {
        for (Component comp : container.getComponents()) {
            if (comp.getName() != null && comp.getName().equals(name)) {
                return comp;
            }
            if (comp instanceof Container) {
                Component found = findComponentByName((Container) comp, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
    
    /**
     * Launch the application
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            WeatherMapWindow window = new WeatherMapWindow();
            window.setLocationRelativeTo(null);
            window.setVisible(true);
        });
    }
}
