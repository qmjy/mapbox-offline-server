package com.example;

import io.github.sebasbaumh.mapbox.vectortile.VectorTile;
import io.github.sebasbaumh.mapbox.vectortile.adapt.jts.JtsAdapter;
import io.github.sebasbaumh.mapbox.vectortile.adapt.jts.UserDataKeyValueMapConverter;
import io.github.sebasbaumh.mapbox.vectortile.build.MvtLayerParams;
import io.github.sebasbaumh.mapbox.vectortile.build.MvtLayerProps;
import io.github.sebasbaumh.mapbox.vectortile.util.MvtUtil;
import org.geotools.api.feature.Property;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.data.geojson.GeoJSONReader;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


/**
 * GeoJSON到MVT切片转换工具
 * 使用Java 21, mapbox-vector-tile-java 25.1.0 和 GeoTools 33.0
 */
public class GeoJsonToMvtTiler {

    private static final int CRS_VALUE_WGS84 = 4326;
    private static final String CRS_WGS84 = "EPSG:4326";
    private static final String CRS_MERCATOR_WEB = "EPSG:3857";

    // 地球半径（米），用于墨卡托投影计算
    private static final double EARTH_RADIUS = 6378137.0;

    //地球周长（米），用于墨卡托投影计算
    private static final double EARTH_CIRCUMFERENCE = 2 * Math.PI * EARTH_RADIUS;

    // 原点坐标（墨卡托投影的左上角）
    private static final double EARTH_HALF_CIRCUMFERENCE_MERCATOR = EARTH_CIRCUMFERENCE / 2.0;

    private static final int TILE_COORDINATE_DIRECTION_ORIGIN_LEFT_TOP = 0;
    private static final int TILE_COORDINATE_DIRECTION_ORIGIN_LEFT_BOTTOM = 1;

    /**
     * 当前要切片的地理数据边界范围
     */
    private static Envelope bounds;


