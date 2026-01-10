package com.javamid.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Widget de leyenda cromática para el modo activo (Humedad/Temp/Precipitación).
 */
public class LegendPanel extends JPanel {

    public enum Mode { HUMIDITY, TEMPERATURE, PRECIPITATION }

    private Mode mode = Mode.HUMIDITY;

    public LegendPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(160, 140));
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // fondo translúcido
        g2d.setColor(new Color(0,0,0,160));
        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);

        int padding = 10;
        int barWidth = 25;
        int barHeight = getHeight() - padding*3;
        int barX = padding;
        int barY = padding*2;

        // título
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        String title;
        switch (mode) {
            case HUMIDITY:
                title = "Humedad (%)";
                break;
            case TEMPERATURE:
                title = "Temperatura (°C)";
                break;
            case PRECIPITATION:
            default:
                title = "Precipitación (mm/h)";
                break;
        }
        g2d.drawString(title, padding, padding + 2);

        // barra de gradiente vertical
        for (int i = 0; i < barHeight; i++) {
            double t = 1.0 - (i / (double)barHeight); // top=max, bottom=min
            Color c;
            switch (mode) {
                case HUMIDITY:
                    c = humidityColor(t);
                    break;
                case TEMPERATURE:
                    c = temperatureColor(t);
                    break;
                case PRECIPITATION:
                default:
                    c = precipitationColor(t);
                    break;
            }
            g2d.setColor(c);
            g2d.drawLine(barX, barY + i, barX + barWidth, barY + i);
        }

        // etiquetas min/max
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.PLAIN, 11));
        String minLabel, maxLabel;
        switch (mode) {
            case HUMIDITY:
                minLabel = "0%"; maxLabel = "100%";
                break;
            case TEMPERATURE:
                minLabel = "-10"; maxLabel = "35";
                break;
            case PRECIPITATION:
            default:
                minLabel = "0"; maxLabel = "20";
                break;
        }
        g2d.drawString(maxLabel, barX + barWidth + 10, barY + 5);
        g2d.drawString(minLabel, barX + barWidth + 10, barY + barHeight - 5);

        g2d.dispose();
    }

    private Color humidityColor(double t) {
        // 0→transparente, 1→azul
        int alpha = (int)(20 + 60*t);
        return new Color(30, 144, 255, alpha);
    }

    private Color temperatureColor(double t) {
        // map t (0..1) to -10..35, reuse TemperatureOverlay scale
        double c = -10 + t * 45.0;
        // Approximate via bands
        if (c <= 0) {
            double k = (c + 10) / 10.0;
            return new Color((int)(0 + 55*k), (int)(80 + 175*k), 255);
        } else if (c <= 15) {
            double k = c / 15.0;
            return new Color((int)(55 - 55*k), (int)(255 - 55*k), (int)(255 - 255*k));
        } else if (c <= 25) {
            double k = (c - 15) / 10.0;
            return new Color((int)(0 + 255*k), 200, 0);
        } else {
            double k = (c - 25) / 10.0;
            return new Color(255, (int)(200 - 150*k), 0);
        }
    }

    private Color precipitationColor(double t) {
        // map t to 0..20 mm
        double p = t * 20.0;
        if (p <= 5) {
            double k = p / 5.0;
            return new Color((int)(180 - 50*k), (int)(220 - 30*k), 255, 180);
        } else if (p <= 10) {
            double k = (p - 5) / 5.0;
            return new Color((int)(130 - 20*k), (int)(190 - 50*k), (int)(255 - 30*k), 180);
        } else if (p <= 15) {
            double k = (p - 10) / 5.0;
            return new Color((int)(110 - 20*k), (int)(140 - 40*k), (int)(225 - 25*k), 180);
        } else {
            double k = (p - 15) / 5.0;
            return new Color((int)(90 - 20*k), (int)(100 - 20*k), (int)(200 - 50*k), 180);
        }
    }
}
