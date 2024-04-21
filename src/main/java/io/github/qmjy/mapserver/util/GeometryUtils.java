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

import org.geotools.geojson.geom.GeometryJSON;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Optional;

/**
 * Geometry数据转换工具
 *
 * @author Shaofeng Liu
 * @since 1.0
 */
public class GeometryUtils {

    /**
     * WKT转geometry数据类型
     *
     * @param wellKnownText WKT文本
     * @return geometry数据类型
     */
    public static Optional<Geometry> toGeometryFromWkt(String wellKnownText) {
        WKTReader reader = new WKTReader(new GeometryFactory());
        try {
            return Optional.of(reader.read(wellKnownText));
        } catch (ParseException e) {
            return Optional.empty();
        }
    }

    /**
     * geojson转geometry数据类型
     *
     * @param geojson geojson文本
     * @return geometry数据类型
     */
    public static Optional<Geometry> toGeometryFromGeojson(String geojson) {
        GeometryJSON geometryJSON = new GeometryJSON(7);
        try {
            return Optional.of(geometryJSON.read(new StringReader(geojson)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * geometry转geojson数据类型
     *
     * @param geometry Geometry实例对象
     * @return geojson
     */
    public static Optional<String> geometry2Geojson(Geometry geometry) {
        GeometryJSON gjson = new GeometryJSON(7);
        StringWriter writer = new StringWriter();
        try {
            gjson.write(geometry, writer);
            return Optional.of(writer.toString());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * geometry转WKT数据类型
     *
     * @param geometry Geometry实例对象
     * @return wkt
     */
    public static Optional<String> geometry2Wkt(Geometry geometry) {
        WKTWriter writer = new WKTWriter();
        return Optional.of(writer.write(geometry));
    }

    /**
     * 将瓦片坐标转换为经纬度坐标(256像素切片)
     * 1. 计算瓦片的像素尺寸：在Web Mercator投影中，每个瓦片的像素尺寸通常是256x256。因此，一个瓦片在缩放级别zoom上的像素宽度是256。<p>
     * 2. 计算一个像素代表的地理距离：地球的周长大约是40,075公里。在缩放级别zoom上，整个地球被分为2^zoom个瓦片，每个瓦片256像素宽。因此，一个像素在缩放级别zoom上代表的地理距离是：<p>
     * <blockquote><pre>
     *    pixel_width = Earth's circumference / (256 * 2^zoom)
     * </pre></blockquote>
     * 3. 计算瓦片地理区域的宽度和高度：使用上面计算的像素宽度，你可以计算出瓦片地理区域的宽度和高度。因为瓦片是正方形的，所以宽度和高度是相同的。<p>
     * <blockquote><pre>
     *    tile_geo_width = pixel_width * 256
     *    tile_geo_height = tile_geo_width
     * </pre></blockquote>
     * 4. 计算瓦片地理区域的右下角经纬度：使用瓦片左上角的经纬度和瓦片的地理尺寸，你可以计算出瓦片地理区域的右下角经纬度。<p>
     *
     * @param xTile  瓦片X坐标
     * @param yTile  瓦片Y坐标
     * @param zoom   缩放层级
     * @param xPixel 瓦片内像素X坐标
     * @param yPixel 瓦片内像素Y坐标
     * @param extent 瓦片像素宽度
     * @return 经纬度坐标
     */
    public static double[] pixel2deg(int xTile, int yTile, int zoom, int xPixel, int yPixel, int extent) {
        double n = Math.pow(2, zoom);

        xTile = xTile + (xPixel / extent);
        yTile = yTile + ((extent - yPixel) / extent);

        double lonDeg = (xTile / n) * 360 - 180;
        double latRad = 0 - Math.atan(Math.sinh(Math.PI * (1 - 2 * yTile / n)));
        double latDeg = Math.toDegrees(latRad);
        return new double[]{lonDeg, latDeg};
    }


    /**
     * 计算一个经纬度坐标在墨卡托投影下的瓦片的坐标位置
     *
     * @param lat  待查询的纬度
     * @param lon  待查询的经度
     * @param zoom 待查询的瓦片在地图中的缩放层级
     * @return 瓦片的横轴坐标位置
     */
    public static int[] mercatorLatLonToTile(double lat, double lon, int zoom) {
        double latRad = Math.toRadians(lat);
        int n = (int) Math.pow(2, zoom);
        int xTile = (int) Math.floor((lon + 180) / 360 * n);
        int yTile = (int) Math.floor((1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2 * n);
        return new int[]{xTile, yTile};
    }

    /**
     * 计算墨卡托投影下指定瓦片的左上角经纬度坐标
     *
     * @param xTile 瓦片的X轴位置
     * @param yTile 瓦片的Y轴位置
     * @param zoom  瓦片所在的缩放层级
     * @return 瓦片在墨卡托投影下的左上角坐标
     */
    public static double[] mercatorTileToLatLon(int xTile, int yTile, int zoom) {
        int n = (int) Math.pow(2, zoom);
        double lon = (xTile / (double) n) * 360.0 - 180.0;
        double latRad = Math.atan(Math.sinh(Math.PI * (1 - 2 * yTile / (double) n)));
        double lat = Math.toDegrees(latRad);
        return new double[]{lat, lon};
    }

    /**
     * 计算一个瓦片所在经纬度坐标边界
     *
     * @param xTile 瓦片的X坐标
     * @param yTile 瓦片的Y坐标
     * @param zoom  瓦片所在的缩放层级
     * @return 瓦片的四个顶点经纬度坐标
     */
    public static double[] calculateTileBoundingBox(int xTile, int yTile, int zoom) {
        double[] tileOrigin = mercatorTileToLatLon(xTile, yTile, zoom);
        double pixelWidth = 40075016.686 / (256 * Math.pow(2, zoom));

        //瓦片是正方形
        double tileGeoWidth = pixelWidth * 256;

        double tileMinLon = tileOrigin[1];
        double tileMaxLon = tileOrigin[1] + tileGeoWidth;
        double tileMaxLat = tileOrigin[0];
        double tileMinLat = tileOrigin[0] - tileGeoWidth;

        return new double[]{tileMinLon, tileMinLat, tileMaxLon, tileMaxLat};
    }
}
