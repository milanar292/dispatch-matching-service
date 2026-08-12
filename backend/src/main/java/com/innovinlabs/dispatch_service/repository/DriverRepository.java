package com.innovinlabs.dispatch_service.repository;

import com.innovinlabs.dispatch_service.entity.Driver;
import com.innovinlabs.dispatch_service.entity.DriverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    List<Driver> findByStatus(DriverStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Driver d SET d.status = 'RESERVED' WHERE d.id = :id AND d.status = 'AVAILABLE'")
    int tryReserve(@Param("id") UUID id);
    }