package com.javamid.ui.overlay;

import java.util.EnumMap;
import java.util.Map;

/**
 * Manages overlay activation and ensures mutual exclusivity.
 */
public class OverlayManager {
    private final Map<OverlayMode, WeatherOverlay> overlays = new EnumMap<>(OverlayMode.class);
    private OverlayMode activeMode = OverlayMode.NONE;

    public void register(OverlayMode mode, WeatherOverlay overlay) {
        overlays.put(mode, overlay);
    }

    public void setActiveMode(OverlayMode mode) {
        this.activeMode = mode != null ? mode : OverlayMode.NONE;
        // deactivate all
        for (Map.Entry<OverlayMode, WeatherOverlay> e : overlays.entrySet()) {
            boolean shouldActivate = e.getKey() == this.activeMode && this.activeMode != OverlayMode.NONE;
            e.getValue().setActive(shouldActivate);
        }
    }

    public OverlayMode getActiveMode() { return activeMode; }
}
