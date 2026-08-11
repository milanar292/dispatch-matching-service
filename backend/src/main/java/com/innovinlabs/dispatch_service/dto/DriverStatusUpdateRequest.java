package com.innovinlabs.dispatch_service.dto;

import com.innovinlabs.dispatch_service.entity.DriverStatus;

public record DriverStatusUpdateRequest(DriverStatus status) {}