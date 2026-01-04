package com.javamid.service;

import com.javamid.model.WeatherDTO;
import com.javamid.model.ForecastDTO;
import com.javamid.model.WeatherPointDTO;

import java.util.List;

public interface WeatherService {
    WeatherDTO getCurrentWeather(String location);
    ForecastDTO getForecast(String location, int days);
    List<WeatherPointDTO> getWeatherForPoints(List<WeatherPointDTO> points);
}
