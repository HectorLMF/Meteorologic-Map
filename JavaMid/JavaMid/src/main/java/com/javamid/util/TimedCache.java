package com.javamid.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal TTL cache (thread-safe) for small objects.
 */
public class TimedCache<K, V> {

    private static final class Entry<V> {
        private final V value;
        private final long expiresAtMillis;

        private Entry(V value, long expiresAtMillis) {
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    private final Map<K, Entry<V>> map = new ConcurrentHashMap<>();

    public V getIfFresh(K key) {
        Entry<V> entry = map.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expiresAtMillis) {
            map.remove(key);
            return null;
        }
        return entry.value;
    }

    public void put(K key, V value, long ttlMillis) {
        if (ttlMillis <= 0) {
            return;
        }
        map.put(key, new Entry<>(value, System.currentTimeMillis() + ttlMillis));
    }
}
