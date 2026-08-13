package com.innovinlabs.dispatch_service.repository;

import com.innovinlabs.dispatch_service.entity.RideRequest;
import com.innovinlabs.dispatch_service.entity.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RideRequestRepository extends JpaRepository<RideRequest, UUID> {
    List<RideRequest> findByStatus(RequestStatus status);
}