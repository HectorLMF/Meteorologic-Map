package com.javamid.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;

/**
 * Adapter for OpenMeteoClient implementing WeatherClient.
 */
public class OpenMeteoAdapter implements WeatherClient {
    private final OpenMeteoClient delegate;

    public OpenMeteoAdapter(OpenMeteoClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public JsonNode getHistoricalWeather(double lat, double lon, LocalDate start, LocalDate end) throws Exception {
        return delegate.getHistoricalWeather(lat, lon, start, end);
    }
}