    /**
     * 仅接受WGS84坐标系的数据接收
     *
     * @param geojsonPath json文件位置
     * @param mapKey      图层名称的字段
     * @return 解析后的Geometry对象列表
     */
    private static Map<String, List<Geometry>> readGeoJson(String geojsonPath, String mapKey) {
        Map<String, List<Geometry>> geometryMap = new HashMap<>();
        try {
            GeoJSONReader reader = new GeoJSONReader(new FileInputStream(geojsonPath));
            SimpleFeatureIterator features = reader.getFeatures().features();
            while (features.hasNext()) {
                SimpleFeature feature = features.next();

                Optional<Geometry> geometry = getGeometry(feature);
                if (geometry.isPresent()) {
                    Geometry g = geometry.get();
                    g.setUserData(getGeometryUserData(feature));
                    g.setSRID(CRS_VALUE_WGS84);

                    if (mapKey != null && feature.getAttribute(mapKey) != null) {
                        geometryMap.computeIfAbsent(feature.getAttribute(mapKey).toString(), k -> new ArrayList<>()).add(g);
                    } else {
                        geometryMap.computeIfAbsent("default", k -> new ArrayList<>()).add(g);
                    }

                    updateBounds(g);
                }
            }
            features.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return geometryMap;
    }


    private static Map<String, String> getGeometryUserData(SimpleFeature feature) {
        HashMap<String, String> objectObjectHashMap = new HashMap<>();
        for (Property next : feature.getProperties()) {
            if (next.getName() != null && next.getValue() != null) {
                objectObjectHashMap.put(next.getName().toString(), next.getValue().toString());
            }
        }
        return objectObjectHashMap;
    }

    private static Optional<Geometry> getGeometry(SimpleFeature feature) {
        Object geometry = feature.getAttribute("geometry");
        Geometry g = null;
        switch (geometry) {
            case Point point -> g = point;
            case MultiPoint multiPoint -> g = multiPoint;
            case LineString lineString -> g = lineString;
            case MultiLineString multiLineString -> g = multiLineString;
            case Polygon polygon -> g = polygon;
            case MultiPolygon multiPolygon -> g = multiPolygon;
            default -> System.err.println("Unsupported geometry type: " + geometry);
        }
        return g == null ? Optional.empty() : Optional.of(g);
    }

    private static void updateBounds(Geometry g) {
        if (g != null) {
            Envelope envelopeInternal = g.getEnvelopeInternal();
            if (bounds == null) {
                bounds = envelopeInternal;
            } else {
                if (!bounds.contains(envelopeInternal)) {
                    bounds.expandToInclude(envelopeInternal);
                }
            }
        }
    }


    /**
     * 为指定缩放级别生成所有瓦片
     *
     * @param geometryMap 几何对象列表
     * @param outputDir   输出目录
     * @param zoom        缩放级别
     * @throws IOException 如果写入文件失败
     */
    private static void generateTilesForZoom(Map<String, List<Geometry>> geometryMap, String outputDir, int zoom) throws Exception {
        // 创建输出目录
        Path zoomPath = Paths.get(outputDir, String.valueOf(zoom));
        Files.createDirectories(zoomPath);

        // 计算该缩放级别下的瓦片数量
//        int tileCount = (int) Math.pow(2, zoom);
        if (!geometryMap.isEmpty()) {
            if (geometryMap.values().iterator().next().getFirst().getSRID() == CRS_VALUE_WGS84) {
                Map<String, Integer> tileInfoMap = calculateTileRange(bounds, TILE_COORDINATE_DIRECTION_ORIGIN_LEFT_TOP, zoom);
                for (int x = tileInfoMap.get("minTileX"); x <= tileInfoMap.get("maxTileX"); x++) {
                    for (int y = tileInfoMap.get("minTileY"); y <= tileInfoMap.get("maxTileY"); y++) {
                        createTile(geometryMap, zoom, x, y, zoomPath);
                    }
                }
            }
        }
    }

    private static void createTile(Map<String, List<Geometry>> geometries, int zoom, int x, int y, Path zoomPath) {
        // 获取当前瓦片的地理范围
        Envelope tileEnvelope = calculateTileEnvelope(x, y, zoom);

        // 筛选出在当前瓦片范围内的几何对象
        List<Geometry> tileGeometries = new ArrayList<>();

        geometries.forEach((key, value) -> {
            Iterator<Geometry> iterator = value.iterator();
            while (iterator.hasNext()) {
                Geometry geometry = iterator.next();

                System.out.println("Generate tile:" + zoom + "/" + x + "/" + y);
                Envelope mercatorBounds = getMercatorBounds(geometry);
                if (tileEnvelope.intersects(mercatorBounds)) {
                    // 裁剪几何对象到瓦片范围
                    Optional<Geometry> geometryOpt = transformProjections(geometry, CRS_WGS84, CRS_MERCATOR_WEB);
                    if (geometryOpt.isPresent()) {
                        Geometry clippedGeometry = clipGeometryToTile(geometryOpt.get(), tileEnvelope);
                        if (clippedGeometry != null && !clippedGeometry.isEmpty()) {
                            tileGeometries.add(clippedGeometry);
                        }
                    }
                }

                //TODO 如果是完全包含关系，切片完成以后则可以在此层级移除此要素
                if (tileEnvelope.contains(mercatorBounds)) {
                    iterator.remove();
                }
            }

            if (!tileGeometries.isEmpty()) {
                // 创建坐标目录：x
                Path xPath = zoomPath.resolve(String.valueOf(x));
                try {
                    Files.createDirectories(xPath);

                    // 创建瓦片文件：y
                    byte[] mvtData = createMvt(tileGeometries, tileEnvelope, key);
                    Path tilePath = xPath.resolve(y + ".mvt");

                    try (FileOutputStream fos = new FileOutputStream(tilePath.toFile())) {
                        fos.write(mvtData);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }


    /**
     * 根据瓦片的 x, y, zoom 级别计算其边界范围 (Envelope)。
     * 遵循谷歌地图的墨卡托投影规则。
     *
     * @param tileX 瓦片X坐标
     * @param tileY 瓦片Y坐标
     * @param zoom  缩放级别
     * @return 瓦片的经纬度边界范围 Envelope
     */
    public static Envelope calculateTileEnvelope(int tileX, int tileY, int zoom) {
        // 1. 计算地图在当前zoom级别下的总宽度（以墨卡托米为单位）
        // 整个世界的墨卡托宽度是 2 * EARTH_HALF_CIRCUMFERENCE_MERCATOR
        double mapWidthInMeters = 2 * EARTH_HALF_CIRCUMFERENCE_MERCATOR;

        // 2. 计算每个瓦片在当前zoom级别下的宽度（以墨卡托米为单位）
        // 2^zoom 是当前zoom级别下，X或Y方向上的瓦片数量
        double numTiles = Math.pow(2, zoom);
        double tileSizeInMeters = mapWidthInMeters / numTiles;

        // 3. 计算瓦片的左上角墨卡托投影坐标 (minX, maxY)
        // 墨卡托投影坐标系的中心是 (0,0)，左下角是 (-EARTH_HALF_CIRCUMFERENCE_MERCATOR, -EARTH_HALF_CIRCUMFERENCE_MERCATOR)
        // 右上角是 (EARTH_HALF_CIRCUMFERENCE_MERCATOR, EARTH_HALF_CIRCUMFERENCE_MERCATOR)
        double minMercatorX = -EARTH_HALF_CIRCUMFERENCE_MERCATOR + tileX * tileSizeInMeters;
        double maxMercatorY = EARTH_HALF_CIRCUMFERENCE_MERCATOR - tileY * tileSizeInMeters;

        // 4. 计算瓦片的右下角墨卡托投影坐标 (maxX, minY)
        double maxMercatorX = minMercatorX + tileSizeInMeters;
        double minMercatorY = maxMercatorY - tileSizeInMeters;

        // 5. 将墨卡托投影坐标转换为经纬度坐标
//        double minLon = mercatorXToLon(minMercatorX);
//        double minLat = mercatorYToLat(minMercatorY);
//        double maxLon = mercatorXToLon(maxMercatorX);
//        double maxLat = mercatorYToLat(maxMercatorY);

        return new Envelope(minMercatorX, maxMercatorX, minMercatorY, maxMercatorY);
    }

    /**
     * 将墨卡托X坐标转换为经度。
     *
     * @param mercatorX 墨卡托X坐标
     * @return 经度 (度)
     */
    private static double mercatorXToLon(double mercatorX) {
        return (mercatorX / EARTH_HALF_CIRCUMFERENCE_MERCATOR) * 180;
    }

    /**
     * 将墨卡托Y坐标转换为纬度。
     *
     * @param mercatorY 墨卡托Y坐标
     * @return 纬度 (度)
     */
    private static double mercatorYToLat(double mercatorY) {
        double latRad = Math.atan(Math.sinh(mercatorY / EARTH_HALF_CIRCUMFERENCE_MERCATOR * Math.PI));
        return Math.toDegrees(latRad);
    }

    /**
     * 将WGS84经纬度坐标转换为Web墨卡托坐标
     *
     * @param lng 经度
     * @param lat 纬度
     * @return 墨卡托坐标点
     */
    private static Point lngLatToWebMercator(double lng, double lat) {
        double x = lng * EARTH_HALF_CIRCUMFERENCE_MERCATOR / 180;
        double y = Math.log(Math.tan((90 + lat) * Math.PI / 360)) / (Math.PI / 180);
        y = y * EARTH_HALF_CIRCUMFERENCE_MERCATOR / 180;
        return new GeometryFactory().createPoint(new Coordinate(x, y));
    }

    /**
     * 将WGS84坐标系转换为Web墨卡托坐标系
     *
     * @param geometry      几何对象
     * @param sourceCRSCode 原坐标系
     * @param targetCRSCode 目标坐标系
     * @return Web墨卡托坐标系下的几何对象
     */
    private static Optional<Geometry> transformProjections(Geometry geometry, String sourceCRSCode, String targetCRSCode) {
        try {
            // 1. 定义源和目标坐标系
            // 注意坐标顺序：经度在前（X），纬度在后（Y），强制指定为经度、纬度顺序，避免潜在问题。 true 表示强制 (longitude, latitude) 顺序
            CoordinateReferenceSystem sourceCRS = CRS.decode(sourceCRSCode, true);
            CoordinateReferenceSystem targetCRS = CRS.decode(targetCRSCode);

            // 2. 创建坐标转换器
            MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS);

            // 3. 执行转换
            return Optional.of(JTS.transform(geometry, transform));
        } catch (FactoryException e) {
            e.printStackTrace();
        } catch (TransformException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    private static Envelope getMercatorBounds(Geometry geometry) {
        Envelope envelope = geometry.getEnvelopeInternal();

        Point topLeft = lngLatToWebMercator(envelope.getMinX(), envelope.getMinY());
        Point rightBottom = lngLatToWebMercator(envelope.getMaxX(), envelope.getMaxY());

        return new Envelope(topLeft.getX(), rightBottom.getX(), topLeft.getY(), rightBottom.getY());
    }

    public static Map<String, Integer> calculateTileRange(Envelope envelope, Integer origin, int zoom) {
        if (zoom < 0 || zoom > 23) {
            throw new IllegalArgumentException("缩放层级必须在0-23之间");
        }

        // 计算瓦片坐标范围
        int minTileX = longitudeToTileX(envelope.getMinX(), zoom);
        int maxTileX = longitudeToTileX(envelope.getMaxX(), zoom);
        int minTileY, maxTileY;

        if (TILE_COORDINATE_DIRECTION_ORIGIN_LEFT_TOP == origin) {
            // 左上角为原点
            minTileY = latitudeToTileYTopLeft(envelope.getMaxY(), zoom);
            maxTileY = latitudeToTileYTopLeft(envelope.getMinY(), zoom);
        } else if (TILE_COORDINATE_DIRECTION_ORIGIN_LEFT_BOTTOM == origin) {
            // 左下角为原点
            minTileY = latitudeToTileYBottomLeft(envelope.getMinY(), zoom);
            maxTileY = latitudeToTileYBottomLeft(envelope.getMaxY(), zoom);
        } else {
            throw new IllegalArgumentException("坐标原点必须是'top-left'或'bottom-left'");
        }

        // 确保坐标范围正确（min ≤ max）
        if (minTileX > maxTileX) {
            int temp = minTileX;
            minTileX = maxTileX;
            maxTileX = temp;
        }

        if (minTileY > maxTileY) {
            int temp = minTileY;
            minTileY = maxTileY;
            maxTileY = temp;
        }

        // 计算瓦片总数
        int tileCountX = maxTileX - minTileX + 1;
        int tileCountY = maxTileY - minTileY + 1;
        int totalTiles = tileCountX * tileCountY;

        // 返回结果
        Map<String, Integer> result = new HashMap<>();
        result.put("minTileX", minTileX);
        result.put("maxTileX", maxTileX);
        result.put("minTileY", minTileY);
        result.put("maxTileY", maxTileY);
        result.put("tileCountX", tileCountX);
        result.put("tileCountY", tileCountY);
        result.put("totalTiles", totalTiles);
        result.put("zoomLevel", zoom);
        result.put("origin", origin);

        return result;
    }

    /**
     * 将纬度转换为瓦片Y坐标（左上角为原点）
     *
     * @param latitude 纬度（-85到85）
     * @param zoom     缩放级别
     * @return 瓦片Y坐标
     */
    private static int latitudeToTileYTopLeft(double latitude, int zoom) {
        // 将纬度转换为弧度
        double latRad = Math.toRadians(latitude);
        // 使用墨卡托投影公式
        double normalizedY = (1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2;
        // 计算瓦片坐标
        int tileY = (int) Math.floor(normalizedY * (1 << zoom));
        // 确保在有效范围内
        return Math.max(0, Math.min(tileY, (1 << zoom) - 1));
    }

    /**
     * 将纬度转换为瓦片Y坐标（左下角为原点）
     *
     * @param latitude 纬度（-85到85）
     * @param zoom     缩放级别
     * @return 瓦片Y坐标
     */
    private static int latitudeToTileYBottomLeft(double latitude, int zoom) {
        // 对于左下角原点，Y坐标是左上角原点的镜像
        int maxTile = (1 << zoom) - 1;
        int tileYTopLeft = latitudeToTileYTopLeft(latitude, zoom);
        return maxTile - tileYTopLeft;
    }

    /**
     * 将经度转换为瓦片X坐标
     *
     * @param longitude 经度（-180到180）
     * @param zoom      缩放级别
     * @return 瓦片X坐标
     */
    private static int longitudeToTileX(double longitude, int zoom) {
        // 将经度归一化到0-1范围
        double normalizedX = (longitude + 180.0) / 360.0;
        // 计算瓦片坐标
        int tileX = (int) Math.floor(normalizedX * (1 << zoom));
        // 确保在有效范围内
        return Math.max(0, Math.min(tileX, (1 << zoom) - 1));
    }


    /**
     * 使用瓦片范围裁剪几何对象
     *
     * @param geometry     要裁剪的几何对象
     * @param tileEnvelope 瓦片范围
     * @return 裁剪后的几何对象，裁剪失败则返回空
     */
    private static Geometry clipGeometryToTile(Geometry geometry, Envelope tileEnvelope) {
        GeometryFactory geometryFactory = geometry.getFactory();
        Geometry tileGeometry = geometryFactory.toGeometry(tileEnvelope);

        try {
            Geometry intersection = geometry.intersection(tileGeometry);

            //复制对象属性
            intersection.setUserData(geometry.getUserData());
            intersection.setSRID(geometry.getSRID());

            return intersection;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 创建MVT数据:cite[4]:cite[7]
     *
     * @param geometries   几何对象列表
     * @param tileEnvelope 瓦片范围
     * @param layerName    图层名称
     * @return MVT字节数组
     */
    private static byte[] createMvt(List<Geometry> geometries, Envelope tileEnvelope, String layerName) {
        GeometryFactory geomFactory = new GeometryFactory();
        MvtLayerProps mvtLayerProps = new MvtLayerProps();
        MvtLayerParams mvtLayerParams = MvtLayerParams.DEFAULT;
        VectorTile.Tile.Layer.Builder mvtLayerBuilder = MvtUtil.newLayerBuilder(layerName, mvtLayerParams);

        List<Geometry> geometryList = new ArrayList<>();
        for (Geometry geometry : geometries) {
            Geometry tileGeom = JtsAdapter.createTileGeom(geometry, tileEnvelope, geomFactory, mvtLayerParams, null);
            if (!tileGeom.isEmpty()) {
                geometryList.add(tileGeom);
            }
        }

        // add it to the layer builder
        if (!geometryList.isEmpty()) {
            JtsAdapter.addFeatures(mvtLayerBuilder, geometryList, mvtLayerProps, new UserDataKeyValueMapConverter());

            // finish writing of features
            MvtUtil.writeProps(mvtLayerBuilder, mvtLayerProps);
            VectorTile.Tile.Builder mvtBuilder = VectorTile.Tile.newBuilder();
            mvtBuilder.addLayers(mvtLayerBuilder.build());

            // build the vector tile (here as byte array)
            return mvtBuilder.build().toByteArray();
        }

        return new byte[0];
    }

    /**
     * 主函数
     *
     * @param args 命令行参数: GeoJSON文件路径 输出目录 缩放级别(可选，多个用逗号分隔)
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java GeoJsonToMvtTiler <geojson-file> <output-dir> [zoom-levels]");
            System.out.println("Example: java GeoJsonToMvtTiler input.geojson ./tiles 10,12,14");
            System.exit(1);
        }

        String geojsonPath = args[0];
        String outputDir = args[1];
        Set<Integer> zoomLevels = new HashSet<>();

        if (args.length > 2) {
            String[] string = args[2].split(",");
            for (String zoomStr : string) {
                try {
                    zoomLevels.add(Integer.parseInt(zoomStr.trim()));
                } catch (NumberFormatException e) {
                    System.err.println("Invalid zoom level: " + zoomStr);
                }
            }
        } else {
            zoomLevels.add(10);
            zoomLevels.add(12);
            zoomLevels.add(14);
        }

        try {
            System.out.println("Reading GeoJSON file: " + geojsonPath);
            Map<String, List<Geometry>> geometryMap = readGeoJson(geojsonPath, "type");
            System.out.println("Read layers of " + geometryMap.size());

            for (int zoom : zoomLevels) {
                System.out.println("Generating tiles for zoom level: " + zoom);
                generateTilesForZoom(geometryMap, outputDir, zoom);
            }

            System.out.println("Tile generation completed successfully!");

        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
