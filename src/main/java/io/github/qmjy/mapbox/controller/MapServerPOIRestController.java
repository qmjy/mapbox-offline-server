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

package io.github.qmjy.mapbox.controller;

import io.github.qmjy.mapbox.config.AppConfig;
import io.github.qmjy.mapbox.model.Poi;
import io.github.qmjy.mapbox.util.JdbcUtils;
import io.github.qmjy.mapbox.util.ResponseMapUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

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
     * @param tileset  待查询的瓦片数据文件名
     * @param keywords POI关键字。目前只支持单个关键词
     * @return 查询到到的POI搜索结果
     */
    @GetMapping(value = "/{tileset}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Operation(summary = "获取POI数据", description = "查询POI数据。")
    public ResponseEntity<Map<String, Object>> loadJpegTile(@PathVariable("tileset") String tileset, @RequestParam String keywords) {
        if (keywords.trim().isEmpty() || keywords.split(" ").length > 1) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound("参数不合法，请检查参数！"));
        }

        String poiFilePath = appConfig.getDataPath() + File.separator + "tilesets" + File.separator + tileset + ".idx";
        File pbfFile = new File(poiFilePath);
        if (pbfFile.exists()) {
            JdbcTemplate idxJdbcTemp = JdbcUtils.getInstance().getJdbcTemplate(appConfig.getDriverClassName(), poiFilePath);
            String sql = "SELECT * FROM poi WHERE name LIKE '%?%'";
            List<Poi> query = idxJdbcTemp.query(sql, (rs, rowNum) -> new Poi(rs.getString("name"), rs.getString("geometry"), rs.getInt("geometry_type")), keywords);

            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(null);
        } else {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound("Can't find POI data or POI index service not ready yet!"));
        }
    }
}
