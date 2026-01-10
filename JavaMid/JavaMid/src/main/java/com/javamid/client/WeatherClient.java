package com.javamid.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;

public interface WeatherClient {
    JsonNode getHistoricalWeather(double lat, double lon, LocalDate start, LocalDate end) throws Exception;
}
