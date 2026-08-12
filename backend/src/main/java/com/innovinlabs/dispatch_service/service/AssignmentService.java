package com.innovinlabs.dispatch_service.service;

import com.innovinlabs.dispatch_service.entity.*;
import com.innovinlabs.dispatch_service.exception.NotFoundException;
import com.innovinlabs.dispatch_service.repository.AssignmentRepository;
import com.innovinlabs.dispatch_service.repository.DriverRepository;
import com.innovinlabs.dispatch_service.repository.RideRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final DriverRepository driverRepository;
    private final RideRequestRepository rideRequestRepository;

    public AssignmentService(AssignmentRepository assignmentRepository,
                              DriverRepository driverRepository,
                              RideRequestRepository rideRequestRepository) {
        this.assignmentRepository = assignmentRepository;
        this.driverRepository = driverRepository;
        this.rideRequestRepository = rideRequestRepository;
    }

    @Transactional
    public Assignment confirm(UUID assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("Assignment not found: " + assignmentId));

        if (assignment.getStatus() != AssignmentStatus.RESERVED) {
            throw new IllegalStateException("Assignment is not in a confirmable state: " + assignment.getStatus());
        }
        if (assignment.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Assignment has already expired");
        }

        assignment.setConfirmedAt(LocalDateTime.now());
        assignment.setStatus(AssignmentStatus.CONFIRMED);
        assignmentRepository.save(assignment);

        Driver driver = assignment.getDriver();
        driver.setStatus(DriverStatus.BUSY);
        driverRepository.save(driver);

        RideRequest request = assignment.getRequest();
        request.setStatus(RequestStatus.DRIVER_CONFIRMED);
        rideRequestRepository.save(request);

        return assignment;
    }

    public Assignment getById(UUID id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Assignment not found: " + id));
    }
}