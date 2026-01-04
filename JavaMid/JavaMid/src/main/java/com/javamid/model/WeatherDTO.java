package com.javamid.model;

// REMOVED: código eliminado por petición del usuario. Estamos en fase de diagramas.

public class WeatherDTO {
    private LocationDTO location;
    private Double tempC;
    private Integer humidity;
    private Double windSpeed;
    private Integer windDirection;
    private ConditionDTO condition;
    private String provider;
    private String timestamp;

    public WeatherDTO() {
    }

    public LocationDTO getLocation() {
        return location;
    }

    public void setLocation(LocationDTO location) {
        this.location = location;
    }

    public Double getTempC() {
        return tempC;
    }

    public void setTempC(Double tempC) {
        this.tempC = tempC;
    }

    public Integer getHumidity() {
        return humidity;
    }

    public void setHumidity(Integer humidity) {
        this.humidity = humidity;
    }

    public Double getWindSpeed() {
        return windSpeed;
    }

    public void setWindSpeed(Double windSpeed) {
        this.windSpeed = windSpeed;
    }

    public Integer getWindDirection() {
        return windDirection;
    }

    public void setWindDirection(Integer windDirection) {
        this.windDirection = windDirection;
    }

    public ConditionDTO getCondition() {
        return condition;
    }

    public void setCondition(ConditionDTO condition) {
        this.condition = condition;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
