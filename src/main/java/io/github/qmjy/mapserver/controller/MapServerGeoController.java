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
import io.github.qmjy.mapserver.model.dto.GeoReferencerReqDTO;
import io.github.qmjy.mapserver.util.ImageGeoreferencer;
import io.github.qmjy.mapserver.util.ResponseMapUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.Position2D;
import org.geotools.geometry.jts.GeometryBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

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
    @PostMapping("/georeferencer")
    @ResponseBody
    public ResponseEntity<Map<String, Position2D>> georeferencer(@RequestBody GeoReferencerReqDTO referencerReqDTO) {
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
            Map<String, Position2D> position2DS = imageGeoreferencer.transformImageToGeo(getPixels(referencerReqDTO.getPixelPoints()));
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(position2DS);
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
     * 地理逆编码
     *
     * @param location 经度在前，纬度在后，经纬度间以“,”分割，经纬度小数点后不要超过 6 位。
     * @param langType 可选参数，支持本地语言(0:default)和英语(1)。
     * @param splitter 各行政区划节点之间的分割符。
     * @return 地理逆编码结果
     */
    @Operation(summary = "地理逆编码查询", description = "通过经纬度查询行政区划概要信息，通过区划ID可获取行政区划详细信息。")
    @GetMapping("/geocode/regeo")
    public ResponseEntity<Map<String, Object>> regeo(@Parameter(description = "待查询的经纬度坐标，例如：104.071883,30.671974") @RequestParam(value = "location") String location,
                                                     @Parameter(description = "返回的数据语言。0：本地语言（default）；1：英语") @RequestParam(value = "langType", required = false, defaultValue = "0") int langType,
                                                     @Parameter(description = "各行政区划节点之间的分割符。默认本地语言无分隔符，英文为空格。") @RequestParam(value = "splitter", required = false, defaultValue = "") String splitter) {
        Map<Integer, List<SimpleFeature>> administrativeDivisionLevel = MapServerDataCenter.getAdministrativeDivisionLevel();
        if (administrativeDivisionLevel.isEmpty()) {
            String msg = "Can't find any geojson file for boundary search!";
            logger.error(msg);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound(msg));
        }

        if (location.trim().isEmpty() || location.indexOf(",") <= 0) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound("参数不合法，请检查参数！"));
        }

        Integer[] array = administrativeDivisionLevel.keySet().toArray(new Integer[0]);
        Arrays.sort(array);
        for (int i = array.length - 1; i >= 0; i--) {
            Integer level = array[i];
            List<SimpleFeature> simpleFeatures = administrativeDivisionLevel.get(level);
            for (SimpleFeature simpleFeature : simpleFeatures) {
                Object geometry = simpleFeature.getAttribute("geometry");
                if (geometry instanceof Polygon || geometry instanceof MultiPolygon) {
                    Map<String, Object> result = wrapData(simpleFeature, location, (Geometry) geometry, langType, splitter);
                    if (!result.isEmpty()) {
                        return ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(result);
                    }
                }
            }
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound());
    }


    private Map<String, Object> wrapData(SimpleFeature simpleFeature, String location, Geometry polygon, int langType, String splitter) {
        String[] split = location.split(",");
        GeometryBuilder geometryBuilder = new GeometryBuilder();
        Point point = geometryBuilder.point(Double.parseDouble(split[0]), Double.parseDouble(split[1]));
        if (polygon.covers(point)) {
            String parentPath = getParentFullPath(simpleFeature, langType, splitter);

            HashMap<Object, Object> data = new HashMap<>();
            data.put("id", simpleFeature.getAttribute("osm_id"));
            data.put("name", langType == 0 ? simpleFeature.getAttribute("local_name") : simpleFeature.getAttribute("name_en"));
            data.put("adminLevel", simpleFeature.getAttribute("admin_level"));
            data.put("fullPath", parentPath + data.get("name"));

            return ResponseMapUtil.ok(data);
        }
        return new HashMap<>();
    }

    private String getParentFullPath(SimpleFeature simpleFeature, int langType, String splitter) {
        String parentsString = (String) simpleFeature.getAttribute("parents");
        String[] parents = parentsString.split(",");
        StringBuilder sb = new StringBuilder();
        for (int j = parents.length - 1; j >= 0; j--) {
            SimpleFeature feature = MapServerDataCenter.getAdministrativeDivision().get(Integer.parseInt(parents[j]));
            if (splitter == null || splitter.trim().isEmpty()) {
                sb.append(langType == 0 ? feature.getAttribute("local_name") : feature.getAttribute("name_en") + " ");
            } else {
                sb.append(langType == 0 ? feature.getAttribute("local_name") : feature.getAttribute("name_en")).append(splitter);
            }
        }
        return sb.toString();
    }
}