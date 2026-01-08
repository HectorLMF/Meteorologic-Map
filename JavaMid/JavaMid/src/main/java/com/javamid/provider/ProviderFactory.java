package com.javamid.provider;

public class ProviderFactory {

    public static WeatherProvider getProvider(String name) {
        if (name == null || name.isBlank()) {
            name = System.getenv().getOrDefault("WEATHER_PROVIDER", "open-meteo");
        }

        switch (name.toLowerCase()) {
            case "weatherapi":
                // return new WeatherApiProvider(...);
                throw new UnsupportedOperationException("WeatherApiProvider not implemented yet");
            case "open-meteo":
            case "openmeteo":
            default:
                return new OpenMeteoProvider();
        }
    }
}
