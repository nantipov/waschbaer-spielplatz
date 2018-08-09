package org.nantipov.waschbaer.domain;

import lombok.Data;

import java.util.List;

@Data
public class ContainerDTO<T> {
    private List<T> items;
}
