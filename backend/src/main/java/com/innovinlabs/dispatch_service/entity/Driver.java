package com.innovinlabs.dispatch_service.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "driver")
public class Driver {

    @Id
    @GeneratedValue
    private UUID id;

    private String name;

    @ManyToOne
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    private DriverStatus status = DriverStatus.OFFLINE;

    private Double latitude;
    private Double longitude;
    private LocalDateTime locationUpdatedAt;
    private LocalDateTime availableSince;
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Driver() {}

    public Driver(String name, Vehicle vehicle) {
        this.name = name;
        this.vehicle = vehicle;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Vehicle getVehicle() { return vehicle; }
    public DriverStatus getStatus() { return status; }

    /**
     * Transitioning into AVAILABLE (from any other status) stamps
     * availableSince — this is what the fairness tie-breaker in
     * MatchingService uses to prefer whoever has been idle longest.
     * Stamping it here, rather than at each call site, means every
     * place that flips a driver to AVAILABLE (explicit status update,
     * ReassignmentScheduler releasing a timed-out driver) gets it
     * automatically instead of relying on each one remembering to.
     */
    public void setStatus(DriverStatus status) {
        if (status == DriverStatus.AVAILABLE && this.status != DriverStatus.AVAILABLE) {
            this.availableSince = LocalDateTime.now();
        }
        this.status = status;
    }

    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public LocalDateTime getLocationUpdatedAt() { return locationUpdatedAt; }
    public LocalDateTime getAvailableSince() { return availableSince; }

    public void updateLocation(double lat, double lng, LocalDateTime at) {
        this.latitude = lat;
        this.longitude = lng;
        this.locationUpdatedAt = at;
    }
}
