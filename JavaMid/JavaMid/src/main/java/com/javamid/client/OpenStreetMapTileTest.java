package com.javamid.client;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class OpenStreetMapTileTest {
    public static void main(String[] args) throws Exception {
        String urlString = "http://tile.openstreetmap.org/12/2004/1545.png";
        System.out.println("Requesting: " + urlString);

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "JavaMidWeatherMapTest/1.0 (educational use)");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int code = conn.getResponseCode();
        System.out.println("HTTP status: " + code);
        System.out.println("Content-Type: " + conn.getContentType());
        System.out.println("Content-Length: " + conn.getContentLengthLong());

        if (code == 200) {
            try (InputStream in = conn.getInputStream()) {
                byte[] buffer = new byte[16];
                int read = in.read(buffer);
                System.out.println("Read first bytes: " + read);
            }
        } else {
            try (InputStream err = conn.getErrorStream()) {
                if (err != null) {
                    byte[] buffer = err.readAllBytes();
                    System.out.println("Error body: " + new String(buffer));
                }
            }
        }

        conn.disconnect();
    }
}
