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


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * GeoJson解析出来的原始行政区划数据模型
 *
 * @author liushaofeng
 */
@Getter
public class AdministrativeDivisionOrigin {
    @Schema(description = "行政区划节点ID", example = "-2110264")
    private final int id;
    @Schema(description = "行政区划父节点ID列表", example = "-913068,-270056")
    private final String parentsId;
    @Schema(description = "行政区划级别", example = "3")
    private final int adminLevel;
    @Schema(description = "行政区划本地名称", example = "成都市")
    private final String name;
    @Schema(description = "行政区划英文名称", example = "Chengdu")
    private final String nameEn;
    @Schema(description = "行政区划集合范围", example = "MULTIPOLYGON (((102.989623 30.768977, 103.8880439 31.4235321, 104.8948475 30.3805587, 103.393414 30.096723, 102.989623 30.768977), (103.433074 30.127017, 103.436041 30.121855, 103.44764 30.124618, 103.441397 30.129972, 103.433074 30.127017), (103.9459137 30.3085205, 103.9476461 30.3064626, 103.9587751 30.3107293, 103.9510199 30.3154977, 103.9459137 30.3085205)))")
    private final String geometry;
    @Schema(description = "行政区划中心点", example = "POINT(102.989623 30.768977)")
    private final String center;
    @Schema(description = "行政区划外接矩阵", example = "POINT(102.989623 30.768977)")
    private final String bounds;
    @Schema(description = "行政区划标签描述", example = "一些有用的数据")
    private final Map<String, Object> tags = new HashMap<>();

    public AdministrativeDivisionOrigin(int osmId, String parents, int adminLevel, String name, String nameEn, String[] geometry, Object tagsData) {
        this.id = osmId;
        this.parentsId = parents;
        this.adminLevel = adminLevel;
        this.name = name;
        this.nameEn = nameEn;
        this.geometry = geometry[0];
        this.center = geometry[1];
        this.bounds = geometry[2];
        JsonParser jsonParser = JsonParserFactory.getJsonParser();
        if (tagsData != null) {
            tags.putAll(jsonParser.parseMap(tagsData.toString()));
        }
    }
}
