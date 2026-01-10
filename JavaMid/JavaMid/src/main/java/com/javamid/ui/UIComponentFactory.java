package com.javamid.ui;

import com.javamid.config.MapConfig;

import javax.swing.*;
import java.awt.*;

/**
 * Factory para crear componentes de UI de manera organizada y reutilizable.
 * Implementa el patrón Builder para construcción de paneles complejos.
 */
public class UIComponentFactory {
    
    /**
     * Crea el panel de control de partículas de viento.
     */
    public static JPanel createParticleControlPanel(WindOverlayPanel windOverlay) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Control de Partículas"));
        
        // Particle count slider
        JPanel countPanel = new JPanel(new BorderLayout(5, 0));
        JLabel countLabel = new JLabel("Cantidad: " + MapConfig.DEFAULT_PARTICLES_PER_STATION);
        JSlider countSlider = new JSlider(
            MapConfig.MIN_PARTICLES_PER_STATION, 
            MapConfig.MAX_PARTICLES_PER_STATION, 
            MapConfig.DEFAULT_PARTICLES_PER_STATION
        );
        countSlider.addChangeListener(e -> {
            int count = countSlider.getValue();
            countLabel.setText("Cantidad: " + count);
            windOverlay.setParticleCount(count);
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
            windOverlay.setSizeScale(scale);
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
            windOverlay.setSpeedScale(percent);
        });
        speedPanel.add(speedLabel, BorderLayout.NORTH);
        speedPanel.add(speedSlider, BorderLayout.CENTER);
        
        panel.add(countPanel);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        panel.add(sizePanel);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        panel.add(speedPanel);
        
