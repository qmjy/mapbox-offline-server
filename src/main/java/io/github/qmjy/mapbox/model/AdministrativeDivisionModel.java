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


import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;

import java.util.HashMap;
import java.util.Map;

public class AdministrativeDivisionModel {
    private final int id;
    private final String parentsId;
    private final int adminLevel;
    private final String name;
    private final String nameEn;
    private final String geometry;
    private final Map<String, Object> tags = new HashMap<>();

    public AdministrativeDivisionModel(int osmId, String parents, int adminLevel, String name, String nameEn, String geometry, String tagsData) {
        this.id = osmId;
        this.parentsId = parents;
        this.adminLevel = adminLevel;
        this.name = name;
        this.nameEn = nameEn;
        this.geometry = geometry;
        JsonParser jsonParser = JsonParserFactory.getJsonParser();
        tags.putAll(jsonParser.parseMap(tagsData));
    }

    public int getId() {
        return id;
    }

    public String getParentsId() {
        return parentsId;
    }

    public int getAdminLevel() {
        return adminLevel;
    }

    public String getName() {
        return name;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getGeometry() {
        return geometry;
    }

    public Map<String, Object> getTags() {
        return tags;
    }
}
