package com.javamid.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.javamid.client.MeteostatApiClient;
import com.javamid.model.WeatherStation;
import com.javamid.util.TimedCache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service to find the nearest weather station to given coordinates using Meteostat
 */
public class WeatherStationService {

    private static final Logger LOGGER = Logger.getLogger(WeatherStationService.class.getName());

    private static final String JSON_DATA = "data";
    private static final String JSON_COUNTRY = "country";

    private final MeteostatApiClient meteostatClient;
    private final TimedCache<String, WeatherStation> meteostatMetaCache = new TimedCache<>();
    private final TimedCache<String, List<WeatherStation>> meteostatNearbyCache = new TimedCache<>();
    
    public WeatherStationService() {
        this(null);
    }
    
    public WeatherStationService(String meteostatKey) {
        this.meteostatClient = new MeteostatApiClient(meteostatKey);
    }
    
    /**
     * Find the nearest weather station to the given coordinates using Meteostat
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @return The nearest WeatherStation or null if not found
     */
    public WeatherStation findNearestStation(double latitude, double longitude) {
        if (!meteostatClient.isConfigured()) {
            LOGGER.info("Meteostat key not configured (set 'meteostat.rapidapi.key' in javamid.local.properties/application-local.properties or env METEOSTAT_RAPIDAPI_KEY/RAPIDAPI_KEY) - skipping station lookup");
            return null;
        }
        
        return findNearestStationMeteostat(latitude, longitude);
    }

    private WeatherStation findNearestStationMeteostat(double latitude, double longitude) {
        try {
            // Cache key rounded ~100m to avoid spamming while panning
            String key = String.format("%.3f,%.3f", latitude, longitude);

            List<WeatherStation> nearby = meteostatNearbyCache.getIfFresh(key);
            if (nearby == null) {
                JsonNode root = meteostatClient.getNearbyStations(latitude, longitude, 10, 100000);
                nearby = parseMeteostatNearbyStations(root);
                meteostatNearbyCache.put(key, nearby, 30_000);
            }

            if (nearby.isEmpty()) {
                return null;
            }

            WeatherStation nearest = nearby.get(0);
            // Enrich with meta (coords, elevation, timezone, country/region)
            WeatherStation meta = meteostatMetaCache.getIfFresh(nearest.getId());
            if (meta == null) {
                JsonNode metaRoot = meteostatClient.getStationMeta(nearest.getId());
                meta = parseMeteostatStationMeta(metaRoot);
                if (meta != null) {
                    meteostatMetaCache.put(nearest.getId(), meta, 24L * 60 * 60 * 1000);
                }
            }

            if (meta != null) {
                // Keep distance from nearby call
                meta.setDistanceKm(nearest.getDistanceKm());
                if (meta.getName() == null || meta.getName().isBlank()) {
                    meta.setName(nearest.getName());
                }
                return meta;
            }

            return nearest;

        } catch (com.javamid.client.MeteostatApiException e) {
            int code = e.getStatusCode();
            LOGGER.log(Level.WARNING, () -> "Meteostat lookup failed (" + code + "): " + e.getMessage());
            return null;
        } catch (Exception e) {
            // Other unexpected errors
            LOGGER.log(Level.WARNING, e, () -> "Station lookup error: " + e.getMessage());
            return null;
        }
    }

    private List<WeatherStation> parseMeteostatNearbyStations(JsonNode root) {
        List<WeatherStation> stations = new ArrayList<>();
        if (root == null) {
            return stations;
        }
        JsonNode data = root.get(JSON_DATA);
        if (data == null || !data.isArray()) {
            return stations;
        }

        for (JsonNode node : data) {
            if (node == null) {
                continue;
            }
            WeatherStation ws = new WeatherStation();
            if (node.hasNonNull("id")) {
                ws.setId(node.get("id").asText());
            }
            if (node.hasNonNull("name")) {
                ws.setName(pickLocalizedName(node.get("name")).orElse(null));
            }
            if (node.hasNonNull("distance")) {
                ws.setDistanceKm(node.get("distance").asDouble() / 1000.0);
            }
            ws.setSource("meteostat");
            stations.add(ws);
        }

        stations.sort(Comparator.comparingDouble(ws -> ws.getDistanceKm() == null ? Double.MAX_VALUE : ws.getDistanceKm()));
        return stations;
    }

    private WeatherStation parseMeteostatStationMeta(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode data = root.get(JSON_DATA);
        if (data == null || data.isNull()) {
            return null;
        }

        WeatherStation ws = new WeatherStation();
        ws.setSource("meteostat");

        if (data.hasNonNull("id")) {
            ws.setId(data.get("id").asText());
        }
        if (data.hasNonNull("name")) {
            ws.setName(pickLocalizedName(data.get("name")).orElse(null));
        }
        if (data.hasNonNull(JSON_COUNTRY)) {
            ws.setCountry(data.get(JSON_COUNTRY).asText());
        }
        if (data.hasNonNull("region")) {
            ws.setState(data.get("region").asText());
        }
        if (data.hasNonNull("timezone")) {
            ws.setTimezone(data.get("timezone").asText());
        }

        applyLocation(ws, data.get("location"));

        return ws;
    }

    private void applyLocation(WeatherStation ws, JsonNode loc) {
        if (ws == null || loc == null || !loc.isObject()) {
            return;
        }
        if (loc.hasNonNull("latitude")) {
            ws.setLatitude(loc.get("latitude").asDouble());
        }
        if (loc.hasNonNull("longitude")) {
            ws.setLongitude(loc.get("longitude").asDouble());
        }
        if (loc.hasNonNull("elevation")) {
            ws.setElevationMeters(loc.get("elevation").asInt());
        }
    }

    private Optional<String> pickLocalizedName(JsonNode nameNode) {
        if (nameNode == null || !nameNode.isObject()) {
            return Optional.empty();
        }
        if (nameNode.hasNonNull("en")) {
            return Optional.ofNullable(nameNode.get("en").asText());
        }
        if (nameNode.fieldNames().hasNext()) {
            String anyLang = nameNode.fieldNames().next();
            JsonNode value = nameNode.get(anyLang);
            if (value != null && !value.isNull()) {
                return Optional.ofNullable(value.asText());
            }
        }
        return Optional.empty();
    }
    
    /**
     * Find multiple nearby weather stations using Meteostat
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param count Number of stations to find
     * @return List of nearby weather stations
     */
    public List<WeatherStation> findNearbyStations(double latitude, double longitude, int count) {
        if (!meteostatClient.isConfigured()) {
            return List.of();
        }

        try {
            JsonNode root = meteostatClient.getNearbyStations(latitude, longitude, count, 100000);
            return parseMeteostatNearbyStations(root);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e, () -> "Meteostat nearby failed: " + e.getMessage());
            return List.of();
        }
    }
}
