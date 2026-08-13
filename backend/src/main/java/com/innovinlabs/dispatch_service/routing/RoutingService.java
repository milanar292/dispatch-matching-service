package com.innovinlabs.dispatch_service.routing;

import java.util.UUID;

/**
 * Supplies a travel-time estimate for a driver-to-pickup leg. The only
 * implementation today is {@link MockRoutingService} (simulated traffic,
 * no external calls) — a real maps/traffic provider could implement this
 * same interface later without any change to MatchingService.
 */
public interface RoutingService {
    RoutingEstimate estimate(double distanceKm, UUID driverId);
}
