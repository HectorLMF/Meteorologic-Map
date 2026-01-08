package com.javamid.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Client for Open-Meteo Historical Weather API
 * Free API, no key required
 * https://open-meteo.com/en/docs/historical-weather-api
 */
public class OpenMeteoClient {
    
    private static final String BASE_URL = "https://archive-api.open-meteo.com/v1/archive";
    private static final Logger LOGGER = Logger.getLogger(OpenMeteoClient.class.getName());
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Get historical weather data for a location
     */
    public JsonNode getHistoricalWeather(double lat, double lon, LocalDate startDate, LocalDate endDate) {
        StringBuilder url = new StringBuilder(BASE_URL);
        url.append("?latitude=").append(lat);
        url.append("&longitude=").append(lon);
        url.append("&start_date=").append(startDate);
        url.append("&end_date=").append(endDate);
        url.append("&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m,wind_direction_10m");
        url.append("&timezone=auto");
        
        return getJson(url.toString());
    }
    
    /**
     * Generate random virtual weather stations around a center point
     * Since Open-Meteo doesn't have physical stations, we create random points
     */
    public List<VirtualStation> generateNearbyPoints(double centerLat, double centerLon, double radiusKm, int count) {
        List<VirtualStation> points = new ArrayList<>();
        
        // Calculate degree offset for the radius
        double kmPerDegreeLat = 111.0;
        double kmPerDegreeLon = 111.0 * Math.cos(Math.toRadians(centerLat));
        
        double latOffset = radiusKm / kmPerDegreeLat;
        double lonOffset = radiusKm / kmPerDegreeLon;
        
        // Generate random points within the radius
        java.util.Random random = new java.util.Random();
        
        for (int i = 0; i < count; i++) {
            // Random angle and distance
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = Math.sqrt(random.nextDouble()) * radiusKm; // sqrt for uniform distribution
            
            // Convert to lat/lon offset
            double distLat = distance * Math.cos(angle) / kmPerDegreeLat;
            double distLon = distance * Math.sin(angle) / kmPerDegreeLon;
            
            double lat = centerLat + distLat;
            double lon = centerLon + distLon;
            
            // Calculate actual distance from center
            double actualDistance = calculateDistance(centerLat, centerLon, lat, lon);
            
            String id = String.format("point_%d", i);
            String name = String.format("Punto %.2f°N, %.2f°E", lat, lon);
            
            points.add(new VirtualStation(id, name, lat, lon, actualDistance));
        }
        
        return points;
    }
    
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
    
    private JsonNode getJson(String urlString) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(urlString).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                    StandardCharsets.UTF_8));

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            if (responseCode >= 200 && responseCode < 300) {
                return mapper.readTree(response.toString());
            }

            throw new RuntimeException("OpenMeteo API error (HTTP " + responseCode + "): " + response);

        } catch (Exception e) {
            throw new RuntimeException("Error calling OpenMeteo API: " + urlString, e);
        }
    }
    
    /**
     * Represents a virtual weather station (grid point)
     */
    public static class VirtualStation {
        private final String id;
        private final String name;
        private final double latitude;
        private final double longitude;
        private final double distanceKm;
        
        public VirtualStation(String id, String name, double latitude, double longitude, double distanceKm) {
            this.id = id;
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
            this.distanceKm = distanceKm;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public double getDistanceKm() { return distanceKm; }
    }
}
