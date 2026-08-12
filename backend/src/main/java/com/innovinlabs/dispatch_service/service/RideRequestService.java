package com.innovinlabs.dispatch_service.service;

import com.innovinlabs.dispatch_service.dto.CreateRideRequest;
import com.innovinlabs.dispatch_service.entity.RequestStatus;
import com.innovinlabs.dispatch_service.entity.RideRequest;
import com.innovinlabs.dispatch_service.exception.NotFoundException;
import com.innovinlabs.dispatch_service.repository.RideRequestRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RideRequestService {

    private final RideRequestRepository rideRequestRepository;
    private final MatchingService matchingService;

    public RideRequestService(RideRequestRepository rideRequestRepository, MatchingService matchingService) {
        this.rideRequestRepository = rideRequestRepository;
        this.matchingService = matchingService;
    }

    public RideRequest create(CreateRideRequest request) {
        RideRequest rideRequest = new RideRequest(
                request.riderId(), request.pickupLat(), request.pickupLng(),
                request.dropoffLat(), request.dropoffLng()
        );
        rideRequest.setStatus(RequestStatus.SEARCHING);
        rideRequest = rideRequestRepository.save(rideRequest);

        matchingService.match(rideRequest.getId());

        return rideRequestRepository.findById(rideRequest.getId()).orElseThrow();
    }

    public RideRequest getById(UUID id) {
        return rideRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Request not found: " + id));
    }
}