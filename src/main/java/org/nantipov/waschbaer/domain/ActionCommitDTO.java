package org.nantipov.waschbaer.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class ActionCommitDTO {
    private UUID actionId;
}
