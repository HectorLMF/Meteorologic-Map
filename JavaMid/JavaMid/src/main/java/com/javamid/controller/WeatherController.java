package com.javamid.controller;

import com.javamid.model.WeatherDTO;
import com.javamid.model.WeatherPointDTO;
import com.javamid.service.WeatherService;
import com.javamid.service.WeatherServiceImpl;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class WeatherController {

    private final WeatherService weatherService = new WeatherServiceImpl();

    @GetMapping(value = "/weather", produces = "application/json")
    public WeatherDTO getWeather(@RequestParam String city) {
        return weatherService.getCurrentWeather(city);
    }

    @PostMapping(value = "/weather/points", produces = "application/json")
    public List<WeatherPointDTO> getWeatherForPoints(@RequestBody List<WeatherPointDTO> points) {
        // Llama al nuevo método del servicio (a implementar) para obtener los datos de cada punto
        return weatherService.getWeatherForPoints(points);
    }
}
