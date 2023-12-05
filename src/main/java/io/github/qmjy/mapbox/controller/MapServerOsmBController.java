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

package io.github.qmjy.mapbox.controller;

import io.github.qmjy.mapbox.MapServerDataCenter;
import io.github.qmjy.mapbox.model.AdministrativeDivisionModel;
import org.geotools.api.feature.simple.SimpleFeature;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 行政区划获取接口
 *
 * @author liushaofeng
 */
@RestController
@RequestMapping("/api/geo/admins")
public class MapServerOsmBController {

    /**
     * 获取行政区划数据，为空则从根节点开始
     *
     * @return 行政区划节详情
     */
    @GetMapping("")
    @ResponseBody
    public Object loadAdministrativeDivision() {
        //TODO 目录未配置提示
        return MapServerDataCenter.getSimpleAdminDivision();
    }

    /**
     * 查询指定节点行政区划明细数据
     *
     * @param nodeId 父节点
     * @return 行政区划数据
     */
    @GetMapping("/nodes/{nodeId}")
    @ResponseBody
    public Object loadAdministrativeDivisionNode(@PathVariable Integer nodeId) {
        //TODO 目录未配置提示
        if (nodeId != null) {
            Map<Integer, SimpleFeature> administrativeDivision = MapServerDataCenter.getAdministrativeDivision();
            if (administrativeDivision.containsKey(nodeId)) {
                SimpleFeature simpleFeature = administrativeDivision.get(nodeId);

                int osmId = (int) simpleFeature.getAttribute("osm_id");
                String name = (String) simpleFeature.getAttribute("local_name");
                String nameEn = (String) simpleFeature.getAttribute("name_en");
                String parents = (String) simpleFeature.getAttribute("parents");
                Object geometry = simpleFeature.getAttribute("geometry");
                Object tags = simpleFeature.getAttribute("all_tags");
                int adminLevel = (int) simpleFeature.getAttribute("admin_level");

                return new AdministrativeDivisionModel(
                        osmId, parents, adminLevel, name, nameEn, String.valueOf(geometry), String.valueOf(tags));
            }
        }
        return new HashMap<String, String>();
    }
}
