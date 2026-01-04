package com.javamid.client;

/**
 * Runtime exception for Meteostat (RapidAPI) client errors.
 */
public class MeteostatApiException extends RuntimeException {

    public MeteostatApiException(String message) {
        super(message);
    }

    public MeteostatApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
