package com.innovinlabs.dispatch_service.routing;

/**
 * Estimated travel time and traffic condition for one driver-to-pickup leg.
 */
public record RoutingEstimate(double distanceKm, TrafficLevel trafficLevel, double etaMinutes) {
}
