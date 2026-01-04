package com.javamid.ui;

import com.javamid.client.MeteostatApiClient;
import com.javamid.client.OpenWeatherApiClient;
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
    private final transient OpenWeatherApiClient weatherClient;
    private final transient MeteostatApiClient meteostatClient;
    
    private final JPanel infoPanel;
    private final JLabel stationLabel;
    private final JLabel coordsLabel;
    private final JTextArea weatherDataArea;
    private final JTextArea stationDataArea;
    
    private transient WeatherStation currentStation;
    private boolean stationLookupInFlight = false;
    private boolean weatherFetchInFlight = false;

    private final Timer mapMoveDebounceTimer;
    private static final int MAP_MOVE_DEBOUNCE_MS = 350;
    
    public WeatherMapWindow() {
        super("Weather Station Map Viewer");
        // Set global User-Agent for HTTP requests (needed by OpenStreetMap)
        System.setProperty("http.agent", "JavaMidWeatherMap/1.0 (educational use)");
        
        // Initialize services
        stationService = new WeatherStationService();
        weatherClient = new OpenWeatherApiClient();
        meteostatClient = new MeteostatApiClient();
        
        // Setup main frame
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setSize(1200, 800);
        
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
        
        // Create info panel
        infoPanel = new JPanel();
        infoPanel.setLayout(new BorderLayout());
        infoPanel.setPreferredSize(new Dimension(350, 800));
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
        
        // Refresh button
        JButton refreshButton = new JButton("Actualizar Datos");
        refreshButton.addActionListener(e -> updateWeatherData());
        
        // Assemble info panel
        infoPanel.add(stationInfoPanel, BorderLayout.NORTH);
        infoPanel.add(weatherPanel, BorderLayout.CENTER);
        infoPanel.add(refreshButton, BorderLayout.SOUTH);
        
        // Add components to frame
        add(mapViewer, BorderLayout.CENTER);
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
        if (stationLookupInFlight) {
            return;
        }
        
        // Get center coordinates
        GeoPosition center = mapViewer.getCenterPosition();
        double lat = center.getLatitude();
        double lon = center.getLongitude();
        
        coordsLabel.setText(String.format("Centro: %.4f, %.4f", lat, lon));
        
        startStationLookup(lat, lon);
    }

    private void startStationLookup(double lat, double lon) {
        SwingWorker<WeatherStation, Void> worker = new SwingWorker<WeatherStation, Void>() {
            @Override
            protected WeatherStation doInBackground() {
                stationLookupInFlight = true;
                return stationService.findNearestStation(lat, lon);
            }

            @Override
            protected void done() {
                try {
                    onStationLookupCompleted(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    stationLabel.setText("Búsqueda interrumpida");
                } catch (Exception e) {
                    stationLabel.setText("Error al buscar estación");
                    LOGGER.log(Level.WARNING, "Error while looking up station", e);
                } finally {
                    stationLookupInFlight = false;
                }
            }
        };

        worker.execute();
    }

    private void onStationLookupCompleted(WeatherStation station) {
        if (station == null) {
            stationLabel.setText("No se encontró estación cercana");
            stationDataArea.setText("");
            weatherDataArea.setText("");
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
                return fetchWeatherForCurrentStation();
            }
            
            @Override
            protected void done() {
                try {
                    JsonNode data = get();
                    weatherDataArea.setText(prettyJson(data));
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

    private JsonNode fetchWeatherForCurrentStation() {
        if ("meteostat".equalsIgnoreCase(currentStation.getSource())) {
            if (!meteostatClient.isConfigured()) {
                throw new IllegalStateException("Meteostat RapidAPI key missing. Set 'meteostat.rapidapi.key' in javamid.local.properties/application-local.properties or env METEOSTAT_RAPIDAPI_KEY/RAPIDAPI_KEY");
            }
            // Meteostat hourly has some delay; request last 2 days and display latest record available.
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(2);
            return meteostatClient.getStationHourly(currentStation.getId(), start, end, currentStation.getTimezone(), "metric");
        }

        // OpenWeather fallback: avoid ambiguity by using city-id when possible.
        if (currentStation.getId() != null && !currentStation.getId().isBlank()) {
            return weatherClient.getWeatherDataByCityId(currentStation.getId());
        }
        return weatherClient.getWeatherData(currentStation.getName());
    }

    // NOTE: We intentionally show raw (pretty) JSON in the weather text area.
    
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
