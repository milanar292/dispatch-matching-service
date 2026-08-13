package com.innovinlabs.dispatch_service.service;

import com.innovinlabs.dispatch_service.entity.*;
import com.innovinlabs.dispatch_service.repository.AssignmentRepository;
import com.innovinlabs.dispatch_service.repository.DriverRepository;
import com.innovinlabs.dispatch_service.repository.RideRequestRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ReassignmentScheduler {

    // Cap on reassignment attempts per ride request. Without this, a request
    // whose nearest driver keeps timing out (never confirms, but stays
    // AVAILABLE-eligible again after each timeout) gets re-matched to the
    // same or similar candidate indefinitely, leaving the rider stuck in
    // REMATCHING forever instead of ever reaching a terminal state.
    private static final int MAX_REASSIGNMENT_ATTEMPTS = 3;

    private final AssignmentRepository assignmentRepository;
    private final DriverRepository driverRepository;
    private final RideRequestRepository rideRequestRepository;
    private final MatchingService matchingService;

    public ReassignmentScheduler(AssignmentRepository assignmentRepository,
                                  DriverRepository driverRepository,
                                  RideRequestRepository rideRequestRepository,
                                  MatchingService matchingService) {
        this.assignmentRepository = assignmentRepository;
        this.driverRepository = driverRepository;
        this.rideRequestRepository = rideRequestRepository;
        this.matchingService = matchingService;
    }

    @Scheduled(fixedRate = 5000)
    @Transactional
    public void reapExpiredAssignments() {
        List<Assignment> expired = assignmentRepository
                .findByStatusAndExpiresAtBefore(AssignmentStatus.RESERVED, LocalDateTime.now());

        for (Assignment assignment : expired) {
            assignment.setStatus(AssignmentStatus.TIMED_OUT);
            assignment.setFailureReason("Driver did not confirm before expiry");
            assignmentRepository.save(assignment);

            Driver driver = driverRepository.findById(assignment.getDriver().getId())
                    .orElseThrow(() -> new IllegalStateException("Driver not found: " + assignment.getDriver().getId()));
            if (driver.getStatus() == DriverStatus.RESERVED) {
                driver.setStatus(DriverStatus.AVAILABLE);
                driverRepository.saveAndFlush(driver);
            }

            RideRequest request = assignment.getRequest();

            long previousAttempts = assignmentRepository.countByRequestIdAndStatus(
                    request.getId(), AssignmentStatus.TIMED_OUT);

            if (previousAttempts >= MAX_REASSIGNMENT_ATTEMPTS) {
                request.setStatus(RequestStatus.NO_DRIVER_AVAILABLE);
                rideRequestRepository.save(request);
                continue;
            }

            request.setStatus(RequestStatus.REMATCHING);
            rideRequestRepository.save(request);

            matchingService.match(request.getId());
        }
    }
}