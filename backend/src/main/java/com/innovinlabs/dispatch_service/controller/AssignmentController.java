package com.innovinlabs.dispatch_service.controller;

import com.innovinlabs.dispatch_service.dto.AssignmentResponse;
import com.innovinlabs.dispatch_service.service.AssignmentService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/{id}")
    public AssignmentResponse getById(@PathVariable UUID id) {
        return AssignmentResponse.from(assignmentService.getById(id));
    }

    @PatchMapping("/{id}/confirm")
    public AssignmentResponse confirm(@PathVariable UUID id) {
        return AssignmentResponse.from(assignmentService.confirm(id));
    }
}