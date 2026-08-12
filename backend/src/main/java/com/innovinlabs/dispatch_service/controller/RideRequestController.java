package com.innovinlabs.dispatch_service.controller;

import com.innovinlabs.dispatch_service.dto.CreateRideRequest;
import com.innovinlabs.dispatch_service.dto.MatchComparisonResponse;
import com.innovinlabs.dispatch_service.dto.RideRequestResponse;
import com.innovinlabs.dispatch_service.service.MatchingService;
import com.innovinlabs.dispatch_service.service.RideRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/requests")
public class RideRequestController {

    private final RideRequestService rideRequestService;
    private final MatchingService matchingService;

    public RideRequestController(RideRequestService rideRequestService, MatchingService matchingService) {
        this.rideRequestService = rideRequestService;
        this.matchingService = matchingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RideRequestResponse create(@RequestBody CreateRideRequest request) {
        return RideRequestResponse.from(rideRequestService.create(request));
    }

    @GetMapping("/{id}")
    public RideRequestResponse getById(@PathVariable UUID id) {
        return RideRequestResponse.from(rideRequestService.getById(id));
    }

    /**
     * Read-only: shows how the driver ranking would differ between the
     * "speed" strategy this service actually uses (nearest first) and a
     * "quality" strategy (nearest few re-ranked by mock ETA/traffic).
     * Never reserves a driver or changes any state.
     */
    @GetMapping("/{id}/match-comparison")
    public MatchComparisonResponse matchComparison(@PathVariable UUID id) {
        return matchingService.compareStrategies(id);
    }
}