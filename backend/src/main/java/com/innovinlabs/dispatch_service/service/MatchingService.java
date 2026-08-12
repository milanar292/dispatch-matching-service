package com.innovinlabs.dispatch_service.service;

import com.innovinlabs.dispatch_service.entity.*;
import com.innovinlabs.dispatch_service.exception.NotFoundException;
import com.innovinlabs.dispatch_service.repository.AssignmentRepository;
import com.innovinlabs.dispatch_service.repository.DriverRepository;
import com.innovinlabs.dispatch_service.repository.RideRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class MatchingService {

    private static final int ASSIGNMENT_TIMEOUT_SECONDS = 120;

    // Staleness tolerance: a driver's last-known location is trusted up to this age.
    // Beyond this, we don't consider them for matching — their real position is
    // too uncertain to safely dispatch them, even though they're still marked AVAILABLE.
    private static final int MAX_LOCATION_STALENESS_SECONDS = 180;

    private final DriverRepository driverRepository;
    private final RideRequestRepository rideRequestRepository;
    private final AssignmentRepository assignmentRepository;

    public MatchingService(DriverRepository driverRepository,
                            RideRequestRepository rideRequestRepository,
                            AssignmentRepository assignmentRepository) {
        this.driverRepository = driverRepository;
        this.rideRequestRepository = rideRequestRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional
    public void match(UUID requestId) {
        RideRequest request = rideRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request not found: " + requestId));

        LocalDateTime staleThreshold = LocalDateTime.now().minusSeconds(MAX_LOCATION_STALENESS_SECONDS);

        List<Driver> candidates = driverRepository.findByStatus(DriverStatus.AVAILABLE);

        candidates.removeIf(d -> d.getLatitude() == null || d.getLongitude() == null);
        candidates.removeIf(d -> d.getLocationUpdatedAt() == null
                || d.getLocationUpdatedAt().isBefore(staleThreshold));

        candidates.sort(Comparator.comparingDouble(d ->
                distanceKm(request.getPickupLat(), request.getPickupLng(), d.getLatitude(), d.getLongitude())));

        for (Driver driver : candidates) {
            int updated = driverRepository.tryReserve(driver.getId());
            if (updated == 1) {
                Assignment assignment = new Assignment(request, driver,
                        LocalDateTime.now().plusSeconds(ASSIGNMENT_TIMEOUT_SECONDS));
                assignmentRepository.save(assignment);

                request.setStatus(RequestStatus.DRIVER_RESERVED);
                rideRequestRepository.save(request);
                return;
            }
        }

        request.setStatus(RequestStatus.NO_DRIVER_AVAILABLE);
        rideRequestRepository.save(request);
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}