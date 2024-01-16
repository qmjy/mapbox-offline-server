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

package io.github.qmjy.mapbox.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties
public class AppConfig {

    public static final String FILE_EXTENSION_NAME_MBTILES = ".mbtiles";
    public static final String FILE_EXTENSION_NAME_TPK = ".tpk";
    public static final String FILE_EXTENSION_NAME_PBF = ".pbf";
    public static final String FILE_EXTENSION_NAME_JSON = ".json";
    public static final String FILE_EXTENSION_NAME_GEOJSON = ".geojson";
    public static final String FILE_EXTENSION_NAME_PNG = ".png";

    public static final MediaType APPLICATION_X_PROTOBUF_VALUE = MediaType.valueOf("application/x-protobuf");

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    /**
     * 数据文件存放路径
     */
    @Value("${data-path}")
    private String dataPath = "";


    public String getDriverClassName() {
        return driverClassName;
    }

    public String getDataPath() {
        return dataPath;
    }
}
