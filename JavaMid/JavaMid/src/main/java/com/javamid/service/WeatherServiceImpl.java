package com.javamid.service;

import com.javamid.model.ForecastDTO;
import com.javamid.model.WeatherDTO;
import com.javamid.model.WeatherPointDTO;
import com.javamid.provider.ProviderFactory;
import com.javamid.provider.WeatherProvider;

import java.util.List;
import java.util.stream.Collectors;

public class WeatherServiceImpl implements WeatherService {

    private final WeatherProvider provider;

    public WeatherServiceImpl() {
        this.provider = ProviderFactory.getProvider(null);
    }

    // Constructor para inyección/testing
    public WeatherServiceImpl(WeatherProvider provider) {
        this.provider = provider;
    }

    @Override
    public WeatherDTO getCurrentWeather(String location) {
        return provider.getCurrent(location);
    }

    @Override
    public ForecastDTO getForecast(String location, int days) {
        return provider.getForecast(location, days);
    }

    @Override
    public List<WeatherPointDTO> getWeatherForPoints(List<WeatherPointDTO> points) {
        // Por ahora, devolver datos simulados para cada punto
        return points.stream().map(p -> new WeatherPointDTO(
                p.getLat(),
                p.getLon(),
                p.getAlt(),
                20.0 + Math.random() * 5, // temperatura
                5.0 + Math.random() * 2,  // viento
                60.0 + Math.random() * 20, // humedad
                Math.random(), // precipitacion
                Math.random() * 100 // nubosidad
        )).collect(Collectors.toList());
    }
}
