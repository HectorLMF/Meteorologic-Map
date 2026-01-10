package com.javamid.client;

/**
 * Simple factory to obtain a default WeatherClient.
 */
public class WeatherClientFactory {
    public static WeatherClient getDefault() {
        // TODO: read provider from configuration; default to OpenMeteo with cache 10 minutes
        return new CachingWeatherClient(new OpenMeteoAdapter(new OpenMeteoClient()), 10 * 60 * 1000);
    }
}
