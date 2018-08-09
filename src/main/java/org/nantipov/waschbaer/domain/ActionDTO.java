package org.nantipov.waschbaer.domain;

import lombok.Data;

import java.util.UUID;

@Data
public class ActionDTO {
    private UUID id;
    private ActionType type;
    private String argumentsData;
}
