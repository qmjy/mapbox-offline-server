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

package io.github.qmjy.mapbox.util;

import io.github.qmjy.mapbox.model.DbFileModel;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 地图数据库服务工具
 */
@Component
public class MapDbServerUtils {
    private static final Map<String, DbFileModel> map = new HashMap<>();

    /**
     * 初始化数据源
     *
     * @param className 驱动名称
     * @param file      待链接的数据库文件
     */
    public static void initJdbcTemplate(String className, File file) {
        String dbUrl = "jdbc:sqlite:" + file.getAbsolutePath();
        DataSourceBuilder<?> dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.driverClassName(className);
        dataSourceBuilder.url(dbUrl);
        DbFileModel dbFileModel = new DbFileModel(file, dataSourceBuilder.build());
        map.put(file.getName(), dbFileModel);
    }

    /**
     * 通过文件名获取数据源
     *
     * @param fileName 数据库文件名称
     * @return 数据库数据源
     */
    public Optional<JdbcTemplate> getDataSource(String fileName) {
        if (StringUtils.hasLength(fileName)) {
            DbFileModel model = map.get(fileName);
            return Optional.of(model.getJdbcTemplate());
        } else {
            return Optional.empty();
        }
    }


    public String getFilePathMd5(String filePath) {
        return DigestUtils.md5DigestAsHex(filePath.getBytes());
    }
}
