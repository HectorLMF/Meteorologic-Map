package com.javamid.model;

public class ForecastDayDTO {
    private String date;
    private Double minTempC;
    private Double maxTempC;
    private Double avgTempC;
    private ConditionDTO condition;

    public ForecastDayDTO() {
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Double getMinTempC() {
        return minTempC;
    }

    public void setMinTempC(Double minTempC) {
        this.minTempC = minTempC;
    }

    public Double getMaxTempC() {
        return maxTempC;
    }

    public void setMaxTempC(Double maxTempC) {
        this.maxTempC = maxTempC;
    }

    public Double getAvgTempC() {
        return avgTempC;
    }

    public void setAvgTempC(Double avgTempC) {
        this.avgTempC = avgTempC;
    }

    public ConditionDTO getCondition() {
        return condition;
    }

    public void setCondition(ConditionDTO condition) {
        this.condition = condition;
    }
}

