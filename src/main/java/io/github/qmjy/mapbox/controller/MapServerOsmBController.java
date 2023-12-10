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
import io.github.qmjy.mapbox.model.AdministrativeDivision;
import io.github.qmjy.mapbox.model.AdministrativeDivisionOrigin;
import io.github.qmjy.mapbox.model.AdministrativeDivisionTmp;
import io.github.qmjy.mapbox.util.ResponseMapUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.geotools.api.feature.simple.SimpleFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final Map<Integer, AdministrativeDivision> cacheMap = new HashMap<>();

    /**
     * 获取行政区划数据，为空则从根节点开始
     *
     * @param lang 可选参数，支持本地语言(0:default)和英语(1)。
     * @return 行政区划节详情
     */
    @GetMapping("")
    @ResponseBody
    @Operation(summary = "获取省市区划级数据", description = "查询行政区划级联树数据。")
    @ApiResponse(responseCode = "200", description = "成功响应", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdministrativeDivision.class)))
    public ResponseEntity<Map<String, Object>> loadAdministrativeDivision(@Parameter(description = "支持本地语言(0: default)和英语(1)。") @RequestParam(value = "lang", required = false, defaultValue = "0") int lang) {
        Map<Integer, List<SimpleFeature>> administrativeDivisionLevel = MapServerDataCenter.getAdministrativeDivisionLevel();
        if (administrativeDivisionLevel.isEmpty()) {
            logger.error("Can't find any geojson file for boundary search!");
            return ResponseEntity.notFound().build();
        } else {
            if (lang > 1 || lang < 0) {
                return ResponseEntity.badRequest().build();
            }
            AdministrativeDivisionTmp adminDivision = MapServerDataCenter.getSimpleAdminDivision();
            if (cacheMap.containsKey(lang)) {
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.ok(cacheMap.get(lang)));
            }
            AdministrativeDivision ad = new AdministrativeDivision(adminDivision, lang);
            cacheMap.put(lang, ad);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.ok(ad));
        }
    }

    /**
     * 查询指定节点行政区划明细数据
     *
     * @param nodeId 父节点
     * @return 行政区划数据
     */
    @GetMapping("/nodes/{nodeId}")
    @ResponseBody
    @Operation(summary = "获取省市区划节点详情数据", description = "查询行政区划节点详情数据。")
    @ApiResponse(responseCode = "200", description = "成功响应", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdministrativeDivisionOrigin.class)))
    public ResponseEntity<Map<String, Object>> loadAdministrativeDivisionNode(@Parameter(description = "行政区划节点ID，例如：-7668313。") @PathVariable Integer nodeId) {
        Map<Integer, List<SimpleFeature>> administrativeDivisionLevel = MapServerDataCenter.getAdministrativeDivisionLevel();
        if (administrativeDivisionLevel.isEmpty()) {
            logger.error("Can't find any geojson file for boundary search!");
            return ResponseEntity.notFound().build();
        }
        if (nodeId != null) {
            Map<Integer, SimpleFeature> administrativeDivision = MapServerDataCenter.getAdministrativeDivision();
            if (administrativeDivision.containsKey(nodeId)) {
                SimpleFeature simpleFeature = administrativeDivision.get(nodeId);

                int osmId = (int) simpleFeature.getAttribute("osm_id");
                String name = (String) simpleFeature.getAttribute("local_name");
                String nameEn = (String) simpleFeature.getAttribute("name_en");
                String parents = (String) simpleFeature.getAttribute("parents");
                Object geometry = simpleFeature.getAttribute("geometry");
                Object tags = simpleFeature.getAttribute("all_tags");
                int adminLevel = (int) simpleFeature.getAttribute("admin_level");

                AdministrativeDivisionOrigin data = new AdministrativeDivisionOrigin(
                        osmId, parents, adminLevel, name, nameEn, String.valueOf(geometry), String.valueOf(tags));
                Map<String, Object> ok = ResponseMapUtil.ok(data);
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ok);
            }
        }
        return ResponseEntity.notFound().build();
    }
}
