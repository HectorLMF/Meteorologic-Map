package com.javamid.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Panel con fondo translúcido fiable sobre componentes como mapas o capas.
 * Usa pintura personalizada en lugar de setOpaque(true) con alpha,
 * evitando artefactos visuales detrás de los textos.
 */
public class TranslucentPanel extends JPanel {

    private int cornerArc = 8;

    public TranslucentPanel() {
        super();
        setOpaque(false);
        setDoubleBuffered(true);
    }

    public TranslucentPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setDoubleBuffered(true);
    }

    public void setCornerArc(int arc) {
        this.cornerArc = Math.max(0, arc);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Pintar fondo translúcido manualmente para evitar artefactos
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color bg = getBackground();
        if (bg != null && bg.getAlpha() > 0) {
            g2.setComposite(AlphaComposite.SrcOver);
            if (cornerArc > 0) {
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), cornerArc, cornerArc);
            } else {
                g2.setColor(bg);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
