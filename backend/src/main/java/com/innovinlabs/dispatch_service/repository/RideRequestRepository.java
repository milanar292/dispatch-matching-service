package com.innovinlabs.dispatch_service.repository;

import com.innovinlabs.dispatch_service.entity.RideRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RideRequestRepository extends JpaRepository<RideRequest, UUID> {}
