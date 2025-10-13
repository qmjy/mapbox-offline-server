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
import io.github.qmjy.mapserver.model.AdministrativeDivision;
import io.github.qmjy.mapserver.model.AdministrativeDivisionOrigin;
import io.github.qmjy.mapserver.model.AdministrativeDivisionNode;
import io.github.qmjy.mapserver.util.ResponseMapUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.geojson.geom.GeometryJSON;
import org.geotools.geometry.jts.GeometryBuilder;
import org.locationtech.jts.geom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

/**
 * 行政区划获取接口
 *
 * @author liushaofeng
 */
@RestController
@RequestMapping("/api/geo/admins")
@Tag(name = "行政区划管理", description = "行政区划相关服务接口能力")
public class MapServerOsmBController {
    private static final Logger logger = LoggerFactory.getLogger(MapServerOsmBController.class);
    private final Map<String, AdministrativeDivision> cacheMap = new HashMap<>();
    private final MapServerDataCenter mapServerDataCenter = MapServerDataCenter.getInstance();

    /**
     * 获取行政区划数据，为空则从根节点开始
     *
     * @param nodeId 查询行政区划数据的根节点
     * @param lang   可选参数，支持本地语言(0:default)和英语(1)。
     * @return 行政区划节详情
     */
    @GetMapping("")
    @ResponseBody
    @Operation(summary = "获取省市区划级数据", description = "查询行政区划级联树数据。")
    @ApiResponse(responseCode = "200", description = "成功响应", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdministrativeDivision.class)))
    public ResponseEntity<Map<String, Object>> loadAdministrativeDivision(@Parameter(description = "行政区划的根节点") @RequestParam(value = "nodeId", required = false, defaultValue = "0") int nodeId,
                                                                          @Parameter(description = "支持本地语言(0: default)和英语(1)。") @RequestParam(value = "lang", required = false, defaultValue = "0") int lang,
                                                                          @Parameter(description = "是否递归包含子节点。不递归：0(default)和递归(1)。") @RequestParam(value = "recursion", required = false, defaultValue = "0") int recursion) {
        Map<Integer, List<SimpleFeature>> administrativeDivisionLevel = mapServerDataCenter.getAdministrativeDivisionLevel();
        if (administrativeDivisionLevel.isEmpty()) {
            String msg = "Can't find any geojson file for boundary search!";
            logger.error(msg);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound(msg));
        } else {
            if (lang > 1 || lang < 0) {
                return ResponseEntity.badRequest().build();
            }

            String key = nodeId + "-" + recursion + "-" + lang;
            if (cacheMap.containsKey(key)) {
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.ok(cacheMap.get(key)));
            }

            AdministrativeDivisionNode root = mapServerDataCenter.getSimpleAdminDivision();
            if (nodeId != 0) {
                if (mapServerDataCenter.getAdministrativeDivision().containsKey(nodeId)) {
                    Optional<AdministrativeDivisionNode> rootOpt = getRoot(mapServerDataCenter.getSimpleAdminDivision(), nodeId);
                    if (rootOpt.isPresent()) {
                        root = rootOpt.get();
                    }
                } else {
                    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound());
                }
            }

            AdministrativeDivision ad = new AdministrativeDivision(recursion == 0 ? root.clone() : root, lang);
            cacheMap.put(key, ad);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.ok(ad));
        }
    }


    private Optional<AdministrativeDivisionNode> getRoot(AdministrativeDivisionNode simpleAdminDivision, int nodeId) {
        if (simpleAdminDivision.getId() == nodeId) {
            return Optional.of(simpleAdminDivision);
        } else {
            List<AdministrativeDivisionNode> children = simpleAdminDivision.getChildren();
            for (AdministrativeDivisionNode child : children) {
                Optional<AdministrativeDivisionNode> rootOpt = getRoot(child, nodeId);
                if (rootOpt.isPresent()) {
                    return rootOpt;
                }
            }
        }
        return Optional.empty();
    }


    /**
     * 查询支持的节点行政区划节点ID。如果指定父ID则查询所有的子行政区划，否则查询全部。
     *
     * @param nodeId 父节点
     * @return 行政区划ID和名称
     */
    @GetMapping("/nodes")
    @ResponseBody
    @Operation(summary = "获取省市区划节点ID列表", description = "查询行政区划节点ID列表。")
    @ApiResponse(responseCode = "200", description = "成功响应", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    public ResponseEntity<Map<String, Object>> loadAdministrativeDivisionNodes(@Parameter(description = "行政区划的根节点") @RequestParam(value = "nodeId", required = false, defaultValue = "0") int nodeId) {
        if (mapServerDataCenter.getAdministrativeDivisionLevel().isEmpty()) {
            String msg = "Can't find any geojson file for boundary search!";
            logger.error(msg);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound(msg));
        }
        AdministrativeDivisionNode root = mapServerDataCenter.getSimpleAdminDivision();
        if (nodeId != 0) {
            Optional<AdministrativeDivisionNode> rootOpt = getRoot(mapServerDataCenter.getSimpleAdminDivision(), nodeId);
            if (rootOpt.isPresent()) {
                root = rootOpt.get();
            }
        }
        Map<Integer, String> resultMap = new LinkedHashMap<>();
        reWrap2List(root, resultMap);
        Map<String, Object> ok = ResponseMapUtil.ok(resultMap);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ok);
    }

    private void reWrap2List(AdministrativeDivisionNode root, Map<Integer, String> resultMap) {
        resultMap.put(root.getId(), root.getName());
        root.getChildren().forEach(item -> {
            reWrap2List(item, resultMap);
        });
    }


    /**
     * 查询指定节点行政区划明细数据
     *
     * @param nodeId 父节点
     * @param type   返回的行政区划边界数据格式，默认为type为0返回WKT，1返回geojson
     * @return 行政区划数据
     */
    @GetMapping("/nodes/{nodeId}")
    @ResponseBody
    @Operation(summary = "获取省市区划节点详情数据", description = "查询行政区划节点详情数据。")
    @ApiResponse(responseCode = "200", description = "成功响应", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdministrativeDivisionOrigin.class)))
    public ResponseEntity<Map<String, Object>> loadAdministrativeDivisionNode(@Parameter(description = "行政区划节点ID，例如：-2110264。") @PathVariable Integer nodeId,
                                                                              @Parameter(description = "返回的边界数据格式。0：WKT；1:geojson") @RequestParam(value = "type", required = false, defaultValue = "0") int type
    ) {
        if (mapServerDataCenter.getAdministrativeDivisionLevel().isEmpty()) {
            String msg = "Can't find any geojson file for boundary search!";
            logger.error(msg);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound(msg));
        }
        if (nodeId != null) {
            Map<Integer, SimpleFeature> administrativeDivision = mapServerDataCenter.getAdministrativeDivision();
            if (administrativeDivision.containsKey(nodeId)) {
                SimpleFeature simpleFeature = administrativeDivision.get(nodeId);

                int osmId = (int) simpleFeature.getAttribute("osm_id");
                String name = (String) simpleFeature.getAttribute("local_name");
                String nameEn = (String) simpleFeature.getAttribute("name_en");
                String parents = (String) simpleFeature.getAttribute("parents");
                Object geometry = simpleFeature.getAttribute("geometry");
                Object tags = simpleFeature.getAttribute("all_tags");
                int adminLevel = (int) simpleFeature.getAttribute("admin_level");

                AdministrativeDivisionOrigin data = new AdministrativeDivisionOrigin(osmId, parents, adminLevel, name,
                        nameEn, getGeometryStrs(geometry, type), tags);
                Map<String, Object> ok = ResponseMapUtil.ok(data);
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ok);
            }
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound());
    }

    private String[] getGeometryStrs(Object geometryObj, int type) {
        Point centroid = null;
        if (geometryObj instanceof Geometry geometry) {
            centroid = geometry.getCentroid();

            GeometryJSON geoJsonWriter = new GeometryJSON();
            StringWriter writer = new StringWriter();
            try {
                geoJsonWriter.write(geometry, writer);
                Envelope envelopeInternal = geometry.getEnvelopeInternal();
                return new String[]{writer.toString(), centroid.getCoordinate().getX() + "," + centroid.getCoordinate().getY(),
                        envelopeInternal.getMinX() + "," + envelopeInternal.getMinY() + "," + envelopeInternal.getMaxX() + "," + envelopeInternal.getMaxY()};
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return new String[]{geometryObj.toString(), centroid.getCoordinate().getX() + "," + centroid.getCoordinate().getY()};
    }

    /**
     * 判断经纬度坐标是否在行政区划范围内
     *
     * @param nodeId    行政区划节点ID
     * @param locations 待判断的经纬度坐标,多个参数用“;”分割
     * @return 此行政区划是否包含此经纬度点
     */
    @GetMapping("/nodes/{nodeId}/contains")
    @ResponseBody
    @ApiResponse(responseCode = "200", description = "成功响应", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    @Operation(summary = "判断经纬度坐标是否在某个行政区划范围内", description = "判断经纬度坐标是否在某个行政区划范围内。")
    public ResponseEntity<Map<String, Object>> contains(@Parameter(description = "行政区划节点ID，例如：-2110264。") @PathVariable Integer nodeId, @Parameter(description = "待判断的经纬度坐标，多个参数用“;”分割。例如：104.071883,30.671974;104.071823,30.671374") @RequestParam(value = "locations") String locations) {
        if (mapServerDataCenter.getAdministrativeDivisionLevel().isEmpty()) {
            String msg = "Can't find any geojson file for boundary search!";
            logger.error(msg);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound(msg));
        }
        if (nodeId != null) {
            Map<Integer, SimpleFeature> administrativeDivision = mapServerDataCenter.getAdministrativeDivision();
            if (administrativeDivision.containsKey(nodeId)) {
                SimpleFeature simpleFeature = administrativeDivision.get(nodeId);
                Object geometry = simpleFeature.getAttribute("geometry");
                HashMap<String, Boolean> resultMap = getStringBooleanHashMap(locations, geometry);
                Map<String, Object> ok = ResponseMapUtil.ok(resultMap);
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ok);
            }
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound());
    }

    private static HashMap<String, Boolean> getStringBooleanHashMap(String locations, Object geometry) {
        Geometry geo;
        if (geometry instanceof MultiPolygon polygon) {
            geo = polygon;
        } else if (geometry instanceof Polygon polygon) {
            geo = polygon;
        } else {
            return new HashMap<>();
        }
        String[] groups = locations.split(";");
        HashMap<String, Boolean> resultMap = new HashMap<>();
        for (String location : groups) {
            String[] split = location.split(",");
            GeometryBuilder geometryBuilder = new GeometryBuilder();
            Point point = geometryBuilder.point(Double.parseDouble(split[0]), Double.parseDouble(split[1]));
            resultMap.put(location, geo.covers(point));
        }
        return resultMap;
    }
}
