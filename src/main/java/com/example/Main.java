package com.example;

import org.locationtech.jts.geom.Geometry;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        try {
            // 1. 获取资源路径
            File file = new File("C:\\Users\\刘少锋\\Downloads\\map.geojson");

            // 2. 读取并转换GeoJSON
            System.out.println("Reading GeoJSON from: " + file.getAbsolutePath());
            Geometry[] geometries = GeoJsonReader.readAndConvertGeometries(file);
            System.out.println("Loaded " + geometries.length + " geometries");

            // 3. 生成瓦片 (1-14级)
            String outputDir = "tiles";
            System.out.println("Generating tiles to: " + outputDir);
            TileGenerator.generateTiles(geometries, 1, 14, outputDir);

            System.out.println("MVT tiles generated successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}