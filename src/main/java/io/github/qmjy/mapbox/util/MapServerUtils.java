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

import io.github.qmjy.mapbox.model.FontsFileModel;
import io.github.qmjy.mapbox.model.TilesFileModel;
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
public class MapServerUtils {
    /**
     * 瓦片数据库文件模型
     */
    private static final Map<String, TilesFileModel> tilesMap = new HashMap<>();
    /**
     * 字体文件模型
     */
    private static final Map<String, FontsFileModel> fontsMap = new HashMap<>();

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
        TilesFileModel dbFileModel = new TilesFileModel(file, dataSourceBuilder.build());
        tilesMap.put(file.getName(), dbFileModel);
    }

    /**
     * 初始化字体库文件
     *
     * @param fontFolder 字体文件目录
     */
    public static void initFontsFile(File fontFolder) {
        fontsMap.put(fontFolder.getName(), new FontsFileModel(fontFolder));
    }

    /**
     * 通过文件名获取数据源
     *
     * @param fileName 数据库文件名称
     * @return 数据库数据源
     */
    public Optional<JdbcTemplate> getDataSource(String fileName) {
        if (StringUtils.hasLength(fileName)) {
            TilesFileModel model = tilesMap.get(fileName);
            return Optional.of(model.getJdbcTemplate());
        } else {
            return Optional.empty();
        }
    }


    /**
     * 返回瓦片数据库文件的元数据
     *
     * @param fileName 瓦片数据库文件名
     * @return 瓦片元数据
     */
    public Map<String, String> getTileMetaData(String fileName) {
        if (StringUtils.hasLength(fileName)) {
            TilesFileModel model = tilesMap.get(fileName);
            return model.getMetaDataMap();
        } else {
            return new HashMap<>();
        }
    }

    /**
     * 获取字符文件目录
     *
     * @param fontName 字体文件名
     * @return 字体文件目录
     */
    public Optional<FontsFileModel> getFontFolder(String fontName) {
        if (StringUtils.hasLength(fontName)) {
            FontsFileModel fontsFileModel = fontsMap.get(fontName);
            return Optional.of(fontsFileModel);
        } else {
            return Optional.empty();
        }
    }


    public String getFilePathMd5(String filePath) {
        return DigestUtils.md5DigestAsHex(filePath.getBytes());
    }
}
