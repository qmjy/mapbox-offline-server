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

package io.github.qmjy.mapbox.controller;

import io.github.qmjy.mapbox.MapServerDataCenter;
import io.github.qmjy.mapbox.util.ResponseMapUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.geometry.jts.GeometryBuilder;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 地理编码与逆编码接口
 *
 * @author liushaofeng
 */
@RestController
@RequestMapping("/api/geocode")
@Tag(name = "地理编码管理", description = "地理编码与逆编码服务接口能力")
public class MapServerGeoController {
    private static final Logger logger = LoggerFactory.getLogger(MapServerGeoController.class);

    /**
     * 地理编码
     *
     * @return 地理编码结果
     */
    @GetMapping("geo")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> geo() {
        Map<Integer, List<SimpleFeature>> administrativeDivisionLevel = MapServerDataCenter.getAdministrativeDivisionLevel();
        if (administrativeDivisionLevel.isEmpty()) {
            String msg = "Can't find any geojson file for boundary search!";
            logger.error(msg);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound(msg));
        } else {
            //TODO
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.ok("待实现..."));
        }
    }

    /**
     * 地理逆编码
     *
     * @param location 经度在前，纬度在后，经纬度间以“,”分割，经纬度小数点后不要超过 6 位。
     * @param langType 可选参数，支持本地语言(0:default)和英语(1)。
     * @return 地理逆编码结果
     */
    @Operation(summary = "地理逆编码查询", description = "通过经纬度查询行政区划概要信息，通过区划ID可获取行政区划详细信息。")
    @GetMapping("regeo")
    public ResponseEntity<Map<String, Object>> regeo(@RequestParam(value = "location") String location,
                                                     @RequestParam(value = "langType", required = false, defaultValue = "0") int langType) {
        Map<Integer, List<SimpleFeature>> administrativeDivisionLevel = MapServerDataCenter.getAdministrativeDivisionLevel();
        if (administrativeDivisionLevel.isEmpty()) {
            String msg = "Can't find any geojson file for boundary search!";
            logger.error(msg);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound(msg));
        }
        Integer[] array = administrativeDivisionLevel.keySet().toArray(new Integer[0]);
        Arrays.sort(array);
        for (int i = array.length - 1; i >= 0; i--) {
            Integer level = array[i];
            List<SimpleFeature> simpleFeatures = administrativeDivisionLevel.get(level);
            for (SimpleFeature simpleFeature : simpleFeatures) {
                Object geometry = simpleFeature.getAttribute("geometry");
                if (geometry instanceof MultiPolygon polygon) {
                    String[] split = location.split(",");
                    GeometryBuilder geometryBuilder = new GeometryBuilder();
                    Point point = geometryBuilder.point(Double.parseDouble(split[0]), Double.parseDouble(split[1]));
                    if (polygon.covers(point)) {

                        HashMap<Object, Object> data = new HashMap<>();
                        data.put("id", simpleFeature.getAttribute("osm_id"));
                        data.put("name", langType == 0 ? simpleFeature.getAttribute("local_name") : simpleFeature.getAttribute("name_en"));
                        data.put("adminLevel", simpleFeature.getAttribute("admin_level"));

                        Map<String, Object> ok = ResponseMapUtil.ok(data);
                        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ok);
                    }
                }
            }
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound());
    }
}
