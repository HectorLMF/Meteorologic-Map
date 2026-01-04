package com.javamid.model;

public class WeatherPointDTO {
    private double lat;
    private double lon;
    private double alt;
    private double temperature;
    private double wind;
    private double humidity;
    private double precipitation;
    private double cloudiness;

    public WeatherPointDTO() {}

    public WeatherPointDTO(double lat, double lon, double alt, double temperature, double wind, double humidity, double precipitation, double cloudiness) {
        this.lat = lat;
        this.lon = lon;
        this.alt = alt;
        this.temperature = temperature;
        this.wind = wind;
        this.humidity = humidity;
        this.precipitation = precipitation;
        this.cloudiness = cloudiness;
    }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }
    public double getAlt() { return alt; }
    public void setAlt(double alt) { this.alt = alt; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public double getWind() { return wind; }
    public void setWind(double wind) { this.wind = wind; }
    public double getHumidity() { return humidity; }
    public void setHumidity(double humidity) { this.humidity = humidity; }
    public double getPrecipitation() { return precipitation; }
    public void setPrecipitation(double precipitation) { this.precipitation = precipitation; }
    public double getCloudiness() { return cloudiness; }
    public void setCloudiness(double cloudiness) { this.cloudiness = cloudiness; }
}

