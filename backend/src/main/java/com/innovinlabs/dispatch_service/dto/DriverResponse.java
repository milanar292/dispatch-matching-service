package com.innovinlabs.dispatch_service.dto;

import com.innovinlabs.dispatch_service.entity.Driver;
import com.innovinlabs.dispatch_service.entity.DriverStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record DriverResponse(
        UUID id,
        String name,
        DriverStatus status,
        Double latitude,
        Double longitude,
        LocalDateTime locationUpdatedAt
) {
    public static DriverResponse from(Driver d) {
        return new DriverResponse(
                d.getId(), d.getName(), d.getStatus(),
                d.getLatitude(), d.getLongitude(), d.getLocationUpdatedAt()
        );
    }
}