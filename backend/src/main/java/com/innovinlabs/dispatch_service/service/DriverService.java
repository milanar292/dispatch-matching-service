package com.innovinlabs.dispatch_service.service;

import com.innovinlabs.dispatch_service.dto.CreateDriverRequest;
import com.innovinlabs.dispatch_service.dto.DriverStatusUpdateRequest;
import com.innovinlabs.dispatch_service.dto.LocationUpdateRequest;
import com.innovinlabs.dispatch_service.entity.Assignment;
import com.innovinlabs.dispatch_service.entity.AssignmentStatus;
import com.innovinlabs.dispatch_service.entity.Driver;
import com.innovinlabs.dispatch_service.entity.DriverStatus;
import com.innovinlabs.dispatch_service.entity.Vehicle;
import com.innovinlabs.dispatch_service.exception.NotFoundException;
import com.innovinlabs.dispatch_service.repository.AssignmentRepository;
import com.innovinlabs.dispatch_service.repository.DriverRepository;
import com.innovinlabs.dispatch_service.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DriverService {

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final AssignmentRepository assignmentRepository;

    public DriverService(DriverRepository driverRepository, VehicleRepository vehicleRepository,
                          AssignmentRepository assignmentRepository) {
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public Driver createDriver(CreateDriverRequest request) {
        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new NotFoundException("Vehicle not found: " + request.vehicleId()));
        Driver driver = new Driver(request.name(), vehicle);
        return driverRepository.save(driver);
    }

    public Driver getById(UUID driverId) {
        return findDriverOrThrow(driverId);
    }

    public List<Driver> getAll() {
        return driverRepository.findAll();
    }

    public Driver updateLocation(UUID driverId, LocationUpdateRequest request) {
        Driver driver = findDriverOrThrow(driverId);

        LocalDateTime incomingTimestamp = request.timestamp();

        if (incomingTimestamp == null) {
            throw new IllegalArgumentException("Location timestamp must not be null");
        }

        LocalDateTime currentTimestamp = driver.getLocationUpdatedAt();

        // Ignore out-of-order or duplicate location updates.
        // An older location must never overwrite a newer one.
        if (currentTimestamp != null && !incomingTimestamp.isAfter(currentTimestamp)) {
            return driver;
        }

        driver.updateLocation(
                request.latitude(),
                request.longitude(),
                incomingTimestamp
        );

        return driverRepository.save(driver);
    }

    @Transactional
    public Driver updateStatus(UUID driverId, DriverStatusUpdateRequest request) {
        Driver driver = findDriverOrThrow(driverId);

        // A driver can only be RESERVED via the matching engine's atomic
        // tryReserve, and is only ever meant to leave RESERVED through
        // AssignmentService.confirm (-> BUSY) or ReassignmentScheduler on
        // timeout (-> AVAILABLE, after marking the assignment TIMED_OUT).
        // A manual status PATCH bypasses both of those, so if we let it
        // silently flip a RESERVED driver to something else, the open
        // assignment is left dangling in RESERVED and the driver becomes
        // matchable again — producing a second live reservation on the
        // same driver (double-booking). Close out that dangling assignment
        // here so a manual override can't corrupt the reservation invariant.
        if (driver.getStatus() == DriverStatus.RESERVED && request.status() != DriverStatus.RESERVED) {
            List<Assignment> openAssignments =
                    assignmentRepository.findByDriverIdAndStatus(driverId, AssignmentStatus.RESERVED);
            for (Assignment assignment : openAssignments) {
                assignment.setStatus(AssignmentStatus.TIMED_OUT);
                assignment.setFailureReason("Driver status was manually overridden while reserved");
                assignmentRepository.save(assignment);
            }
        }

        driver.setStatus(request.status());
        return driverRepository.save(driver);
    }

    private Driver findDriverOrThrow(UUID driverId) {
        return driverRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Driver not found: " + driverId));
    }
}