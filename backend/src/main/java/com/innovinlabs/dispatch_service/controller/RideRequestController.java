package com.innovinlabs.dispatch_service.controller;

import com.innovinlabs.dispatch_service.dto.CreateRideRequest;
import com.innovinlabs.dispatch_service.dto.RideRequestResponse;
import com.innovinlabs.dispatch_service.service.RideRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/requests")
public class RideRequestController {

    private final RideRequestService rideRequestService;

    public RideRequestController(RideRequestService rideRequestService) {
        this.rideRequestService = rideRequestService;
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
}