package com.innovinlabs.dispatch_service.routing;

/**
 * Simulated traffic condition used by {@link MockRoutingService} to turn a
 * straight-line distance into a rough travel-time estimate. Factors match
 * what was already documented (but never implemented) in docs/DESIGN.md.
 */
public enum TrafficLevel {
    LOW(1.0),
    MODERATE(1.25),
    HIGH(1.6);

    private final double travelTimeFactor;

    TrafficLevel(double travelTimeFactor) {
        this.travelTimeFactor = travelTimeFactor;
    }

    public double getTravelTimeFactor() {
        return travelTimeFactor;
    }
}
