package org.nantipov.waschbaer.domain;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class SensorsDTO {
    private List<SensorDTO> sensors;

    public static SensorsDTO of(SensorDTO sensor) {
        SensorsDTO sensorsDTO = new SensorsDTO();
        sensorsDTO.setSensors(Collections.singletonList(sensor));
        return sensorsDTO;
    }
}
