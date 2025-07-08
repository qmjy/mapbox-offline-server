package com.example;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.data.geojson.GeoJSONReader;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import java.io.File;
import java.io.FileInputStream;

public class GeoJsonReader {
    private static final String SOURCE_CRS = "EPSG:4326"; // WGS84
    private static final String TARGET_CRS = "EPSG:3857"; // Web Mercator
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    public static Geometry[] readAndConvertGeometries(File geoJsonFile) throws Exception {
        if (geoJsonFile == null) {
            throw new IllegalArgumentException("GeoJSON resource not found");
        }

        if (!geoJsonFile.exists()) {
            throw new IllegalArgumentException("GeoJSON file does not exist: " + geoJsonFile.getAbsolutePath());
        }

        SimpleFeatureCollection collection = new GeoJSONReader(new FileInputStream(geoJsonFile)).getFeatures();

        // 坐标转换准备
        CoordinateReferenceSystem sourceCrs = CRS.decode(SOURCE_CRS);
        CoordinateReferenceSystem targetCrs = CRS.decode(TARGET_CRS);
        MathTransform transform = CRS.findMathTransform(sourceCrs, targetCrs, true);

        Geometry[] geometries = new Geometry[collection.size()];
        try (SimpleFeatureIterator iterator = collection.features()) {
            int index = 0;
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();
                Geometry geom = (Geometry) feature.getDefaultGeometry();

                // 1. 首先交换坐标 (经度<->纬度)
                CoordinateSwapper.swapCoordinates(geom);

                // 2. 然后进行坐标转换
                geometries[index++] = JTS.transform(geom, transform);
            }
        }
        return geometries;
    }
}