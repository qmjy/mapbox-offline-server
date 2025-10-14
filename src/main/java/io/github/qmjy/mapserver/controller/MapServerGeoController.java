/*
 * Copyright (c) 2023 QMJY.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.github.qmjy.mapserver.controller;

import io.github.qmjy.mapserver.MapServerDataCenter;
import io.github.qmjy.mapserver.model.AdministrativeDivisionNode;
import io.github.qmjy.mapserver.model.dto.GeoReferencerReqDTO;
import io.github.qmjy.mapserver.model.dto.GeometryPointDTO;
import io.github.qmjy.mapserver.util.ImageGeoreferencer;
import io.github.qmjy.mapserver.util.ResponseMapUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.Position2D;
import org.geotools.geometry.jts.GeometryBuilder;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 地理编码与逆编码接口
 *
 * @author liushaofeng
 */
@RestController
@RequestMapping("/api")
@Tag(name = "地理编码管理", description = "地理编码与逆编码服务接口能力")
public class MapServerGeoController {
    private static final Logger logger = LoggerFactory.getLogger(MapServerGeoController.class);

    /**
     * 地理信息配准。当前接口仅支持四边形校准。顺序为（左上、右上、右下、左下）
     *
     * @param referencerReqDTO 待配准的数据信息
     * @return 配准后的结果。
     */
    @Operation(summary = "地理配准", description = "将一张图片的像素坐标配准成地理坐标。如果一次批量提交了多个像素坐标，获取结果时需要使用`(int)x + '-' + (int)y`格式的key从Map中获取结果。")
    @PostMapping("/georeferencer")
    @ResponseBody
    public ResponseEntity<Map<String, GeometryPointDTO>> georeferencer(@RequestBody GeoReferencerReqDTO referencerReqDTO) {
        Position2D[] array = null;

        String geometryPoints = referencerReqDTO.getGeometryPoints();
        if (geometryPoints != null && geometryPoints.indexOf(",") > 0) {
            String[] split = geometryPoints.split(";");
            if (split.length != 4) {
                return ResponseEntity.badRequest().build();
            }
            array = new Position2D[split.length];
            for (int i = 0; i < split.length; i++) {
                if (split[i].indexOf(",") > 0) {
                    String[] split1 = split[i].split(",");
                    array[i] = new Position2D(Double.parseDouble(split1[0]), Double.parseDouble(split1[1]));
                }
            }
        }

        ImageGeoreferencer imageGeoreferencer = new ImageGeoreferencer(referencerReqDTO.getWidth(), referencerReqDTO.getHeight(), array);
        try {
            Map<String, GeometryPointDTO> points = imageGeoreferencer.transformImageToGeo(getPixels(referencerReqDTO.getPixelPoints()));
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(points);
        } catch (TransformException e) {
            logger.error("地理配准失败：{}", e.toString());
            return ResponseEntity.internalServerError().build();
        }
    }

    private List<Position2D> getPixels(String pixelPoints) {
        List<Position2D> pixels = new ArrayList<>();
        if (pixelPoints != null && pixelPoints.indexOf(",") > 0) {
            String[] split = pixelPoints.split(";");
            for (String s : split) {
                String[] split1 = s.split(",");
                pixels.add(new Position2D(Double.parseDouble(split1[0]), Double.parseDouble(split1[1])));
            }
        }
        return pixels;
    }

    /**
     * 简易地理逆编码
     * 该方法用于通过经纬度坐标获取对应的行政区划信息
     *
     * @param location 经度在前，纬度在后，经纬度间以“,”分割，经纬度小数点后不要超过 6 位。
     * @param langType 可选参数，支持本地语言(0:default)和英语(1)。
     * @param splitter 各行政区划节点之间的分割符。
     * @return 地理逆编码结果
     */
    @Operation(summary = "地理逆编码查询", description = "通过经纬度查询行政区划概要信息，通过区划ID可获取行政区划详细信息。")
    @GetMapping("/geocode/regeo")
    public ResponseEntity<Map<String, Object>> regeo(@Parameter(description = "待查询的经纬度坐标，例如：104.071883,30.671974") @RequestParam(value = "location") String location, @Parameter(description = "返回的数据语言。0：本地语言（default）；1：英语") @RequestParam(value = "langType", required = false, defaultValue = "0") int langType, @Parameter(description = "各行政区划节点之间的分割符。默认本地语言无分隔符，英文为空格。") @RequestParam(value = "splitter", required = false, defaultValue = "") String splitter) {
        AdministrativeDivisionNode simpleAdminDivision = MapServerDataCenter.getInstance().getSimpleAdminDivision();
        if (simpleAdminDivision == null) {
            String msg = "Can't find any geojson file for boundary search!";
            logger.error(msg);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound(msg));
        }

        if (location.trim().isEmpty() || location.indexOf(",") <= 0) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound("参数不合法，请检查参数！"));
        }

        String[] split = location.split(",");
        GeometryBuilder geometryBuilder = new GeometryBuilder();
        Point point = geometryBuilder.point(Double.parseDouble(split[0]), Double.parseDouble(split[1]));

        StringBuilder sb = new StringBuilder();
        AdministrativeDivisionNode leaf = getFullPath2Leaf(simpleAdminDivision, point, sb, langType, splitter);

        if (leaf == null) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.nok(ResponseMapUtil.STATUS_RESOURCE_OUT_OF_RANGE, "经纬度坐标超出范围！"));
        } else {
            Map<String, Object> result = new HashMap<>();
            result.put("id", leaf.getId());
            result.put("name", langType == 0 ? leaf.getName() : leaf.getNameEn());
            result.put("adminLevel", leaf.getAdminLevel());
            result.put("fullPath", sb.toString());

            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.ok(result));
        }
    }

    private AdministrativeDivisionNode getFullPath2Leaf(AdministrativeDivisionNode simpleAdminDivision, Point point, StringBuilder sb, int langType, String splitter) {
        AdministrativeDivisionNode leaf = null;
        if (simpleAdminDivision.getGeometry().covers(point)) {
            if (!sb.isEmpty()) {
                sb.append(splitter);
            }
            sb.append(langType == 0 ? simpleAdminDivision.getName() : simpleAdminDivision.getNameEn() + " ");
            if (!simpleAdminDivision.getChildren().isEmpty()) {
                for (AdministrativeDivisionNode child : simpleAdminDivision.getChildren()) {
                    AdministrativeDivisionNode childResult = getFullPath2Leaf(child, point, sb, langType, splitter);
                    if (childResult != null) {
                        leaf = childResult;
                    }
                }
            } else {
                leaf = simpleAdminDivision;
            }
        }
        return leaf;
    }
}