package com.innovinlabs.dispatch_service.dto;

public record CreateVehicleRequest(
        String type,
        int capacity
) {}