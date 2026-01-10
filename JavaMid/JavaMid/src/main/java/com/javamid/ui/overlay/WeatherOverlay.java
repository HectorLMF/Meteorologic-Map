package com.javamid.ui.overlay;

import com.javamid.model.WeatherStation;
import java.util.List;

/**
 * Common overlay contract to unify humidity/temperature overlays.
 */
public interface WeatherOverlay {
    void setActive(boolean active);
    boolean isActive();
    void setStations(List<WeatherStation> stations);
    void setInfluenceRadiusKm(double radiusKm);
    void onMapChanged();
}
