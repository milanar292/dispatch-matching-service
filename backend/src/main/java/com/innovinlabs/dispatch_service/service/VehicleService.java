package com.innovinlabs.dispatch_service.service;

import com.innovinlabs.dispatch_service.dto.CreateVehicleRequest;
import com.innovinlabs.dispatch_service.entity.Vehicle;
import com.innovinlabs.dispatch_service.repository.VehicleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    public VehicleService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    public Vehicle create(CreateVehicleRequest request) {
        Vehicle vehicle = new Vehicle(request.type(), request.capacity());
        return vehicleRepository.save(vehicle);
    }

    public List<Vehicle> getAll() {
        return vehicleRepository.findAll();
    }
}