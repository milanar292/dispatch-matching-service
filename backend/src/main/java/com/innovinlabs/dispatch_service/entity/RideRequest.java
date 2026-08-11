package com.innovinlabs.dispatch_service.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ride_request")
public class RideRequest {

    @Id
    @GeneratedValue
    private UUID id;

    private String riderId;
    private double pickupLat;
    private double pickupLng;
    private double dropoffLat;
    private double dropoffLng;

    @Enumerated(EnumType.STRING)
    private RequestStatus status = RequestStatus.REQUESTED;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected RideRequest() {}

    public RideRequest(String riderId, double pickupLat, double pickupLng,
                        double dropoffLat, double dropoffLng) {
        this.riderId = riderId;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.dropoffLat = dropoffLat;
        this.dropoffLng = dropoffLng;
    }

    public UUID getId() { return id; }
    public String getRiderId() { return riderId; }
    public double getPickupLat() { return pickupLat; }
    public double getPickupLng() { return pickupLng; }
    public double getDropoffLat() { return dropoffLat; }
    public double getDropoffLng() { return dropoffLng; }
    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
