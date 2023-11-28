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

import lombok.Builder;
import lombok.Data;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class AdministrativeDivisionModel {
    private int id;
    private String parentsId;
    private int adminLevel;
    private String name;
    private String nameEn;
    private String geometry;
    private final Map<String, Object> tags = new HashMap<>();

    public void setTags(String data) {
        JsonParser jsonParser = JsonParserFactory.getJsonParser();
        tags.putAll(jsonParser.parseMap(data));
    }
}
