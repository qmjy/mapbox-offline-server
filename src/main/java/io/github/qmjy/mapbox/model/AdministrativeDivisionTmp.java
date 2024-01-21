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

import lombok.Getter;
import lombok.Setter;
import org.geotools.api.feature.simple.SimpleFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * 行政区划数据，树形结构
 *
 * @author liushaofeng
 */
@Getter
public class AdministrativeDivisionTmp {
    private final int id;
    private final int parentId;
    private final String name;
    private final String nameEn;
    private final int adminLevel;
    @Setter
    private List<AdministrativeDivisionTmp> children = new ArrayList<>();

    public AdministrativeDivisionTmp(SimpleFeature simpleFeature, int parentId) {
        this.id = (int) simpleFeature.getAttribute("osm_id");
        this.name = (String) simpleFeature.getAttribute("local_name");
        Object nameEnObj = simpleFeature.getAttribute("name_en");
        this.nameEn = nameEnObj == null ? "" : String.valueOf(nameEnObj);
        this.parentId = parentId;
        this.adminLevel = simpleFeature.getAttribute("admin_level") == null ? -1 : (int) simpleFeature.getAttribute("admin_level");
    }
}
