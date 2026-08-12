package com.innovinlabs.dispatch_service.service;

import com.innovinlabs.dispatch_service.dto.MatchComparisonResponse;
import com.innovinlabs.dispatch_service.dto.RankedCandidateResponse;
import com.innovinlabs.dispatch_service.entity.*;
import com.innovinlabs.dispatch_service.exception.NotFoundException;
import com.innovinlabs.dispatch_service.repository.AssignmentRepository;
import com.innovinlabs.dispatch_service.repository.DriverRepository;
import com.innovinlabs.dispatch_service.repository.RideRequestRepository;
import com.innovinlabs.dispatch_service.routing.RoutingEstimate;
import com.innovinlabs.dispatch_service.routing.RoutingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class MatchingService {

    private static final int ASSIGNMENT_TIMEOUT_SECONDS = 60;

    // Staleness tolerance: a driver's last-known location is trusted up to this age.
    // Beyond this, we don't consider them for matching — their real position is
    // too uncertain to safely dispatch them, even though they're still marked AVAILABLE.
    private static final int MAX_LOCATION_STALENESS_SECONDS = 180;

    // How many of the nearest candidates get re-ranked by ETA/traffic in the
    // "quality" comparison strategy. Kept small on purpose: this bounds how
    // many routing calls a real (non-mock) provider would need per match.
    private static final int QUALITY_SHORTLIST_SIZE = 5;

    private final DriverRepository driverRepository;
    private final RideRequestRepository rideRequestRepository;
    private final AssignmentRepository assignmentRepository;
    private final RoutingService routingService;

    public MatchingService(DriverRepository driverRepository,
                            RideRequestRepository rideRequestRepository,
                            AssignmentRepository assignmentRepository,
                            RoutingService routingService) {
        this.driverRepository = driverRepository;
        this.rideRequestRepository = rideRequestRepository;
        this.assignmentRepository = assignmentRepository;
        this.routingService = routingService;
    }

    @Transactional
    public void match(UUID requestId) {
        RideRequest request = rideRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request not found: " + requestId));

        List<Driver> candidates = eligibleDriversSortedByDistance(request);

        for (Driver driver : candidates) {
            int updated = driverRepository.tryReserve(driver.getId());
            if (updated == 1) {
                Assignment assignment = new Assignment(request, driver,
                        LocalDateTime.now().plusSeconds(ASSIGNMENT_TIMEOUT_SECONDS));
                assignmentRepository.save(assignment);

                request.setStatus(RequestStatus.DRIVER_RESERVED);
                rideRequestRepository.save(request);
                return;
            }
        }

        request.setStatus(RequestStatus.NO_DRIVER_AVAILABLE);
        rideRequestRepository.save(request);
    }

    /**
     * Available, non-stale drivers eligible for a request, nearest first.
     * This is the exact candidate set and ordering {@link #match} claims
     * against — the "speed" strategy.
     */
    private List<Driver> eligibleDriversSortedByDistance(RideRequest request) {
        LocalDateTime staleThreshold = LocalDateTime.now().minusSeconds(MAX_LOCATION_STALENESS_SECONDS);

        List<Driver> candidates = driverRepository.findByStatus(DriverStatus.AVAILABLE);

        candidates.removeIf(d -> d.getLatitude() == null || d.getLongitude() == null);
        candidates.removeIf(d -> d.getLocationUpdatedAt() == null
                || d.getLocationUpdatedAt().isBefore(staleThreshold));

        candidates.sort(Comparator.comparingDouble(d ->
                distanceKm(request.getPickupLat(), request.getPickupLng(), d.getLatitude(), d.getLongitude())));

        return candidates;
    }

    /**
     * Read-only comparison of two candidate-ranking strategies for a request.
     * Never calls tryReserve() and never changes any driver or request state —
     * it exists purely to make the matching-quality-vs-matching-speed tradeoff
     * concrete and demoable.
     * <p>
     * "Speed" is exactly what {@link #match} uses in production: nearest
     * eligible driver first, full candidate list.
     * <p>
     * "Quality" takes the nearest {@link #QUALITY_SHORTLIST_SIZE} candidates
     * from that same list and re-ranks them by mock ETA (distance adjusted
     * for simulated traffic), which can promote a slightly-farther driver
     * over the geographically closest one.
     */
    @Transactional
    public MatchComparisonResponse compareStrategies(UUID requestId) {
        RideRequest request = rideRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request not found: " + requestId));

        List<Driver> candidates = eligibleDriversSortedByDistance(request);

        List<RankedCandidateResponse> speedOrder = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Driver d = candidates.get(i);
            double distance = distanceKm(request.getPickupLat(), request.getPickupLng(),
                    d.getLatitude(), d.getLongitude());
            speedOrder.add(new RankedCandidateResponse(d.getId(), i + 1, distance, null, null));
        }

        List<Driver> shortlist = candidates.subList(0, Math.min(QUALITY_SHORTLIST_SIZE, candidates.size()));

        List<RankedCandidateResponse> qualityOrder = shortlist.stream()
                .map(d -> {
                    double distance = distanceKm(request.getPickupLat(), request.getPickupLng(),
                            d.getLatitude(), d.getLongitude());
                    RoutingEstimate estimate = routingService.estimate(distance, d.getId());
                    return new RankedCandidateResponse(d.getId(), 0, distance,
                            estimate.etaMinutes(), estimate.trafficLevel().name());
                })
                .sorted(Comparator.comparingDouble(RankedCandidateResponse::etaMinutes))
                .toList();

        // Ranks are assigned after sorting, since sorted() above can't mutate the record in place.
        List<RankedCandidateResponse> rankedQualityOrder = new ArrayList<>();
        for (int i = 0; i < qualityOrder.size(); i++) {
            RankedCandidateResponse c = qualityOrder.get(i);
            rankedQualityOrder.add(new RankedCandidateResponse(
                    c.driverId(), i + 1, c.distanceKm(), c.etaMinutes(), c.trafficLevel()));
        }

        boolean topPickDiffers = !speedOrder.isEmpty() && !rankedQualityOrder.isEmpty()
                && !speedOrder.get(0).driverId().equals(rankedQualityOrder.get(0).driverId());

        return new MatchComparisonResponse(requestId, speedOrder, rankedQualityOrder, topPickDiffers);
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}