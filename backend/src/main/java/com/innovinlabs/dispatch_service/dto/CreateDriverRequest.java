package com.innovinlabs.dispatch_service.dto;

import java.util.UUID;

public record CreateDriverRequest(
        String name,
        UUID vehicleId
) {}