package org.nantipov.waschbaer.domain;

import lombok.Data;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class EventDTO {
    private UUID id;
    private EventType type;
    private Map<String, Object> data;
    private ZonedDateTime occurredAt;
}
