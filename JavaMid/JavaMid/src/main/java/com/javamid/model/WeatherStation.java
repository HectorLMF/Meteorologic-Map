package com.javamid.model;

/**
 * Represents a weather station with geographical coordinates
 */
public class WeatherStation {
    private String id;
    private String name;
    private double latitude;
    private double longitude;
    private String country;
    private String state;
    private Double distanceKm;
    private String source;
    private Integer elevationMeters;
    private String timezone;

    public WeatherStation() {
    }

    public WeatherStation(String id, String name, double latitude, double longitude) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Integer getElevationMeters() {
        return elevationMeters;
    }

    public void setElevationMeters(Integer elevationMeters) {
        this.elevationMeters = elevationMeters;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    /**
     * Calculate distance to another point using Haversine formula
     */
    public double distanceTo(double lat, double lon) {
        final int R = 6371; // Radius of the earth in km

        double latDistance = Math.toRadians(lat - this.latitude);
        double lonDistance = Math.toRadians(lon - this.longitude);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(this.latitude)) * Math.cos(Math.toRadians(lat))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c; // Distance in km
    }

    @Override
    public String toString() {
        return "WeatherStation{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", country='" + country + '\'' +
                ", state='" + state + '\'' +
                ", distanceKm=" + distanceKm +
                ", source='" + source + '\'' +
                ", elevationMeters=" + elevationMeters +
                ", timezone='" + timezone + '\'' +
                '}';
    }
}
