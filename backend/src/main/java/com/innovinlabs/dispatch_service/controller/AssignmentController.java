package com.innovinlabs.dispatch_service.controller;

import com.innovinlabs.dispatch_service.dto.AssignmentResponse;
import com.innovinlabs.dispatch_service.repository.AssignmentRepository;
import com.innovinlabs.dispatch_service.service.AssignmentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final AssignmentRepository assignmentRepository;

    public AssignmentController(AssignmentService assignmentService, AssignmentRepository assignmentRepository) {
        this.assignmentService = assignmentService;
        this.assignmentRepository = assignmentRepository;
    }

    @GetMapping
    public List<AssignmentResponse> getAll() {
        return assignmentRepository.findAll().stream().map(AssignmentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AssignmentResponse getById(@PathVariable UUID id) {
        return AssignmentResponse.from(assignmentService.getById(id));
    }

    @PatchMapping("/{id}/confirm")
    public AssignmentResponse confirm(@PathVariable UUID id) {
        return AssignmentResponse.from(assignmentService.confirm(id));
    }

    @PatchMapping("/{id}/complete")
    public AssignmentResponse complete(@PathVariable UUID id) {
        return AssignmentResponse.from(assignmentService.complete(id));
    }
}