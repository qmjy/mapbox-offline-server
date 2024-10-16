package io.github.qmjy.mapserver.model;

import lombok.Data;

import java.util.List;

@Data
public class VectorLayersJsonObj
{
    private List<VectorLayers> vector_layers;
}
