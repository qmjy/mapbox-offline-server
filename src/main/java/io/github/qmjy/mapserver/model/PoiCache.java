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

package io.github.qmjy.mapserver.model;

import lombok.Data;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

@Data
public class PoiCache {
    private int tileRow;
    private int tileColumn;
    private int zoomLevel;
    private String name;
    private String geometry;

    /**
     * See type code of {@link Geometry}
     */
    private int geometryType = -1;


    public PoiCache(String name, int tileRow, int tileColumn, int zoomLevel, int geometryType, Point point) {
        this(name, tileRow, tileColumn, zoomLevel, geometryType);
        this.geometry = point.toText();
    }

    public PoiCache(String name, int tileRow, int tileColumn, int zoomLevel, int geometryType) {
        this.name = name;
        this.tileRow = tileRow;
        this.tileColumn = tileColumn;
        this.zoomLevel = zoomLevel;
        this.geometryType = geometryType;
    }
}
