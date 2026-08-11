package com.innovinlabs.dispatch_service.service;

import com.innovinlabs.dispatch_service.dto.CreateDriverRequest;
import com.innovinlabs.dispatch_service.dto.DriverStatusUpdateRequest;
import com.innovinlabs.dispatch_service.dto.LocationUpdateRequest;
import com.innovinlabs.dispatch_service.entity.Driver;
import com.innovinlabs.dispatch_service.entity.Vehicle;
import com.innovinlabs.dispatch_service.exception.NotFoundException;
import com.innovinlabs.dispatch_service.repository.DriverRepository;
import com.innovinlabs.dispatch_service.repository.VehicleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DriverService {

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;

    public DriverService(DriverRepository driverRepository, VehicleRepository vehicleRepository) {
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
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
        driver.updateLocation(request.latitude(), request.longitude(), LocalDateTime.now());
        return driverRepository.save(driver);
    }

    public Driver updateStatus(UUID driverId, DriverStatusUpdateRequest request) {
        Driver driver = findDriverOrThrow(driverId);
        driver.setStatus(request.status());
        return driverRepository.save(driver);
    }

    private Driver findDriverOrThrow(UUID driverId) {
        return driverRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Driver not found: " + driverId));
    }
}