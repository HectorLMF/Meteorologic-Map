package com.javamid.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * Panel que muestra una brújula visual de la dirección del viento.
 * La aguja roja apunta en la dirección del viento (grados meteorológicos).
 */
public class WindCompassPanel extends JPanel {
    
    private double windDirection = 0.0; // grados (0-360)
    private static final int COMPASS_SIZE = 80;
    
    public WindCompassPanel() {
        setPreferredSize(new Dimension(COMPASS_SIZE, COMPASS_SIZE));
        setMinimumSize(new Dimension(60, 60));
        setMaximumSize(new Dimension(120, 120));
        setOpaque(false);
    }
    
    public void setWindDirection(double degrees) {
        this.windDirection = degrees % 360;
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getWidth() <= 0 || getHeight() <= 0) return;
        
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int size = Math.min(getWidth(), getHeight());
        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;
        int radius = size / 2 - 2;
        
        // Dibujar círculo externo
        g2d.setColor(new Color(100, 100, 100, 150));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
        
        // Dibujar marcas cardinales (N, S, E, O)
        g2d.setColor(new Color(200, 200, 200, 200));
        g2d.setFont(new Font("Arial", Font.BOLD, 10));
        FontMetrics fm = g2d.getFontMetrics();
        
        // N (arriba)
        drawCardinal(g2d, centerX, centerY - radius - 8, "N", fm);
        // S (abajo)
        drawCardinal(g2d, centerX, centerY + radius + 8, "S", fm);
        // E (derecha)
        drawCardinal(g2d, centerX + radius + 8, centerY, "E", fm);
        // O (izquierda)
        drawCardinal(g2d, centerX - radius - 10, centerY, "O", fm);
        
        // Dibujar aguja del viento (rojo)
        drawWindArrow(g2d, centerX, centerY, radius - 5, windDirection);
        
        g2d.dispose();
    }
    
    private void drawCardinal(Graphics2D g2d, int x, int y, String letter, FontMetrics fm) {
        int strWidth = fm.stringWidth(letter);
        int strHeight = fm.getAscent();
        g2d.drawString(letter, x - strWidth / 2, y + strHeight / 2);
    }
    
    private void drawWindArrow(Graphics2D g2d, int centerX, int centerY, int length, double degrees) {
        // Convertir grados meteorológicos a radianes
        // En meteorología: 0° = N, 90° = E, 180° = S, 270° = O
        // En Java: 0° = E (derecha), 90° = S (abajo)
        // Conversión: java_angle = 90 - met_angle
        double javaAngle = Math.toRadians(90 - degrees);
        
        // Punto final de la aguja
        int endX = centerX + (int) (length * Math.cos(javaAngle));
        int endY = centerY - (int) (length * Math.sin(javaAngle));
        
        // Dibujar línea gruesa roja
        g2d.setColor(new Color(255, 50, 50, 200)); // Rojo brillante
        g2d.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(centerX, centerY, endX, endY);
        
        // Dibujar punta de flecha
        double arrowAngle = Math.PI / 6; // 30 grados
        int arrowLength = 10;
        
        // Línea izquierda de la flecha
        double angle1 = javaAngle + arrowAngle;
        int x1 = endX - (int) (arrowLength * Math.cos(angle1));
        int y1 = endY + (int) (arrowLength * Math.sin(angle1));
        
        // Línea derecha de la flecha
        double angle2 = javaAngle - arrowAngle;
        int x2 = endX - (int) (arrowLength * Math.cos(angle2));
        int y2 = endY + (int) (arrowLength * Math.sin(angle2));
        
        // Dibujar triángulo de la punta
        int[] xs = {endX, x1, x2};
        int[] ys = {endY, y1, y2};
        g2d.fillPolygon(xs, ys, 3);
        
        // Dibujar círculo en el centro
        g2d.setColor(new Color(255, 100, 100, 220));
        g2d.fillOval(centerX - 4, centerY - 4, 8, 8);
        g2d.setColor(new Color(150, 30, 30, 200));
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.drawOval(centerX - 4, centerY - 4, 8, 8);
    }
}
