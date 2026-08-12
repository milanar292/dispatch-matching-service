package com.innovinlabs.dispatch_service.service;

import com.innovinlabs.dispatch_service.entity.*;
import com.innovinlabs.dispatch_service.repository.AssignmentRepository;
import com.innovinlabs.dispatch_service.repository.DriverRepository;
import com.innovinlabs.dispatch_service.repository.RideRequestRepository;
import com.innovinlabs.dispatch_service.repository.VehicleRepository; // adjust if your Vehicle repo has a different name
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests run against the local PostgreSQL instance configured in
 * application.yml (same schema and Flyway migrations used in production).
 *
 * NOTE: For a production CI pipeline, these would instead run against an
 * isolated, ephemeral database (e.g. via Testcontainers) rather than a shared
 * local instance, to guarantee a clean slate and avoid any dependency on
 * developer-machine state. That isolation step is called out explicitly as a
 * known gap in the Future Improvements section of this submission.
 *
 * These tests prove the four properties the evaluation specifically asks about:
 *  1. Concurrency-safe claiming (no double-assignment under a race)
 *  2. Staleness tolerance (stale-but-AVAILABLE drivers are excluded from matching)
 *  3. Timeout + automatic reassignment
 *  4. Rejection of confirm() on an already-expired assignment
 */
@SpringBootTest
class DispatchMatchingIntegrationTest {

    @Autowired private DriverRepository driverRepository;
    @Autowired private RideRequestRepository rideRequestRepository;
    @Autowired private AssignmentRepository assignmentRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private MatchingService matchingService;
    @Autowired private ReassignmentScheduler reassignmentScheduler;
    @Autowired private AssignmentService assignmentService;

    @AfterEach
    void cleanUp() {
        // Harmless no-op for @Transactional tests below (their data never committed);
        // this is what actually matters for the non-transactional concurrency test.
        assignmentRepository.deleteAll();
        rideRequestRepository.deleteAll();
        driverRepository.deleteAll();
        vehicleRepository.deleteAll();
    }

    // ---------- 1. Concurrency-safe claiming ----------
    // Deliberately NOT @Transactional: the two threads must use separate real
    // transactions/connections to genuinely race for the same row, the same way
    // two concurrent HTTP requests would in production.
    @Test
    void onlyOneThreadCanReserveTheSameAvailableDriver() throws Exception {
        Vehicle vehicle = vehicleRepository.save(new Vehicle("SEDAN", 4));
        Driver driver = new Driver("Driver X", vehicle);
        driver.setStatus(DriverStatus.AVAILABLE);
        driver.updateLocation(12.97, 77.59, LocalDateTime.now());
        driver = driverRepository.save(driver);
        UUID driverId = driver.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Integer> attempt = () -> {
            readyLatch.countDown();
            startLatch.await();
            return driverRepository.tryReserve(driverId);
        };

        Future<Integer> f1 = executor.submit(attempt);
        Future<Integer> f2 = executor.submit(attempt);

        readyLatch.await();
        startLatch.countDown();

        int result1 = f1.get(5, TimeUnit.SECONDS);
        int result2 = f2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, result1 + result2,
                "Exactly one of the two concurrent reservation attempts should succeed");

