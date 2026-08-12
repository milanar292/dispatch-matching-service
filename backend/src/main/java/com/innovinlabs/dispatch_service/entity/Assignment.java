package com.innovinlabs.dispatch_service.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "assignment")
public class Assignment {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "request_id")
    private RideRequest request;

    @ManyToOne
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Enumerated(EnumType.STRING)
    private AssignmentStatus status = AssignmentStatus.RESERVED;

    private LocalDateTime reservedAt = LocalDateTime.now();
    private LocalDateTime expiresAt;
    private LocalDateTime confirmedAt;
    private String failureReason;

    protected Assignment() {}

    public Assignment(RideRequest request, Driver driver, LocalDateTime expiresAt) {
        this.request = request;
        this.driver = driver;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public RideRequest getRequest() { return request; }
    public Driver getDriver() { return driver; }
    public AssignmentStatus getStatus() { return status; }
    public void setStatus(AssignmentStatus status) { this.status = status; }
    public LocalDateTime getReservedAt() { return reservedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}