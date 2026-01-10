package com.javamid.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple TTL cache decorator for WeatherClient.
 */
public class CachingWeatherClient implements WeatherClient {
    private static class Entry {
        final JsonNode value; final long expiresAt;
        Entry(JsonNode v, long e) { this.value = v; this.expiresAt = e; }
    }

    private final WeatherClient delegate;
    private final long ttlMillis;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public CachingWeatherClient(WeatherClient delegate, long ttlMillis) {
        this.delegate = delegate;
        this.ttlMillis = ttlMillis;
    }

    @Override
    public JsonNode getHistoricalWeather(double lat, double lon, LocalDate start, LocalDate end) throws Exception {
        String key = lat + ":" + lon + ":" + start + ":" + end;
        long now = System.currentTimeMillis();
        Entry e = cache.get(key);
        if (e != null && e.expiresAt > now) {
            return e.value;
        }
        JsonNode v = delegate.getHistoricalWeather(lat, lon, start, end);
        cache.put(key, new Entry(v, now + ttlMillis));
        return v;
    }
}
