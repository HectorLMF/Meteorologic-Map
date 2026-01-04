package com.javamid.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javamid.client.MeteostatApiClient;
import com.javamid.model.WeatherStation;
import com.javamid.util.Config;
import com.javamid.util.TimedCache;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service to find the nearest weather station to given coordinates
 */
public class WeatherStationService {

    private static final Logger LOGGER = Logger.getLogger(WeatherStationService.class.getName());

    /**
     * Provider selection:
     * - meteostat (default): only Meteostat (RapidAPI)
     * - openweather: only OpenWeather
     * - auto: prefer Meteostat, fall back to OpenWeather
     */
    private static final String DEFAULT_PROVIDER = "meteostat";
    private static final String ENV_PROVIDER = "WEATHER_PROVIDER";
    
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private static final String FIND_API_URL = "https://api.openweathermap.org/data/2.5/find";
    private static final int DEFAULT_NEARBY_COUNT = 20;

    private static final String JSON_DATA = "data";
    private static final String JSON_COUNTRY = "country";

    private final MeteostatApiClient meteostatClient;
    private final TimedCache<String, WeatherStation> meteostatMetaCache = new TimedCache<>();
    private final TimedCache<String, List<WeatherStation>> meteostatNearbyCache = new TimedCache<>();
    private final String provider;
    
    public WeatherStationService() {
        this.apiKey = Config.firstNonBlank(Config.get("openweather.api.key"), System.getenv("OPENWEATHER_API_KEY"));
        this.meteostatClient = new MeteostatApiClient();
        this.provider = normalizeProvider(Config.firstNonBlank(Config.get("weather.provider"), System.getenv(ENV_PROVIDER)));
    }
    
    public WeatherStationService(String apiKey) {
        this.apiKey = apiKey;
        this.meteostatClient = new MeteostatApiClient();
        this.provider = normalizeProvider(Config.firstNonBlank(Config.get("weather.provider"), System.getenv(ENV_PROVIDER)));
    }

