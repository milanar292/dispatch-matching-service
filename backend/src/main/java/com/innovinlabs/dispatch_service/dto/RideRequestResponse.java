package com.innovinlabs.dispatch_service.dto;

import com.innovinlabs.dispatch_service.entity.RequestStatus;
import com.innovinlabs.dispatch_service.entity.RideRequest;

import java.time.LocalDateTime;
import java.util.UUID;

public record RideRequestResponse(
        UUID id,
        String riderId,
        RequestStatus status,
        double pickupLat,
        double pickupLng,
        double dropoffLat,
        double dropoffLng,
        LocalDateTime createdAt
) {
    public static RideRequestResponse from(RideRequest r) {
        return new RideRequestResponse(
                r.getId(), r.getRiderId(), r.getStatus(),
                r.getPickupLat(), r.getPickupLng(),
                r.getDropoffLat(), r.getDropoffLng(),
                r.getCreatedAt()
        );
    }
}