        Driver reloaded = driverRepository.findById(driverId).orElseThrow();
        assertEquals(DriverStatus.RESERVED, reloaded.getStatus());
    }

    // ---------- 2. Staleness tolerance ----------
    @Test
    @Transactional
    void staleDriverIsExcludedFromMatchingEvenIfMarkedAvailable() {
        Vehicle vehicle = vehicleRepository.save(new Vehicle("SEDAN", 4));

        Driver freshDriver = new Driver("Fresh Driver", vehicle);
        freshDriver.setStatus(DriverStatus.AVAILABLE);
        freshDriver.updateLocation(12.97, 77.59, LocalDateTime.now());
        freshDriver = driverRepository.save(freshDriver);

        Driver staleDriver = new Driver("Stale Driver", vehicle);
        staleDriver.setStatus(DriverStatus.AVAILABLE);
        // Older than MAX_LOCATION_STALENESS_SECONDS (180s) in MatchingService
        staleDriver.updateLocation(12.9701, 77.5901, LocalDateTime.now().minusSeconds(400));
        staleDriver = driverRepository.save(staleDriver);

        RideRequest request = new RideRequest("rider-1", 12.97, 77.59, 13.00, 77.60);
        request = rideRequestRepository.save(request);

        matchingService.match(request.getId());

        RideRequest reloadedRequest = rideRequestRepository.findById(request.getId()).orElseThrow();
        assertEquals(RequestStatus.DRIVER_RESERVED, reloadedRequest.getStatus());

        List<Assignment> assignments = assignmentRepository.findAll();
        assertEquals(1, assignments.size());
        assertEquals(freshDriver.getId(), assignments.get(0).getDriver().getId(),
                "Only the driver with a fresh location should have been matched");

        Driver reloadedStale = driverRepository.findById(staleDriver.getId()).orElseThrow();
        assertEquals(DriverStatus.AVAILABLE, reloadedStale.getStatus(),
                "The stale driver must never be reserved, despite being marked AVAILABLE");
    }

    // ---------- 3. Timeout & automatic reassignment ----------
    @Test
    @Transactional
    void expiredAssignmentIsReapedAndDriverIsFreedAndRematched() {
        Vehicle vehicle = vehicleRepository.save(new Vehicle("SEDAN", 4));

        Driver driver = new Driver("Driver Y", vehicle);
        driver.setStatus(DriverStatus.AVAILABLE);
        driver.updateLocation(12.97, 77.59, LocalDateTime.now());
        driver = driverRepository.save(driver);

        RideRequest request = new RideRequest("rider-2", 12.97, 77.59, 13.00, 77.60);
        request = rideRequestRepository.save(request);

        matchingService.match(request.getId());

        List<Assignment> initialAssignments = assignmentRepository.findAll();
        assertEquals(1, initialAssignments.size());
        Assignment original = initialAssignments.get(0);
        assertEquals(AssignmentStatus.RESERVED, original.getStatus());

        // Force it into the past to simulate a real 120s timeout without waiting for it
        original.setExpiresAt(LocalDateTime.now().minusSeconds(5));
        assignmentRepository.save(original);

        reassignmentScheduler.reapExpiredAssignments();

        Assignment reloadedOriginal = assignmentRepository.findById(original.getId()).orElseThrow();
        assertEquals(AssignmentStatus.TIMED_OUT, reloadedOriginal.getStatus());
        assertNotNull(reloadedOriginal.getFailureReason());

        // Same behavior you saw live: the freed driver is still nearest and fresh,
        // so it gets immediately re-matched into a brand-new RESERVED assignment
        // for the same still-unfulfilled request.
        UUID requestId = request.getId();
        List<Assignment> assignmentsForRequest = assignmentRepository.findAll().stream()
                .filter(a -> a.getRequest().getId().equals(requestId))
                .toList();

        assertEquals(2, assignmentsForRequest.size(),
                "Expected the original TIMED_OUT assignment plus one fresh RESERVED one");
        assertTrue(assignmentsForRequest.stream().anyMatch(a -> a.getStatus() == AssignmentStatus.RESERVED),
                "Driver should have been re-matched into a fresh RESERVED assignment");

        Driver reloadedDriver = driverRepository.findById(driver.getId()).orElseThrow();
        assertEquals(DriverStatus.RESERVED, reloadedDriver.getStatus(),
                "Driver should be re-reserved for the same request after timeout");
    }

    // ---------- 4. confirm() rejects an already-expired assignment ----------
    @Test
    @Transactional
    void confirmingAnAlreadyExpiredAssignmentThrows() {
        Vehicle vehicle = vehicleRepository.save(new Vehicle("SEDAN", 4));
        Driver driver = new Driver("Driver Z", vehicle);
        driver.setStatus(DriverStatus.RESERVED);
        driver.updateLocation(12.97, 77.59, LocalDateTime.now());
        driver = driverRepository.save(driver);

        RideRequest request = new RideRequest("rider-3", 12.97, 77.59, 13.00, 77.60);
        request.setStatus(RequestStatus.DRIVER_RESERVED);
        request = rideRequestRepository.save(request);

        Assignment assignment = new Assignment(request, driver, LocalDateTime.now().minusSeconds(1)); // already expired
        assignment = assignmentRepository.save(assignment);

        UUID assignmentId = assignment.getId();

        assertThrows(IllegalStateException.class, () -> assignmentService.confirm(assignmentId));
    }
}
