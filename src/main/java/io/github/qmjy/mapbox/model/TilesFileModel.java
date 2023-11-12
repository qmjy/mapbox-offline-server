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

package io.github.qmjy.mapbox.model;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mbtiles瓦片数据文件模型
 */
@Data
public class TilesFileModel {
    private final Logger logger = LoggerFactory.getLogger(TilesFileModel.class);

    private String name;
    private String filePath;
    private long fileLastModifiedTime = 0;
    private JdbcTemplate jdbcTemplate;
    private Map<String, String> metaDataMap = new HashMap<>();

    public TilesFileModel(File file, String className) {
        this.name = file.getName();
        this.filePath = file.getAbsolutePath();
        this.fileLastModifiedTime = file.lastModified();

        initJdbc(className, file);
        loadMetaData();
    }

    private void initJdbc(String className, File file) {
        DataSourceBuilder<?> ds = DataSourceBuilder.create();
        ds.driverClassName(className);
        ds.url("jdbc:sqlite:" + file.getAbsolutePath());
        this.jdbcTemplate = new JdbcTemplate(ds.build());
    }

    private void loadMetaData() {
        try {
            List<Map<String, Object>> mapList = jdbcTemplate.queryForList("SELECT * FROM metadata");
            for (Map<String, Object> map : mapList) {
                metaDataMap.put(String.valueOf(map.get("name")), String.valueOf(map.get("value")));
            }
        } catch (DataAccessException e) {
            logger.error("Load map meta data failed: {}", filePath);
        }
    }
}
