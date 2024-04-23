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

package io.github.qmjy.mapserver.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.data.geojson.GeoJSONReader;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.simple.SimpleFeatureImpl;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.filter.identity.FeatureIdImpl;
import org.geotools.geojson.feature.FeatureJSON;
import org.junit.Test;
import org.locationtech.jts.geom.Geometry;
import org.springframework.boot.json.BasicJsonParser;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 江西省：-913109
 */
public class ConvertUtilTest {

    //系统根行政区划数据
    private static final String ROOT_OSMB_FILE = "W:\\foxgis-server-lite-win\\data\\OSMB\\OSMB-China.geojson";
    //三方系统获取的行政区划边界
    private static final String ORIGIN_FILE = "C:\\Users\\liush\\Desktop\\xingguo.json";
    //最终目标输出
    private static final String TARGET_OSMB_FILE = "C:\\Users\\liush\\Desktop\\OSMB-China-JiangXi.geojson";


    @Test
    public void convert() throws IOException {
        List<SimpleFeature> originFeatures = getChildrenAndParents(-913109);

        String json = Files.readString(Path.of(ORIGIN_FILE));
        List<Object> objects = new BasicJsonParser().parseList(json);
        List<SimpleFeature> features = getSimpleFeatures(objects);

        features.addAll(originFeatures);

        FeatureJSON featureJson = new FeatureJSON();
        StringWriter stringWriter = new StringWriter();

        featureJson.writeFeatureCollection(getFeatures(features), stringWriter);

//        for (SimpleFeature feature : features) {
//            featureJson.writeFeature(feature, stringWriter);
//        }
        Files.writeString(Path.of(TARGET_OSMB_FILE), stringWriter.toString());
    }

    private FeatureCollection<SimpleFeatureType, SimpleFeature> getFeatures(List<SimpleFeature> features) {
        DefaultFeatureCollection featureCollection = new DefaultFeatureCollection("internal", null);
        featureCollection.addAll(features);
        return featureCollection;
    }


    private List<SimpleFeature> getSimpleFeatures(List<Object> objects) {
        int i = 10000;

        SimpleFeatureTypeBuilder ftb = new SimpleFeatureTypeBuilder();
        ftb.setName("feature");
        ftb.add("admin_level", Integer.class);
        ftb.add("osm_id", Integer.class);
        ftb.add("parents", String.class);
        ftb.add("geometry", Geometry.class);
        ftb.add("local_name", String.class);
        ftb.add("name_en", String.class);
        ftb.add("name", String.class);
        ftb.add("boundary", String.class);
        SimpleFeatureType ft = ftb.buildFeatureType();

        List<SimpleFeature> list = new ArrayList<>();
        for (Object object : objects) {
            if (object instanceof LinkedHashMap<?, ?> map) {
                Optional<Geometry> geometryOptional = getGeometry((Map) map.get("areainfo"));
                if (geometryOptional.isPresent()) {
                    //TODO: 兴国县admin_level=7 、osm_id=-3180943、parents=-3180943,-3180745,-913109,-270056、geometryOptional=？、local_name=、name_en=、name=
                    list.add(new SimpleFeatureImpl(new Object[]{7, i++, "-3180943,-3180745,-913109,-270056", geometryOptional.get(), map.get("areaname"), "", map.get("areaname"), "boundary"}, ft, new FeatureIdImpl(String.valueOf(i++)), false));
                }
            }
        }
        return list;
    }

    private Optional<Geometry> getGeometry(Map<String, Object> areaInfo) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            String geoJson = mapper.writeValueAsString(areaInfo);
            return GeometryUtils.toGeometryFromGeojson(geoJson);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }


    private List<SimpleFeature> getChildrenAndParents(Integer root) throws IOException {
        List<SimpleFeature> dataList = new ArrayList<>();

        SimpleFeatureIterator features;
        try (GeoJSONReader reader = new GeoJSONReader(new FileInputStream(ROOT_OSMB_FILE))) {
            features = reader.getFeatures().features();
        }
        while (features.hasNext()) {
            SimpleFeature feature = features.next();
            if (feature.getAttribute("parents") != null && feature.getAttribute("parents").toString().contains(String.valueOf(root))) {
                dataList.add(feature);
                continue;
            }

            if (feature.getAttribute("osm_id").toString().equals(String.valueOf(root))) {
                dataList.add(feature);
                dataList.addAll(getParents(feature.getAttribute("parents").toString()));
            }
        }
        return dataList;
    }

    private List<SimpleFeature> getParents(String parents) {
        String[] parentIds = parents.split(",");
        List<String> list = Arrays.asList(parentIds);

        List<SimpleFeature> objects = new ArrayList<>();

        try {
            GeoJSONReader reader = new GeoJSONReader(new FileInputStream(ROOT_OSMB_FILE));
            SimpleFeatureIterator features = reader.getFeatures().features();
            while (features.hasNext()) {
                SimpleFeature feature = features.next();
                if (list.contains(feature.getAttribute("osm_id").toString())) {
                    objects.add(feature);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return objects;
    }
}
