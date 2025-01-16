/*
 * Copyright (c) 2024 QMJY.
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

import io.github.qmjy.mapserver.config.AppConfig;
import io.github.qmjy.mapserver.model.PoiPoint;
import io.github.qmjy.mapserver.util.GeometryUtils;
import io.github.qmjy.mapserver.util.JdbcUtils;
import io.github.qmjy.mapserver.util.ResponseMapUtil;
import io.github.qmjy.mapserver.util.SystemUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * POI相关访问接口
 *
 * @author liushaofeng
 */
@RestController
@RequestMapping("/api/poi")
@Tag(name = "地图POI服务管理", description = "地图POI服务接口能力")
public class MapServerPOIRestController {
    private static final Logger logger = LoggerFactory.getLogger(MapServerPOIRestController.class);
    private final AppConfig appConfig;

    public MapServerPOIRestController(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /**
     * POI搜索
     *
     * @param keywords POI关键字。目前只支持单个关键词
     * @param pageSize 返回的结果条数
     * @return 查询到到的POI搜索结果
     */
    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Operation(summary = "获取POI数据", description = "查询POI数据。")
    public ResponseEntity<Map<String, Object>> poiSearch(@Parameter(description = "待查询POI关键字，目前只支持一个关键词") @RequestParam String keywords, @Parameter(description = "返回的POI结果条数,取值范围为1-100") @RequestParam(required = false, defaultValue = "10") int pageSize) {
        if (keywords.trim().isEmpty() || keywords.split(" ").length > 1) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound("参数不合法，请检查参数！"));
        }

        File poiIndexFile = getPoiFile();
        if (poiIndexFile.exists()) {
            JdbcTemplate idxJdbcTemp = JdbcUtils.getInstance().getJdbcTemplate(appConfig.getDriverClassName(), poiIndexFile.getAbsolutePath());
            String sql = "SELECT * FROM poi WHERE name LIKE ? LIMIT " + (pageSize <= 0 || pageSize > 100 ? 10 : pageSize);
            List<Map<String, Object>> maps = idxJdbcTemp.queryForList(sql, "%" + keywords + "%");
            List<PoiPoint> dataList = new ArrayList<>();
            maps.forEach(stringObjectMap -> {
                dataList.add(new PoiPoint((String) stringObjectMap.get("name"), stringObjectMap.get("lon") + "," + stringObjectMap.get("lat")));
            });
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.ok(dataList));
        } else {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound("Can't find POI data or POI index service not ready yet!"));
        }
    }

    private File getPoiFile() {
        File[] files = new File(appConfig.getDataPath() + File.separator + "poi" + File.separator).listFiles((dir, name) -> name.endsWith(".poi"));
        if (files != null && files.length > 0) {
            return files[0];
        } else {
            logger.error("Can't find POI data file!");
            return new File("");
        }
    }

    /**
     * POI搜索
     *
     * @param poiFile  待查询的瓦片数据文件名或POI文件名，例如chengdu.mbtiles或者chengdu.poi
     * @param keywords POI关键字。目前只支持单个关键词
     * @param pageSize 返回的结果条数
     * @return 查询到到的POI搜索结果
     */
    @GetMapping(value = "/{poiIndexFile}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Operation(summary = "获取POI数据", description = "指定检索文件来查询POI数据。")
    public ResponseEntity<Map<String, Object>> poiSearchByFile(@Parameter(description = "查询POI数据的矢量瓦片数据源或POI文件名，例如：Chengdu.mbtiles | Chengdu.poi") @PathVariable("poiIndexFile") String poiFile, @Parameter(description = "待查询POI关键字，目前只支持一个关键词") @RequestParam String keywords, @Parameter(description = "返回的POI结果条数,取值范围为1-100") @RequestParam(required = false, defaultValue = "10") int pageSize) {
        if (keywords.trim().isEmpty() || keywords.split(" ").length > 1 || SystemUtils.checkTilesetName(poiFile)) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound("参数不合法，请检查参数！"));
        }

        String filePath = poiFile.endsWith(".poi") ? appConfig.getDataPath() + File.separator + "poi" + File.separator + poiFile : appConfig.getDataPath() + File.separator + "tilesets" + File.separator + poiFile + ".idx";
        File poiIndexFile = new File(filePath);
        if (poiIndexFile.exists()) {
            JdbcTemplate idxJdbcTemp = JdbcUtils.getInstance().getJdbcTemplate(appConfig.getDriverClassName(), filePath);
            String sql = "SELECT * FROM poi WHERE name LIKE ? LIMIT " + (pageSize <= 0 || pageSize > 100 ? 10 : pageSize);
            List<Map<String, Object>> maps = idxJdbcTemp.queryForList(sql, "%" + keywords + "%");
            List<PoiPoint> dataList = new ArrayList<>();
            maps.forEach(stringObjectMap -> {
                dataList.add(formatPoiPoint(stringObjectMap, poiFile.endsWith(".poi")));
            });
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.ok(dataList));
        } else {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound("Can't find POI data or POI index service not ready yet!"));
        }
    }

    private PoiPoint formatPoiPoint(Map<String, Object> stringObjectMap, boolean isPoi) {
        String name = (String) stringObjectMap.get("name");
        if (isPoi) {
            return new PoiPoint(name, stringObjectMap.get("lon") + "," + stringObjectMap.get("lat"));
        } else {
            String wellKnownText = (String) stringObjectMap.get("geometry");
            int geometryType = (int) stringObjectMap.get("geometry_type");
            int tileRow = (int) stringObjectMap.get("tile_row");
            int tileColumn = (int) stringObjectMap.get("tile_column");
            int zoomLevel = (int) stringObjectMap.get("zoom_level");

            //TODO 目前就只先保存点类型的数据
            switch (geometryType) {
                case 0:
                    Optional<Geometry> geometryOpt = GeometryUtils.toGeometryFromWkt(wellKnownText);
                    if (geometryOpt.isPresent()) {
                        Point point = (Point) geometryOpt.get();
                        double[] doubles = GeometryUtils.pixel2deg(tileColumn, tileRow, zoomLevel, (int) point.getX(), (int) point.getY(), 4096);
                        return new PoiPoint(name, doubles[0] + "," + doubles[1]);
                    }
                default:
                    return new PoiPoint(name, null);
            }
        }
    }
}
