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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 行政区划获取接口
 *
 * @author liushaofeng
 */
@RestController
@RequestMapping("/api/admins")
public class MapServerOsmBController {
    @Autowired
    private MapServerDataCenter mapServerDataCenter;

    /**
     * 获取行政区划数据，为空则从根节点开始
     *
     * @param parentId  父节点
     * @param recursion 是否递归。 0: false(default)、1: true;
     * @return 行政区划节详情
     */
    @GetMapping("")
    @ResponseBody
    public Object loadAdministrativeDivision(@RequestParam(required = false) Integer parentId, @RequestParam(required = false, defaultValue = "0") Integer recursion) {
        Map<String, List<SimpleFeature>> administrativeDivisionLevel = MapServerDataCenter.getAdministrativeDivisionLevel();
        Map<Integer, SimpleFeature> administrativeDivision = MapServerDataCenter.getAdministrativeDivision();
        if (administrativeDivision.containsKey(parentId)) {
            SimpleFeature simpleFeature = administrativeDivision.get(parentId);

            Object osmId = simpleFeature.getAttribute("osm_id");
            Object name = simpleFeature.getAttribute("local_name");
            Object nameEn = simpleFeature.getAttribute("name_en");
            Object parents = simpleFeature.getAttribute("parents");
            Object geometry = simpleFeature.getAttribute("geometry");
            Object tags = simpleFeature.getAttribute("all_tags");
            Object adminLevel = simpleFeature.getAttribute("admin_level");

            AdministrativeDivisionModel build = AdministrativeDivisionModel.builder()
                    .id((int) osmId)
                    .parentsId(String.valueOf(parents))
                    .adminLevel((int) adminLevel)
                    .name(String.valueOf(name))
                    .nameEn(String.valueOf(nameEn))
                    .geometry(String.valueOf(geometry))
                    .build();
            build.setTags(String.valueOf(tags));
            return build;
        }
        return new HashMap<String, String>();
    }
}
