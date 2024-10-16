package io.github.qmjy.mapserver.model;

import lombok.Data;

import java.util.Map;


@Data
public class VectorLayers {
    private String id;
    private Map<String,String> fields;
    private int minzoom;
    private int maxzoom;
}
