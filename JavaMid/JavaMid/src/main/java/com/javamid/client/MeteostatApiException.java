package com.javamid.client;

/**
 * Runtime exception for Meteostat (RapidAPI) client errors.
 */
public class MeteostatApiException extends RuntimeException {

    private final int statusCode;

    public MeteostatApiException(String message) {
        super(message);
        this.statusCode = -1;
    }

    public MeteostatApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public MeteostatApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
