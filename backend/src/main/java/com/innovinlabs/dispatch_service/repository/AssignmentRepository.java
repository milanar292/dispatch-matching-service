package com.innovinlabs.dispatch_service.repository;

import com.innovinlabs.dispatch_service.entity.Assignment;
import com.innovinlabs.dispatch_service.entity.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {
    List<Assignment> findByStatusAndExpiresAtBefore(AssignmentStatus status, LocalDateTime time);
}