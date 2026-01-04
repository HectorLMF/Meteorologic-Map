package com.javamid.provider;

import com.javamid.model.WeatherDTO;
import com.javamid.model.ForecastDTO;

public interface WeatherProvider {
    WeatherDTO getCurrent(String location);
    ForecastDTO getForecast(String location, int days);
}

