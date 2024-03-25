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

package io.github.qmjy.mapbox.model.osm.pbf;

import com.wdtinc.mapbox_vector_tile.adapt.jts.model.JtsLayer;
import com.wdtinc.mapbox_vector_tile.adapt.jts.model.JtsMvt;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.locationtech.jts.geom.Geometry;

import java.util.*;

/**
 * 可读的PBF数据模型
 */
@Data
public class OsmPbfTileOfReadable {
    private final int z;
    private final int x;
    private final int y;
    private Map<String, String> headers = new HashMap<>();
    private final Statistics statistics = new Statistics();

    private long tileLength;
    private Date time = Calendar.getInstance().getTime();
    private String url;
    private Map<String, Collection<Geometry>> features = new HashMap<>();

    public OsmPbfTileOfReadable(HttpServletRequest req, int z, int x, int y) {
        this.z = z;
        this.x = x;
        this.y = y;
        wrapRequestInfo(req);
    }

    private void wrapRequestInfo(HttpServletRequest req) {
        this.url = req.getRequestURL().toString();
        headers.put("referer", req.getHeader("referer"));
        headers.put("User-Agent", req.getHeader("User-Agent"));
    }

    public void wrapStatistics(JtsMvt mvt) {
        for (Map.Entry<String, JtsLayer> next : mvt.getLayersByName().entrySet()) {
            JtsLayer value = next.getValue();
            //TODO 待完善
            features.put(next.getKey(), null);
        }
        statistics.setLayersCount(mvt.getLayers().size());
        statistics.updateLayerCount(mvt.getLayersByName());
    }
}
