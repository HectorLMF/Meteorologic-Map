package com.javamid.ui;

import org.jxmapviewer.OSMTileFactoryInfo;

/**
 * TileFactoryInfo que logea todas las URLs de tiles solicitadas
 * y fuerza el uso de HTTPS contra tile.openstreetmap.org.
 */
public class LoggingOSMTileFactoryInfo extends OSMTileFactoryInfo {

    @Override
    public String getTileUrl(int x, int y, int zoom) {
        // Importante: usamos la lógica de OSMTileFactoryInfo para convertir el zoom interno
        // de JXMapViewer al zoom real de OpenStreetMap. Solo cambiamos el esquema a HTTPS.
        String url = super.getTileUrl(x, y, zoom);
        if (url != null && url.startsWith("http://")) {
            url = "https://" + url.substring("http://".length());
        }
        System.out.printf("[TileURL] z=%d x=%d y=%d -> %s%n", zoom, x, y, url);
        return url;
    }
}
