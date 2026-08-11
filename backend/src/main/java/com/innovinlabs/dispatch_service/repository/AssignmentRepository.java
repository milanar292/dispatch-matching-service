package com.innovinlabs.dispatch_service.repository;

import com.innovinlabs.dispatch_service.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {}
