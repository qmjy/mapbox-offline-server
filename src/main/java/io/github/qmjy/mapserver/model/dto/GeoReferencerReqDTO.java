package io.github.qmjy.mapserver.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class GeoReferencerReqDTO {
    @Schema(description = "图片的宽度，单位为像素", example = "1920")
    private int width;

    @Schema(description = "图片的高度，单位为像素", example = "1080")
    private int height;

    @Schema(description = "待校准的地理点位，需要填入四组GIS点位", example = "经度,纬度;经度,纬度;经度,纬度;经度,纬度")
    private String geometryPoints;

    @Schema(description = "待配准的像素点位", example = "x1,y1;x2,y2;x3,y3")
    private String pixelPoints;
}
