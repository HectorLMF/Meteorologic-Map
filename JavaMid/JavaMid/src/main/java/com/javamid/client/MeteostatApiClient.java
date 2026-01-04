package com.javamid.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javamid.util.Config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public class MeteostatApiClient {

    private static final String DEFAULT_BASE_URL = "https://meteostat.p.rapidapi.com";
    private static final String DEFAULT_HOST = "meteostat.p.rapidapi.com";

    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String host;

    public MeteostatApiClient() {
        this(Config.firstNonBlank(
                Config.get("meteostat.rapidapi.key"),
                System.getenv("METEOSTAT_RAPIDAPI_KEY"),
                System.getenv("RAPIDAPI_KEY"),
                System.getenv("X_RAPIDAPI_KEY")),
            Config.firstNonBlank(
                Config.get("meteostat.baseUrl"),
                System.getenv("METEOSTAT_BASE_URL")),
            Config.firstNonBlank(
                Config.get("meteostat.rapidapi.host"),
                System.getenv("METEOSTAT_RAPIDAPI_HOST")));
    }

    public MeteostatApiClient(String apiKey, String baseUrl, String host) {
        this.apiKey = (apiKey == null || apiKey.isBlank()) ? null : apiKey;
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl;
        this.host = (host == null || host.isBlank()) ? DEFAULT_HOST : host;
    }

    public boolean isConfigured() {
        return this.apiKey != null && !this.apiKey.isBlank();
    }

    // Use Config.firstNonBlank(...) instead.

    public JsonNode getNearbyStations(double lat, double lon, Integer limit, Integer radiusMeters) {
        StringBuilder qs = new StringBuilder();
        qs.append("lat=").append(lat);
        qs.append("&lon=").append(lon);
        if (limit != null) {
            qs.append("&limit=").append(limit);
        }
        if (radiusMeters != null) {
            qs.append("&radius=").append(radiusMeters);
        }
        String url = this.baseUrl + "/stations/nearby?" + qs;
        return getJson(url);
    }

    public JsonNode getStationMeta(String stationId) {
        if (stationId == null || stationId.isBlank()) {
            throw new IllegalArgumentException("stationId is required");
        }
        String url = this.baseUrl + "/stations/meta?id=" + java.net.URLEncoder.encode(stationId, StandardCharsets.UTF_8);
        return getJson(url);
    }

    public JsonNode getStationHourly(String stationId, LocalDate start, LocalDate end, String timezone, String units) {
        if (stationId == null || stationId.isBlank()) {
            throw new IllegalArgumentException("stationId is required");
        }
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end are required");
        }

        StringBuilder qs = new StringBuilder();
        qs.append("station=").append(java.net.URLEncoder.encode(stationId, StandardCharsets.UTF_8));
        qs.append("&start=").append(start);
        qs.append("&end=").append(end);
        if (timezone != null && !timezone.isBlank()) {
            qs.append("&tz=").append(java.net.URLEncoder.encode(timezone, StandardCharsets.UTF_8));
        }
        if (units != null && !units.isBlank()) {
            qs.append("&units=").append(java.net.URLEncoder.encode(units, StandardCharsets.UTF_8));
        }
        // Meteostat defaults model=true; we keep it as default (more complete data)

        String url = this.baseUrl + "/stations/hourly?" + qs;
        return getJson(url);
    }

    private JsonNode getJson(String urlString) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Meteostat RapidAPI key missing. Configure 'meteostat.rapidapi.key' in javamid.local.properties/application-local.properties or set env METEOSTAT_RAPIDAPI_KEY/RAPIDAPI_KEY");
        }

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(urlString).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            // RapidAPI headers
            conn.setRequestProperty("X-RapidAPI-Key", this.apiKey);
            conn.setRequestProperty("X-RapidAPI-Host", this.host);
            conn.setRequestProperty("x-rapidapi-key", this.apiKey);
            conn.setRequestProperty("x-rapidapi-host", this.host);
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

            JsonNode err = tryParseJson(response.toString());
            String detail = err != null ? err.toString() : response.toString();
            throw new MeteostatApiException("Meteostat API error (HTTP " + responseCode + "): " + detail);

        } catch (MeteostatApiException e) {
            throw e;
        } catch (Exception e) {
            throw new MeteostatApiException("Error while calling Meteostat API: " + urlString, e);
        }
    }

    private JsonNode tryParseJson(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception ignored) {
            return null;
        }
    }
}
