package com.javamid.flyweight;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Factory para estilos de marcadores usando patron Flyweight.
 * Reutiliza estilos compartidos entre marcadores.
 */
public class MarkerStyleFactory {
    private static final Map<String, MarkerStyle> styles = new HashMap<>();
    
    // Estilo para marcador activo
    private static final MarkerStyle ACTIVE_STYLE = new MarkerStyle(
        new Color(0, 255, 0, 80),      // outerColor - verde semi-transparente
        new Color(100, 255, 100, 120), // innerColor - verde claro
        new Color(255, 255, 255, 255), // markerColor - blanco solido
        new Color(0, 180, 0, 255),     // centerColor - verde
        50, 25, 10, 6, 3.0f            // radios y stroke
    );
    
    // Estilo para marcador inactivo
    private static final MarkerStyle INACTIVE_STYLE = new MarkerStyle(
        null,                          // outerColor - no se usa
        null,                          // innerColor - no se usa
        new Color(255, 255, 255, 255), // markerColor - blanco solido
        new Color(0, 150, 0, 255),     // centerColor - verde oscuro
        0, 0, 8, 5, 2.0f               // radios y stroke
    );
    
    // Estilo para cluster
    private static final MarkerStyle CLUSTER_STYLE = new MarkerStyle(
        new Color(50, 100, 200, 150),  // outerColor - azul para fondo cuadrado
        null,                          // innerColor - no se usa
        Color.BLACK,                   // markerColor - borde negro
        Color.WHITE,                   // centerColor - texto blanco
        40, 0, 0, 0, 2.0f              // size del cuadrado y stroke
    );
    
    static {
        styles.put("active", ACTIVE_STYLE);
        styles.put("inactive", INACTIVE_STYLE);
        styles.put("cluster", CLUSTER_STYLE);
    }
    
    public static MarkerStyle getActiveStyle() {
        return styles.get("active");
    }
    
    public static MarkerStyle getInactiveStyle() {
        return styles.get("inactive");
    }
    
    public static MarkerStyle getClusterStyle() {
        return styles.get("cluster");
    }
}
