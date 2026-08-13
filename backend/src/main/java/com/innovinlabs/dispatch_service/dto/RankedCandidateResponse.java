package com.innovinlabs.dispatch_service.dto;

import java.util.UUID;

/**
 * One driver's position in either the speed ordering (distance only) or the
 * quality ordering (ETA + traffic). etaMinutes/trafficLevel are null in the
 * speed ordering, since that strategy never computes them.
 */
public record RankedCandidateResponse(
        UUID driverId,
        int rank,
        double distanceKm,
        Double etaMinutes,
        String trafficLevel
) {
}
