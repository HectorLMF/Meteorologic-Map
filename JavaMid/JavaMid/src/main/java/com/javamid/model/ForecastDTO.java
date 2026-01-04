package com.javamid.model;

import java.util.List;

public class ForecastDTO {
    private List<ForecastDayDTO> days;

    public ForecastDTO() {
    }

    public List<ForecastDayDTO> getDays() {
        return days;
    }

    public void setDays(List<ForecastDayDTO> days) {
        this.days = days;
    }
}
