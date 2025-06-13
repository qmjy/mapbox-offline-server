
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


import io.github.sebasbaumh.mapbox.vectortile.adapt.jts.model.JtsLayer;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class Statistics {

    private int layersCount;

    private int featuresCount;

    private Map<String, Integer> layers = new HashMap<>();

    public void updateLayerCount(Map<String, JtsLayer> layersMap) {
        for (Map.Entry<String, JtsLayer> next : layersMap.entrySet()) {
            int size = next.getValue().getGeometries().size();
            featuresCount += size;
            layers.put(next.getKey(), size);
        }
    }
}