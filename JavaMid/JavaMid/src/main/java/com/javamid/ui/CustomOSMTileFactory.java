package com.javamid.ui;

import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Custom tile factory for OpenStreetMap that properly sets User-Agent
 */
public class CustomOSMTileFactory extends DefaultTileFactory {
    
    private static final String USER_AGENT = "WeatherMapApp/1.0 (Java)";
    
    public CustomOSMTileFactory(TileFactoryInfo info) {
        super(info);
    }
    
    protected InputStream getConnectionInputStream(URI uri) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.connect();
        return conn.getInputStream();
    }
}
