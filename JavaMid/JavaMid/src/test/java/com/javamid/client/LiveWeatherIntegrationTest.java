package com.javamid.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class LiveWeatherIntegrationTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DateTimeFormatter fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

    @Test
    public void testLiveWeatherAndAppendToMd() throws Exception {
        String testingFile = "testingapi.md";
        String city = System.getenv().getOrDefault("TEST_CITY", "Madrid");

        String weatherApiKey = System.getenv("WEATHERAPI_KEY");
        JsonNode root = null;
        String provider = null;

        if (weatherApiKey != null && !weatherApiKey.isBlank()) {
            provider = "weatherapi.com";
            String base = "http://api.weatherapi.com/v1/current.json";
            String urlStr = base + "?key=" + weatherApiKey + "&q=" + java.net.URLEncoder.encode(city, StandardCharsets.UTF_8) + "&aqi=no";
            root = fetchJson(urlStr);
        } else {
            // Fallback to Open-Meteo (no API key required) using Madrid coords as default
            provider = "open-meteo.com";
            // Use coordinates of Madrid as default; override with env vars if provided
            String lat = System.getenv().getOrDefault("TEST_LAT", "40.4168");
            String lon = System.getenv().getOrDefault("TEST_LON", "-3.7038");
            String urlStr = String.format("https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&current_weather=true", lat, lon);
            root = fetchJson(urlStr);
        }

        // Build markdown entry
        StringBuilder md = new StringBuilder();
        md.append("## ").append(fmt.format(Instant.now())).append(" - ").append(provider).append("\n\n");
        md.append("**City**: ").append(city).append("\n\n");

        if (provider.equals("weatherapi.com")) {
            JsonNode location = root.path("location");
            JsonNode current = root.path("current");
            md.append("- Location: ").append(location.path("name")).append(", ").append(location.path("country")).append("\n");
            md.append("- Temp (C): ").append(current.path("temp_c")).append("\n");
            md.append("- Condition: ").append(current.path("condition").path("text")).append("\n");
            md.append("- Wind (kph): ").append(current.path("wind_kph")).append("\n");
            md.append("- Humidity: ").append(current.path("humidity")).append("\n\n");
            md.append("<details>\n<summary>Raw JSON</summary>\n\n``json\n");
            md.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)).append("\n``\n</details>\n\n");
        } else {
            // open-meteo structure
            JsonNode current = root.path("current_weather");
            md.append("- Lat/Lon: ").append(root.path("latitude")).append(", ").append(root.path("longitude")).append("\n");
            md.append("- Temp (C): ").append(current.path("temperature")).append("\n");
            md.append("- Wind (m/s): ").append(current.path("windspeed")).append("\n");
            md.append("- Wind Dir (deg): ").append(current.path("winddirection")).append("\n\n");
            md.append("<details>\n<summary>Raw JSON</summary>\n\n``json\n");
            md.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)).append("\n``\n</details>\n\n");
        }

        // Append to file at project root
        Path p = Path.of(testingFile);
        String entry = md.toString();
        Files.write(p, entry.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        // Basic assertions to ensure we got meaningful data
        Assertions.assertNotNull(root);
        if (provider.equals("weatherapi.com")) {
            Assertions.assertTrue(root.has("location"));
            Assertions.assertTrue(root.has("current"));
        } else {
            Assertions.assertTrue(root.has("current_weather"));
        }
    }

    private JsonNode fetchJson(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");

        int responseCode = conn.getResponseCode();
        BufferedReader in = new BufferedReader(new InputStreamReader(
                responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line).append('\n');
        }
        in.close();

        if (responseCode >= 200 && responseCode < 300) {
            return mapper.readTree(response.toString());
        } else {
            throw new RuntimeException("HTTP " + responseCode + " when fetching " + urlStr + ": " + response.toString());
        }
    }
}

