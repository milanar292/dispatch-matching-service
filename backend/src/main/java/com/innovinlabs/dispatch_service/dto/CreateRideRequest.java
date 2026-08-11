package com.innovinlabs.dispatch_service.dto;

public record CreateRideRequest(
        String riderId,
        double pickupLat,
        double pickupLng,
        double dropoffLat,
        double dropoffLng
) {}