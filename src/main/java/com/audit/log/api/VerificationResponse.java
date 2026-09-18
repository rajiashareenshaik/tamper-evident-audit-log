package com.audit.log.api;

import java.util.UUID;

public record VerificationResponse(
        boolean intact,
        long recordsVerified,
        Inconsistency firstInconsistency
) {
    public record Inconsistency(long sequence, UUID eventId, String violationType) { }

    public static VerificationResponse intact(long count) {
        return new VerificationResponse(true, count, null);
    }

    public static VerificationResponse broken(long count, long sequence, UUID eventId, String type) {
        return new VerificationResponse(false, count, new Inconsistency(sequence, eventId, type));
    }
}
