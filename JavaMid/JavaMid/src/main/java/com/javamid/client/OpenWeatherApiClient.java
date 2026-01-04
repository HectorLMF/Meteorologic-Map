package com.javamid.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class OpenWeatherApiClient {

    private static final String DEFAULT_API_KEY = null;
    private static final String DEFAULT_BASE_URL = "https://api.openweathermap.org/data/2.5/weather";
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;

    // Default constructor reads from environment variables
    public OpenWeatherApiClient() {
        this(System.getenv("OPENWEATHER_API_KEY"), System.getenv("OPENWEATHER_BASE_URL"));
    }

    // Constructor for tests / custom base URL
    public OpenWeatherApiClient(String apiKey, String baseUrl) {
        this.apiKey = (apiKey == null || apiKey.isBlank()) ? DEFAULT_API_KEY : apiKey;
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl;
    }

    public JsonNode getWeatherData(String city) {
        if (this.apiKey == null || this.apiKey.isBlank()) {
            throw new IllegalStateException("OPENWEATHER_API_KEY is not set (or was not provided to constructor)");
        }

        try {
            String urlString = this.baseUrl + "?q=" + java.net.URLEncoder.encode(city, StandardCharsets.UTF_8) + "&appid=" + this.apiKey + "&units=metric";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8));

            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            if (responseCode >= 200 && responseCode < 300) {
                return mapper.readTree(response.toString());
            } else {
                JsonNode err = mapper.readTree(response.toString());
                throw new RuntimeException("OpenWeather API error: " + err.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while fetching weather data", e);
        }
    }

    public JsonNode getWeatherDataByCityId(String cityId) {
        if (this.apiKey == null || this.apiKey.isBlank()) {
            throw new IllegalStateException("OPENWEATHER_API_KEY is not set (or was not provided to constructor)");
        }

        if (cityId == null || cityId.isBlank()) {
            throw new IllegalArgumentException("cityId is required");
        }

        try {
            String urlString = this.baseUrl + "?id=" + java.net.URLEncoder.encode(cityId, StandardCharsets.UTF_8)
                    + "&appid=" + this.apiKey + "&units=metric";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8));

            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            if (responseCode >= 200 && responseCode < 300) {
                return mapper.readTree(response.toString());
            } else {
                JsonNode err = mapper.readTree(response.toString());
                throw new RuntimeException("OpenWeather API error: " + err.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while fetching weather data", e);
        }
    }
}
