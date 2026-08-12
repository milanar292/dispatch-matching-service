package com.innovinlabs.dispatch_service.routing;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Deterministic simulated routing — no real maps/traffic API call, as
 * intentionally scoped out in docs/DESIGN.md. Traffic level is derived
 * from the driver's id (not random) so demo runs are repeatable: the
 * same driver always shows the same traffic condition.
 */
@Service
public class MockRoutingService implements RoutingService {

    private static final double AVERAGE_SPEED_KMH = 30.0;

    @Override
    public RoutingEstimate estimate(double distanceKm, UUID driverId) {
        TrafficLevel level = deterministicTrafficLevel(driverId);
        double baseMinutes = (distanceKm / AVERAGE_SPEED_KMH) * 60.0;
        double etaMinutes = baseMinutes * level.getTravelTimeFactor();
        return new RoutingEstimate(distanceKm, level, etaMinutes);
    }

    private TrafficLevel deterministicTrafficLevel(UUID driverId) {
        TrafficLevel[] levels = TrafficLevel.values();
        int index = Math.floorMod(driverId.hashCode(), levels.length);
        return levels[index];
    }
}
