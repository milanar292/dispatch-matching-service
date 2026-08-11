package com.innovinlabs.dispatch_service.repository;

import com.innovinlabs.dispatch_service.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {}
