package com.innovinlabs.dispatch_service.controller;

import com.innovinlabs.dispatch_service.dto.CreateVehicleRequest;
import com.innovinlabs.dispatch_service.dto.VehicleResponse;
import com.innovinlabs.dispatch_service.service.VehicleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleResponse create(@RequestBody CreateVehicleRequest request) {
        return VehicleResponse.from(vehicleService.create(request));
    }

    @GetMapping
    public List<VehicleResponse> getAll() {
        return vehicleService.getAll().stream().map(VehicleResponse::from).toList();
    }
}