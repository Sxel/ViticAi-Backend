package com.vitialert.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record NodeStatusDto(
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("last_seen") Instant lastSeen,
        @JsonProperty("online") boolean online,
        @JsonProperty("offline_after_minutes") long offlineAfterMinutes,
        @JsonProperty("latest") TelemetryReadingDto latest,
        @JsonProperty("current_irrigation") IrrigationEventDto currentIrrigation
) {
}
