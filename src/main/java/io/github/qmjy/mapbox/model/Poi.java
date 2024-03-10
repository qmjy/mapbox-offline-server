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

package io.github.qmjy.mapbox.model;

import lombok.Data;
import no.ecc.vectortile.VectorTileDecoder;
import org.locationtech.jts.geom.*;

@Data
public class Poi {
    private String name;
    private String geometry;

    /**
     * See type code of {@link Geometry}
     */
    private int geometryType = -1;

    public Poi(VectorTileDecoder.Feature feature) {
        if (feature.getAttributes().get("name") != null) {
            this.name = (String) feature.getAttributes().get("name");
            this.geometry = feature.getGeometry().toText();
            switch (feature.getGeometry()) {
                case Point point -> {
                    this.geometryType = 0;
                }
                case LineString lineString -> {
                    this.geometryType = 2;
                }
                case MultiLineString multiLineString -> {
                    this.geometryType = 4;
                }
                case Polygon polygon -> {
                    this.geometryType = 5;
                }
                case MultiPolygon multiPolygon -> {
                    this.geometryType = 6;
                }
                case null, default -> System.out.println();
            }
        }
    }
}
