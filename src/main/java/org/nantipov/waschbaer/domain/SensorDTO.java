package org.nantipov.waschbaer.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SensorDTO {
    private String name;
    private Double value;
}
