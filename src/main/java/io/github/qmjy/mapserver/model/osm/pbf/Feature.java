/*
 * Copyright (c) 2024 QMJY.
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

package io.github.qmjy.mapserver.model.osm.pbf;

import lombok.Data;
import org.locationtech.jts.geom.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class Feature {
    private String type = "Feature";
    private Map<String, Object> geometry = new HashMap<>();
    private LinkedHashMap<?, ?> properties;

    public Feature(Geometry value) {
        switch (value.getGeometryN(0)) {
            case Point point -> {
                geometry.put("type", "Point");
                geometry.put("coordinates", point.toString());
            }
            case LineString lineString -> {
                geometry.put("type", "LineString");
                geometry.put("coordinates", null);
            }
            case Polygon polygon -> {
                geometry.put("type", "Polygon");
                geometry.put("coordinates", null);
            }
            case MultiPolygon multiPolygon -> {
                geometry.put("type", "MultiPolygon");
                geometry.put("coordinates", null);
            }
            case MultiLineString multiLineString -> {
                geometry.put("type", "MultiLineString");
                geometry.put("coordinates", null);
            }
            case null, default -> System.out.println();
        }
        if (value.getUserData() instanceof LinkedHashMap<?, ?> hashMap) {
            properties = hashMap;
        }
    }
}
