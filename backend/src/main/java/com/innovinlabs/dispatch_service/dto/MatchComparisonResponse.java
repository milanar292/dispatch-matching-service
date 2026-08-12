package com.innovinlabs.dispatch_service.dto;

import java.util.List;
import java.util.UUID;

/**
 * Side-by-side comparison of the two candidate orderings for a request:
 * the actual "speed" strategy the service uses in production (nearest
 * eligible driver first), and a "quality" strategy (nearest N re-ranked
 * by mock ETA/traffic). This endpoint is read-only — it never reserves
 * a driver, it only shows how the pick would differ.
 */
public record MatchComparisonResponse(
        UUID requestId,
        List<RankedCandidateResponse> speedOrder,
        List<RankedCandidateResponse> qualityOrder,
        boolean topPickDiffers
) {
}
