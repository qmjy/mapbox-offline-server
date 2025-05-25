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

package io.github.qmjy.mapserver.config;


import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;


@Getter
@Component
@ConfigurationProperties
public class AppConfig {

    public static final String FILE_EXTENSION_NAME_MBTILES = ".mbtiles";
    /**
     * 矢量瓦片包，存储矢量数据而非栅格瓦片
     */
    public static final String FILE_EXTENSION_NAME_TPK = ".tpk";
    public static final String FILE_EXTENSION_NAME_VTPK = ".vtpk";
    /**
     * 地图包，包含地图文档和引用的数据
     */
    public static final String FILE_EXTENSION_NAME_MPK = ".mpk";
    /**
     * 定位器包，包含地理编码数据
     */
    public static final String FILE_EXTENSION_NAME_LPK = ".lpk";
    public static final String FILE_EXTENSION_NAME_SHP = ".shp";
    public static final String FILE_EXTENSION_NAME_PBF = ".pbf";
    public static final String FILE_EXTENSION_NAME_OSM_PBF = ".osm.pbf";
    public static final String FILE_EXTENSION_NAME_JSON = ".json";
    public static final String FILE_EXTENSION_NAME_GEOJSON = ".geojson";
    public static final String FILE_EXTENSION_NAME_PNG = ".png";
    public static final MediaType APPLICATION_X_PROTOBUF_VALUE = MediaType.valueOf("application/x-protobuf");

    /**
     * A String equivalent of {@link AppConfig#IMAGE_WEBP}.
     */
    public static final String IMAGE_WEBP_VALUE = "image/webp";

    /**
     * Public constant media type for {@code image/jpeg}.
     */
    public static final MediaType IMAGE_WEBP = MediaType.valueOf("image/webp");

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    /**
     * 数据文件存放路径
     */
    @Value("${data-path}")
    private String dataPath = "";

    @Value("${enable-default-tile}")
    private boolean enableDefaultTile;

    @Value("${osmwrangle.inputFormat}")
    private String inputFormat;

    @Value("${osmwrangle.mode}")
    private String mode;

    @Value("${osmwrangle.mapping_file}")
    private String mappingFile;

    @Value("${osmwrangle.tmpDir}")
    private String tmpDir;

    @Value("${osmwrangle.outputDir}")
    private String outputDir;


    /**
     * 从mvt的pbf提取poi数据
     */
    @Value("${enable-poi-extract-mvt}")
    private boolean enablePoiExtractMvt = false;


    /**
     * 从osm.pbf提取poi数据
     */
    @Value("${enable-poi-extract-osm-pbf}")
    private boolean enablePoiExtractOsmPbf = false;

    /**
     * 启用路径规划
     */
    @Value("${enable-planning}")
    private boolean enablePlanning = false;

    private AppConfig() {
    }
}