    private static String normalizeProvider(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PROVIDER;
        }
        String v = value.trim().toLowerCase();
        if ("meteostat".equals(v) || "openweather".equals(v) || "auto".equals(v)) {
            return v;
        }
        return DEFAULT_PROVIDER;
    }
    
    /**
     * Find the nearest weather station to the given coordinates
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @return The nearest WeatherStation or null if not found
     */
    public WeatherStation findNearestStation(double latitude, double longitude) {
        if ("openweather".equals(provider)) {
            if (this.apiKey == null || this.apiKey.isBlank()) {
                LOGGER.info("OPENWEATHER_API_KEY not set - skipping station lookup");
                return null;
            }
            return findNearestStationOpenWeather(latitude, longitude);
        }

        // Default: Meteostat only (RapidAPI)
        if (meteostatClient.isConfigured()) {
            return findNearestStationMeteostat(latitude, longitude);
        }

        if ("auto".equals(provider)) {
            if (this.apiKey == null || this.apiKey.isBlank()) {
                LOGGER.info("No API keys configured (set 'meteostat.rapidapi.key' in javamid.local.properties/application-local.properties or env METEOSTAT_RAPIDAPI_KEY/RAPIDAPI_KEY; and/or 'openweather.api.key'/env OPENWEATHER_API_KEY) - skipping station lookup");
                return null;
            }
            return findNearestStationOpenWeather(latitude, longitude);
        }

        LOGGER.info("Meteostat key not configured (set 'meteostat.rapidapi.key' in javamid.local.properties/application-local.properties or env METEOSTAT_RAPIDAPI_KEY/RAPIDAPI_KEY) - skipping station lookup");
        return null;
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

        } catch (Exception e) {
            if ("auto".equals(provider)) {
                LOGGER.log(Level.WARNING, e, () -> "Meteostat lookup failed; trying OpenWeather because WEATHER_PROVIDER=auto: " + e.getMessage());
                if (this.apiKey == null || this.apiKey.isBlank()) {
                    return null;
                }
                return findNearestStationOpenWeather(latitude, longitude);
            }

            LOGGER.log(Level.WARNING, e, () -> "Meteostat lookup failed: " + e.getMessage());
            return null;
        }
    }

    private WeatherStation findNearestStationOpenWeather(double latitude, double longitude) {
        List<WeatherStation> candidates = findNearbyStationsOpenWeather(latitude, longitude, DEFAULT_NEARBY_COUNT);
        if (candidates.isEmpty()) {
            return null;
        }

        WeatherStation nearest = candidates.stream()
                .min(Comparator.comparingDouble(ws -> ws.distanceTo(latitude, longitude)))
                .orElse(null);

        if (nearest != null) {
            nearest.setDistanceKm(nearest.distanceTo(latitude, longitude));
        }

        return nearest;
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
     * Find multiple nearby weather stations
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param count Number of stations to find
     * @return List of nearby weather stations
     */
    public List<WeatherStation> findNearbyStations(double latitude, double longitude, int count) {
        if ("openweather".equals(provider)) {
            return findNearbyStationsOpenWeather(latitude, longitude, count);
        }

        if (meteostatClient.isConfigured()) {
            try {
                JsonNode root = meteostatClient.getNearbyStations(latitude, longitude, count, 100000);
                return parseMeteostatNearbyStations(root);
            } catch (Exception e) {
                if ("auto".equals(provider)) {
                    LOGGER.log(Level.WARNING, e, () -> "Meteostat nearby failed; trying OpenWeather because WEATHER_PROVIDER=auto: " + e.getMessage());
                    return findNearbyStationsOpenWeather(latitude, longitude, count);
                }
                LOGGER.log(Level.WARNING, e, () -> "Meteostat nearby failed: " + e.getMessage());
                return List.of();
            }
        }

        if ("auto".equals(provider)) {
            return findNearbyStationsOpenWeather(latitude, longitude, count);
        }

        return List.of();
    }

    private List<WeatherStation> findNearbyStationsOpenWeather(double latitude, double longitude, int count) {
        List<WeatherStation> stations = new ArrayList<>();

        if (this.apiKey == null || this.apiKey.isBlank()) {
            LOGGER.info("OPENWEATHER_API_KEY not set - skipping station lookup");
            return stations;
        }

        try {
            HttpURLConnection conn = openOpenWeatherConnection(latitude, longitude, count);
            int responseCode = conn.getResponseCode();
            String responseBody = readBody(conn, responseCode);
            if (responseCode >= 200 && responseCode < 300) {
                stations.addAll(parseOpenWeatherFind(responseBody, latitude, longitude));
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e, () -> "OpenWeather nearby lookup failed: " + e.getMessage());
        }

        return stations;
    }

    private HttpURLConnection openOpenWeatherConnection(double latitude, double longitude, int count) throws IOException {
        String urlString = FIND_API_URL +
                "?lat=" + latitude +
                "&lon=" + longitude +
                "&cnt=" + count +
                "&appid=" + apiKey;

        HttpURLConnection conn = (HttpURLConnection) URI.create(urlString).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        return conn;
    }

    private String readBody(HttpURLConnection conn, int responseCode) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(
                responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                StandardCharsets.UTF_8));

        StringBuilder response = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        return response.toString();
    }

    private List<WeatherStation> parseOpenWeatherFind(String responseBody, double latitude, double longitude) throws IOException {
        List<WeatherStation> stations = new ArrayList<>();
        JsonNode root = mapper.readTree(responseBody);
        JsonNode list = root.get("list");
        if (list == null || !list.isArray()) {
            return stations;
        }

        for (JsonNode station : list) {
            WeatherStation ws = new WeatherStation();
            ws.setId(station.get("id").asText());
            ws.setName(station.get("name").asText());

            JsonNode coord = station.get("coord");
            ws.setLatitude(coord.get("lat").asDouble());
            ws.setLongitude(coord.get("lon").asDouble());

            JsonNode sys = station.get("sys");
            if (sys != null && sys.has(JSON_COUNTRY)) {
                ws.setCountry(sys.get(JSON_COUNTRY).asText());
            }

            ws.setDistanceKm(ws.distanceTo(latitude, longitude));
            ws.setSource("openweather");
            stations.add(ws);
        }

        return stations;
    }
}
