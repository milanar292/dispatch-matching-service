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

            Driver driver = assignment.getDriver();
            if (driver.getStatus() == DriverStatus.RESERVED) {
                driver.setStatus(DriverStatus.AVAILABLE);
                driverRepository.save(driver);
            }

            RideRequest request = assignment.getRequest();
            request.setStatus(RequestStatus.REMATCHING);
            rideRequestRepository.save(request);

            matchingService.match(request.getId());
        }
    }
}