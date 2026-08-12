package com.innovinlabs.dispatch_service.dto;

import com.innovinlabs.dispatch_service.entity.Assignment;
import com.innovinlabs.dispatch_service.entity.AssignmentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AssignmentResponse(
        UUID id,
        UUID requestId,
        UUID driverId,
        AssignmentStatus status,
        LocalDateTime reservedAt,
        LocalDateTime confirmedAt,
        LocalDateTime expiresAt,
        String failureReason
) {
    public static AssignmentResponse from(Assignment a) {
        return new AssignmentResponse(
                a.getId(), a.getRequest().getId(), a.getDriver().getId(),
                a.getStatus(), a.getReservedAt(), a.getConfirmedAt(),
                a.getExpiresAt(), a.getFailureReason()
        );
    }
}