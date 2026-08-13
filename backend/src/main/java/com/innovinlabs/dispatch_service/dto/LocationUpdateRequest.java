package com.innovinlabs.dispatch_service.dto;

import java.time.LocalDateTime;

public record LocationUpdateRequest(
        double latitude,
        double longitude,
        LocalDateTime timestamp
) {}