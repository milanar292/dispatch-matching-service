package com.innovinlabs.dispatch_service.repository;

import com.innovinlabs.dispatch_service.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    @Modifying
    @Query("UPDATE Driver d SET d.status = 'RESERVED' WHERE d.id = :id AND d.status = 'AVAILABLE'")
    int tryReserve(@Param("id") UUID id);
}
