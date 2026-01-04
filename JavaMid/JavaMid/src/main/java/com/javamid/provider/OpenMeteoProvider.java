package com.javamid.provider;

import com.javamid.model.LocationDTO;
import com.javamid.model.WeatherDTO;
import com.javamid.model.ForecastDTO;

public class OpenMeteoProvider implements WeatherProvider {

    public OpenMeteoProvider() {
    }

    @Override
    public WeatherDTO getCurrent(String location) {
        // Esqueleto: devolver un WeatherDTO vacío para ser llenado por implementación real
        WeatherDTO w = new WeatherDTO();
        LocationDTO loc = new LocationDTO();
        loc.setName(location);
        w.setLocation(loc);
        w.setProvider("open-meteo");
        w.setTimestamp(java.time.Instant.now().toString());
        return w;
    }

    @Override
    public ForecastDTO getForecast(String location, int days) {
        // Esqueleto: no implementado aún
        return new ForecastDTO();
    }
}
