package com.innovinlabs.dispatch_service.entity;

public enum RequestStatus {
    REQUESTED, SEARCHING, DRIVER_RESERVED, ASSIGNMENT_SENT,
    DRIVER_CONFIRMED, COMPLETED, NO_DRIVER_AVAILABLE, REMATCHING, CANCELLED
}
