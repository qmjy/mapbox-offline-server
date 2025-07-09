package io.github.qmjy.mapserver.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.geotools.geometry.Position2D;

@Data
@Schema(name = "GeometryPointDTO", description = "地理配准后的地理坐标")
public class GeometryPointDTO {

    @Schema(name = "lng", description = "地理经度坐标", example = "103.89454044804097")
    private double lng;
    @Schema(name = "lat", description = "地理纬度坐标", example = "30.71703562322753")
    private double lat;

    public GeometryPointDTO() {
    }

    public GeometryPointDTO(Position2D p) {
        this.lng = p.getX();
        this.lat = p.getY();
    }
}