        return panel;
    }
    
    /**
     * Crea el panel de capas (viento, precipitación, temperatura).
     */
    public static LayersPanelComponents createLayersPanel(WindOverlayPanel windOverlay,
                                                          HumidityOverlayPanel humidityOverlay,
                                                          Runnable onWindToggle,
                                                          Runnable onHumidityToggle) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Capas"));

        JToggleButton windButton = new JToggleButton("Viento");
        JToggleButton humidityButton = new JToggleButton("Humedad");
        JToggleButton temperatureButton = new JToggleButton("Temperatura");

        // Activar por defecto las capas principales para una experiencia coherente
        windButton.setSelected(true);
        humidityButton.setSelected(true);

        windButton.addActionListener(e -> {
            if (onWindToggle != null) {
                onWindToggle.run();
            }
        });
        
        humidityButton.addActionListener(e -> {
            if (onHumidityToggle != null) {
                onHumidityToggle.run();
            }
        });

        panel.add(windButton);
        panel.add(Box.createRigidArea(new Dimension(0, 6)));
        panel.add(humidityButton);
        panel.add(Box.createRigidArea(new Dimension(0, 6)));
        panel.add(temperatureButton);
        
        return new LayersPanelComponents(panel, windButton, humidityButton, temperatureButton);
    }
    
    /**
     * Crea el panel de información meteorológica (chivato inferior).
     */
    public static WeatherInfoPanelComponents createWeatherInfoPanel() {
        TranslucentPanel panel = new TranslucentPanel(new GridBagLayout());
        panel.setBackground(new Color(0, 0, 0, 180));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        panel.setPreferredSize(new Dimension(800, 100));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        
        // Panel de viento con brújula
        JPanel windPanel = new JPanel();
        windPanel.setOpaque(false);
        windPanel.setLayout(new BoxLayout(windPanel, BoxLayout.Y_AXIS));
        windPanel.setMaximumSize(new Dimension(100, 85));
        
        JLabel windLabel = new JLabel(String.format("Viento: %.0f°", MapConfig.DEFAULT_WIND_DIRECTION_DEG));
        windLabel.setForeground(Color.WHITE);
        windLabel.setFont(new Font("Arial", Font.BOLD, 13));
        windLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        WindCompassPanel compass = new WindCompassPanel();
        compass.setAlignmentX(Component.CENTER_ALIGNMENT);
        compass.setWindDirection(MapConfig.DEFAULT_WIND_DIRECTION_DEG);
        
        windPanel.add(windLabel);
        windPanel.add(Box.createVerticalStrut(3));
        windPanel.add(compass);
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(windPanel, gbc);
        
        // Temperatura
        JLabel temperatureLabel = new JLabel("Temp: --");
        temperatureLabel.setForeground(Color.WHITE);
        temperatureLabel.setFont(new Font("Arial", Font.BOLD, 14));
        temperatureLabel.setMinimumSize(new Dimension(100, 40));
        temperatureLabel.setPreferredSize(new Dimension(100, 40));
        temperatureLabel.setHorizontalAlignment(SwingConstants.CENTER);
        temperatureLabel.setVerticalAlignment(SwingConstants.CENTER);
        
        gbc.gridx = 1;
        gbc.gridy = 0;
        panel.add(temperatureLabel, gbc);
        
        // Humedad
        JLabel humidityLabel = new JLabel("Humedad: --%");
        humidityLabel.setForeground(Color.WHITE);
        humidityLabel.setFont(new Font("Arial", Font.BOLD, 14));
        humidityLabel.setMinimumSize(new Dimension(100, 40));
        humidityLabel.setPreferredSize(new Dimension(100, 40));
        humidityLabel.setHorizontalAlignment(SwingConstants.CENTER);
        humidityLabel.setVerticalAlignment(SwingConstants.CENTER);
        
        gbc.gridx = 2;
        gbc.gridy = 0;
        panel.add(humidityLabel, gbc);
        
        return new WeatherInfoPanelComponents(panel, windLabel, temperatureLabel, humidityLabel, compass);
    }
    
    /**
     * Crea el panel de selección de tiempo (slider temporal).
     */
    public static TimeSelectorPanelComponents createTimeSelectorPanel(Runnable onTimeChanged) {
        TranslucentPanel panel = new TranslucentPanel(new BorderLayout(10, 5));
        panel.setName("timeSelectorPanel");
        panel.setBackground(new Color(0, 0, 0, 180));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));
        
        JLabel timeLabel = new JLabel("Hora: --");
        timeLabel.setForeground(Color.WHITE);
        timeLabel.setFont(new Font("Arial", Font.BOLD, 12));
        
        JSlider timeSlider = new JSlider(0, 100, 0);
        timeSlider.setOpaque(false);
        timeSlider.setPreferredSize(new Dimension(400, 30));
        timeSlider.setEnabled(false);
        timeSlider.addChangeListener(e -> {
            if (!timeSlider.getValueIsAdjusting() && onTimeChanged != null) {
                onTimeChanged.run();
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
        
        JButton nextButton = new JButton("►");
        nextButton.setPreferredSize(new Dimension(40, 25));
        
        JButton latestButton = new JButton("Actual");
        latestButton.setPreferredSize(new Dimension(70, 25));
        
        timeButtonsPanel.add(prevButton);
        timeButtonsPanel.add(nextButton);
        timeButtonsPanel.add(latestButton);
        
        panel.add(timeControlPanel, BorderLayout.CENTER);
        panel.add(timeButtonsPanel, BorderLayout.EAST);
        panel.setVisible(false);
        
        return new TimeSelectorPanelComponents(panel, timeSlider, timeLabel, 
                                               prevButton, nextButton, latestButton);
    }
    
    /**
     * Crea el panel de control de radio de influencia.
     */
    public static InfluencePanelComponents createInfluenceControlPanel(
            StationMarkerPanel stationMarker, WindOverlayPanel windOverlay) {
        TranslucentPanel panel = new TranslucentPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        panel.setBackground(new Color(0, 0, 0, 180));
        
        JLabel label = new JLabel("Radio de influencia: " + (int)MapConfig.DEFAULT_INFLUENCE_RADIUS_KM + " km");
        label.setForeground(Color.WHITE);
        label.setFont(new Font("Arial", Font.BOLD, 12));
        
        JSlider slider = new JSlider(
            (int)MapConfig.MIN_INFLUENCE_RADIUS_KM, 
            (int)MapConfig.MAX_INFLUENCE_RADIUS_KM, 
            (int)MapConfig.DEFAULT_INFLUENCE_RADIUS_KM
        );
        slider.setPreferredSize(new Dimension(200, 30));
        slider.setOpaque(false);
        slider.addChangeListener(e -> {
            int radiusKm = slider.getValue();
            label.setText("Radio de influencia: " + radiusKm + " km");
            stationMarker.setInfluenceRadiusKm(radiusKm);
            windOverlay.setInfluenceRadiusKm(radiusKm);
        });
        
        panel.add(label);
        panel.add(slider);
        panel.setPreferredSize(new Dimension(300, 50));
        
        return new InfluencePanelComponents(panel, slider, label);
    }
    
    /**
     * Crea el panel de información de estación.
     */
    public static StationInfoPanelComponents createStationInfoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Estación Meteorológica"));
        
        JLabel stationLabel = new JLabel("Buscando estación...");
        stationLabel.setFont(new Font("Arial", Font.BOLD, 14));
        
        JLabel coordsLabel = new JLabel("Coordenadas: -");
        coordsLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        
        JTextArea stationDataArea = new JTextArea(5, 30);
        stationDataArea.setEditable(false);
        stationDataArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        stationDataArea.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        
        panel.add(stationLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 5)));
        panel.add(coordsLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        panel.add(stationDataArea);
        
        return new StationInfoPanelComponents(panel, stationLabel, coordsLabel, stationDataArea);
    }
    
    // === Clases de componentes agrupados ===
    
    public static class LayersPanelComponents {
        public final JPanel panel;
        public final JToggleButton windButton;
        public final JToggleButton humidityButton;
        public final JToggleButton temperatureButton;
        
        public LayersPanelComponents(JPanel panel, JToggleButton wind,
                                    JToggleButton humidity, JToggleButton temperature) {
            this.panel = panel;
            this.windButton = wind;
            this.humidityButton = humidity;
            this.temperatureButton = temperature;
        }
    }
    
    public static class WeatherInfoPanelComponents {
        public final JPanel panel;
        public final JLabel windLabel;
        public final JLabel temperatureLabel;
        public final JLabel humidityLabel;
        public final WindCompassPanel windCompass;
        
        public WeatherInfoPanelComponents(JPanel panel, JLabel wind, 
                                         JLabel temperature, JLabel humidity, WindCompassPanel compass) {
            this.panel = panel;
            this.windLabel = wind;
            this.temperatureLabel = temperature;
            this.humidityLabel = humidity;
            this.windCompass = compass;
        }
    }
    
    public static class TimeSelectorPanelComponents {
        public final JPanel panel;
        public final JSlider slider;
        public final JLabel label;
        public final JButton prevButton;
        public final JButton nextButton;
        public final JButton latestButton;
        
        public TimeSelectorPanelComponents(JPanel panel, JSlider slider, JLabel label,
                                          JButton prev, JButton next, JButton latest) {
            this.panel = panel;
            this.slider = slider;
            this.label = label;
            this.prevButton = prev;
            this.nextButton = next;
            this.latestButton = latest;
        }
    }
    
    public static class InfluencePanelComponents {
        public final JPanel panel;
        public final JSlider slider;
        public final JLabel label;
        
        public InfluencePanelComponents(JPanel panel, JSlider slider, JLabel label) {
            this.panel = panel;
            this.slider = slider;
            this.label = label;
        }
    }
    
    public static class StationInfoPanelComponents {
        public final JPanel panel;
        public final JLabel stationLabel;
        public final JLabel coordsLabel;
        public final JTextArea dataArea;
        
        public StationInfoPanelComponents(JPanel panel, JLabel stationLabel, 
                                         JLabel coordsLabel, JTextArea dataArea) {
            this.panel = panel;
            this.stationLabel = stationLabel;
            this.coordsLabel = coordsLabel;
            this.dataArea = dataArea;
        }
    }
}
