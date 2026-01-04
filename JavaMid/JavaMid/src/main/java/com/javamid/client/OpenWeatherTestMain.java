package com.javamid.client;

import com.fasterxml.jackson.databind.JsonNode;

public class OpenWeatherTestMain {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Uso: java OpenWeatherTestMain <city>");
            return;
        }
        String city = args[0];
        OpenWeatherApiClient client = new OpenWeatherApiClient();
        try {
            JsonNode root = client.getWeatherData(city);
            System.out.println("Ciudad: " + root.path("name").asText());
            System.out.println("Lat/Lon: " + root.path("coord").path("lat").asDouble() + ", " + root.path("coord").path("lon").asDouble());
            JsonNode main = root.path("main");
            System.out.println("Temperatura: " + main.path("temp").asDouble() + " °C");
            System.out.println("Humedad: " + main.path("humidity").asInt() + " %");
            JsonNode weather = root.path("weather");
            if (weather.isArray() && weather.size() > 0) {
                System.out.println("Condición: " + weather.get(0).path("main").asText() + " - " + weather.get(0).path("description").asText());
            }
            System.out.println("Viento (m/s): " + root.path("wind").path("speed").asDouble());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

