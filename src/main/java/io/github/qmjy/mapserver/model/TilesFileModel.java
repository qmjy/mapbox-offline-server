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
import lombok.Setter;
import org.geotools.api.geometry.Position;
import org.geotools.tpk.TPKFile;
import org.geotools.tpk.TPKZoomLevel;
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
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 瓦片数据包文件模型
 *
 * @author liushaofeng
 */
@Getter
public class TilesFileModel {
    /**
     * 瓦片数据包文件格式: mbtiles
     */
    public static final int TILE_FILE_TYPE_OF_MBTILES = 1;
    /**
     * 瓦片数据包文件格式: tpk
     */
    public static final int TILE_FILE_TYPE_OF_TPK = 2;

    /**
     * 瓦片数据包文件格式: vtpk
     */
    public static final int TILE_FILE_TYPE_OF_VTPK = 3;

    private final Map<String, Object> metaDataMap = new HashMap<>();
    private final String name;
    private long tilesCount = -1;
    private long fileLength = 0L;
    //maptiler的数据是gzip压缩；bbbike的未被压缩；
    private boolean isCompressed = false;

    @JsonIgnore
    private final Logger logger = LoggerFactory.getLogger(TilesFileModel.class);
    @JsonIgnore
    private final String filePath;
    @JsonIgnore
    private JdbcTemplate jdbcTemplate;
    @Setter
    @JsonIgnore
    private TPKFile tpkFile;
    @Setter
    @JsonIgnore
    private Map<Long, TPKZoomLevel> zoomLevelMap;
    //瓦片数据包文件格式。 0：未知；
    @JsonIgnore
    private int tileFileType = 0;
    //文件是否支持，文件解析成功与否
    @JsonIgnore
    private boolean valid = false;

    /**
     * TPK解析
     *
     * @param tpk tpk数据包
     */
    public TilesFileModel(File tpk) {
        this.filePath = tpk.getAbsolutePath();
        this.fileLength = tpk.getAbsoluteFile().length();
        this.name = tpk.getName();
        boolean success = tryLoadMetaDataFromTpk();
        if (success) {
            this.tileFileType = 2;
            valid = true;
        }
    }

    /**
     * Mbtiles解析
     *
     * @param file      待解析的mbtiles文件
     * @param className 驱动类名
     */
    public TilesFileModel(File file, String className) {
        this.filePath = file.getAbsolutePath();
        this.fileLength = file.getAbsoluteFile().length();
        this.name = file.getName();

        initJdbc(className, file);
        tryLoadMetaDataFromMbtiles();
        if (tileFileType == 1) {
            countSize();
            this.isCompressed = compressed();
            valid = true;
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

    private boolean tryLoadMetaDataFromTpk() {
        zoomLevelMap = new HashMap<>();
        try {
            tpkFile = new TPKFile(new File(filePath), zoomLevelMap);

            //TODO 混合模式以后再支持
            if ("MIXED".equals(tpkFile.getImageFormat())) {
                return false;
            } else {
                this.metaDataMap.put("format", tpkFile.getImageFormat().toLowerCase(Locale.getDefault()));
                this.metaDataMap.put("minzoom", tpkFile.getMinZoomLevel());
                this.metaDataMap.put("maxzoom", tpkFile.getMaxZoomLevel());

                Position lowerCorner = tpkFile.getBounds().getLowerCorner();
                Position upperCorner = tpkFile.getBounds().getUpperCorner();

                //TODO 待验证顺序正确性,当前为左下+右上。
                this.metaDataMap.put("bounds", lowerCorner.getCoordinate()[0] + ","
                        + lowerCorner.getCoordinate()[1] + ","
                        + upperCorner.getCoordinate()[0] + ","
                        + upperCorner.getCoordinate()[1]);
                return true;
            }
        } catch (RuntimeException e) {
            logger.error("Read tpk failed: {}", e.getMessage());
            return false;
        }
    }

    private void tryLoadMetaDataFromMbtiles() {
        try {
            List<Map<String, Object>> mapList = jdbcTemplate.queryForList("SELECT * FROM metadata");
            for (Map<String, Object> map : mapList) {
                metaDataMap.put(String.valueOf(map.get("name")), map.get("value"));
            }
            this.tileFileType = 1;
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
