package com.innovinlabs.dispatch_service.dto;

import com.innovinlabs.dispatch_service.entity.Vehicle;

import java.util.UUID;

public record VehicleResponse(
        UUID id,
        String type,
        int capacity
) {
    public static VehicleResponse from(Vehicle v) {
        return new VehicleResponse(v.getId(), v.getType(), v.getCapacity());
    }
}