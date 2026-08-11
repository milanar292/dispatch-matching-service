package com.innovinlabs.dispatch_service.controller;

import com.innovinlabs.dispatch_service.dto.CreateDriverRequest;
import com.innovinlabs.dispatch_service.dto.DriverResponse;
import com.innovinlabs.dispatch_service.dto.DriverStatusUpdateRequest;
import com.innovinlabs.dispatch_service.dto.LocationUpdateRequest;
import com.innovinlabs.dispatch_service.service.DriverService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/drivers")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DriverResponse create(@RequestBody CreateDriverRequest request) {
        return DriverResponse.from(driverService.createDriver(request));
    }

    @GetMapping("/{id}")
    public DriverResponse getById(@PathVariable UUID id) {
        return DriverResponse.from(driverService.getById(id));
    }

    @GetMapping
    public List<DriverResponse> getAll() {
        return driverService.getAll().stream().map(DriverResponse::from).toList();
    }

    @PostMapping("/{id}/location")
    public DriverResponse updateLocation(@PathVariable UUID id, @RequestBody LocationUpdateRequest request) {
        return DriverResponse.from(driverService.updateLocation(id, request));
    }

    @PatchMapping("/{id}/status")
    public DriverResponse updateStatus(@PathVariable UUID id, @RequestBody DriverStatusUpdateRequest request) {
        return DriverResponse.from(driverService.updateStatus(id, request));
    }
}