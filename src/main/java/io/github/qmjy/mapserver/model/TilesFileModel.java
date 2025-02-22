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

package io.github.qmjy.mapserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.qmjy.mapserver.util.JdbcUtils;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Mbtiles瓦片数据文件模型
 *
 * @author liushaofeng
 */
@Getter
public class TilesFileModel {
    @JsonIgnore
    private final Logger logger = LoggerFactory.getLogger(TilesFileModel.class);
    @JsonIgnore
    private final String filePath;
    private final String name;
    private final Map<String, Object> metaDataMap = new HashMap<>();
    @JsonIgnore
    private JdbcTemplate jdbcTemplate;
    private long tilesCount = -1;
    @JsonIgnore
    private boolean isMbtiles = false;
    //maptiler的数据是gzip压缩；bbbike的未被压缩；
    private boolean isCompressed = false;

    public TilesFileModel(File file, String className) {
        this.filePath = file.getAbsolutePath();
        this.name = file.getName();
        
        initJdbc(className, file);
        tryLoadMetaData();
        if (isMbtiles) {
            countSize();
            this.isCompressed = compressed();
        }
    }

    public void countSize() {
        String sql = "SELECT COUNT(*) AS count FROM tiles";
        Map<String, Object> result = jdbcTemplate.queryForMap(sql);
        tilesCount = (int) result.get("count");
    }

    private void initJdbc(String className, File file) {
        this.jdbcTemplate = JdbcUtils.getInstance().getJdbcTemplate(className, file.getAbsolutePath());
    }

    private void tryLoadMetaData() {
        try {
            List<Map<String, Object>> mapList = jdbcTemplate.queryForList("SELECT * FROM metadata");
            for (Map<String, Object> map : mapList) {
                metaDataMap.put(String.valueOf(map.get("name")), map.get("value"));
            }
            isMbtiles = true;
        } catch (DataAccessException e) {
            logger.error("Load map meta data failed: {}", filePath);
        }
    }

    private boolean compressed() {
        String sql = "SELECT tile_data FROM tiles limit 1";
        try {
            byte[] data = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> rs.getBytes(1));
            if (data == null || data.length < 2) {
                return false;
            }
            new GZIPInputStream(new ByteArrayInputStream(data));
            // 如果能顺利创建GZIPInputStream，并且没有抛出IOException，那么这很可能是GZIP压缩的数据
            return true;
        } catch (EmptyResultDataAccessException | IOException e) {
            // 如果在创建GZIPInputStream时发生异常，这很可能不是一个有效的GZIP流
            return false;
        }
    }
}
