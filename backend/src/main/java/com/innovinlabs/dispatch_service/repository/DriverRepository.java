package com.innovinlabs.dispatch_service.repository;

import com.innovinlabs.dispatch_service.entity.Driver;
import com.innovinlabs.dispatch_service.entity.DriverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    List<Driver> findByStatus(DriverStatus status);

    @Query(value = """
            SELECT d.id, d.name, d.vehicle_id, d.status, d.latitude, d.longitude,
                   d.location_updated_at, d.created_at, d.available_since
            FROM driver d
            WHERE d.status = 'AVAILABLE'
              AND d.location IS NOT NULL
              AND d.location_updated_at >= :staleThreshold
              AND ST_DWithin(d.location,
                    ST_SetSRID(ST_MakePoint(:pickupLng, :pickupLat), 4326)::geography,
                    :radiusMeters)
            ORDER BY
                ST_Distance(d.location,
                    ST_SetSRID(ST_MakePoint(:pickupLng, :pickupLat), 4326)::geography),
                d.available_since ASC NULLS FIRST
            """, nativeQuery = true)
    List<Driver> findNearbyAvailableDrivers(@Param("pickupLat") double pickupLat,
                                            @Param("pickupLng") double pickupLng,
                                            @Param("radiusMeters") double radiusMeters,
                                            @Param("staleThreshold") LocalDateTime staleThreshold);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Driver d SET d.status = 'RESERVED' WHERE d.id = :id AND d.status = 'AVAILABLE'")
    int tryReserve(@Param("id") UUID id);
    }