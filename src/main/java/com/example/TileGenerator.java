package com.example;


import io.github.sebasbaumh.mapbox.vectortile.VectorTile;
import io.github.sebasbaumh.mapbox.vectortile.adapt.jts.JtsAdapter;
import io.github.sebasbaumh.mapbox.vectortile.adapt.jts.UserDataKeyValueMapConverter;
import io.github.sebasbaumh.mapbox.vectortile.build.MvtLayerParams;
import io.github.sebasbaumh.mapbox.vectortile.build.MvtLayerProps;
import io.github.sebasbaumh.mapbox.vectortile.util.MvtUtil;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TileGenerator {
    // 地球周长（Web Mercator投影）
    private static final double EARTH_RADIUS = 6378137;
    private static final double EARTH_CIRCUMFERENCE = 2 * Math.PI * EARTH_RADIUS;
    private static final double ORIGIN_SHIFT = EARTH_CIRCUMFERENCE / 2.0;

    // 瓦片参数配置
    private static final MvtLayerParams LAYER_PARAMS = new MvtLayerParams(4096);
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 3857);


    public static void generateTiles(Geometry[] geometries, int minZoom, int maxZoom, String outputDir) throws Exception {
        for (int zoom = minZoom; zoom <= maxZoom; zoom++) {
            int tileCount = (int) Math.pow(2, zoom);

            for (int x = 0; x < tileCount; x++) {
                for (int y = 0; y < tileCount; y++) {
                    generateSingleTile(geometries, zoom, x, y, outputDir);
                }
            }
        }
    }

    private static void generateSingleTile(Geometry[] geometries, int z, int x, int y, String outputDir) throws Exception {
        // 1. 计算瓦片地理范围
        Envelope tileEnvelope = calculateTileEnvelope(z, x, y);

        // 2. 裁剪几何体到瓦片范围
        Geometry clipBoundingBox = new GeometryFactory().toGeometry(tileEnvelope);
        List<Geometry> tileGeoms = new ArrayList<>();

        for (Geometry geom : geometries) {
            if (geom.intersects(clipBoundingBox)) {
                Geometry clipped = geom.intersection(clipBoundingBox);
                if (!clipped.isEmpty()) {
                    // 确保经纬度坐标顺序正确
                    clipped.apply(new CoordinateSwapper());
                    tileGeoms.add(clipped);
                }
            }
        }

        if (tileGeoms.isEmpty()) {
            return;
        }

        // 3. 创建MVT图层
        MvtLayerProps layerProps = new MvtLayerProps();
        VectorTile.Tile.Layer.Builder layerBuilder = MvtUtil.newLayerBuilder("features", LAYER_PARAMS);

        // 4. 转换几何体到MVT格式
        List<Geometry> collect = tileGeoms.stream().map(item -> JtsAdapter.createTileGeom(
                item,
                tileEnvelope,
                GEOMETRY_FACTORY,
                LAYER_PARAMS, geometry -> true)
        ).toList();


        // 5. 添加要素到图层
        JtsAdapter.addFeatures(layerBuilder, collect, layerProps, new UserDataKeyValueMapConverter());

        // 6. 写入属性
        MvtUtil.writeProps(layerBuilder, layerProps);

        // 7. 构建MVT瓦片
        VectorTile.Tile.Builder tileBuilder = VectorTile.Tile.newBuilder();
        tileBuilder.addLayers(layerBuilder.build());
        VectorTile.Tile tile = tileBuilder.build();

        // 8. 保存到文件
        Path dirPath = Paths.get(outputDir, String.valueOf(z), String.valueOf(x));
        Files.createDirectories(dirPath);

        try (OutputStream os = new FileOutputStream(dirPath.resolve(y + ".mvt").toFile())) {
            tile.writeTo(os);
        }
    }

    private static Envelope calculateTileEnvelope(int z, int x, int y) {
        double tileSize = EARTH_CIRCUMFERENCE / (1 << z);
        double minX = -ORIGIN_SHIFT + x * tileSize;
        double maxX = minX + tileSize;
        double minY = ORIGIN_SHIFT - (y + 1) * tileSize;
        double maxY = minY + tileSize;

        return new Envelope(minX, maxX, minY, maxY);
    }
}