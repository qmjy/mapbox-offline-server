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

package io.github.qmjy.mapbox.controller;

import io.github.qmjy.mapbox.MapServerDataCenter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.geotools.api.data.FeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.style.Style;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.referencing.CRS;
import org.geotools.renderer.GTRenderer;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.SLD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * WMS服务
 */
@RestController
@RequestMapping("/api/wms")
@Tag(name = "地图WMS服务管理", description = "地图wms服务接口能力")
public class MapServerWMSRestController {
    private static final Logger logger = LoggerFactory.getLogger(MapServerWMSRestController.class);


    /**
     * 返回WMS服务的Capabilities文档，通常是一个XML文件，描述了服务的能力和可用的图层
     *
     * @return 服务的能力和可用的图层
     */
    @GetMapping(value = "/capabilities", produces = MediaType.APPLICATION_XML_VALUE)
    public String getCapabilities() {
        return "";
    }

    /**
     * 根据请求参数生成和返回地图图像。这个端点需要一系列的参数，包括要渲染的图层、样式、地图范围、尺寸、投影和图像格式。
     *
     * @param layers 要渲染的图层
     * @param styles 样式
     * @param bbox   地图范围
     * @param width  宽度
     * @param height 高度
     * @param srs    坐标系
     * @return 地图数据
     */
    @GetMapping(value = "/map/{shapefile}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<ByteArrayResource> getMap(
            @PathVariable("shapefile") String shapefile,
            @RequestParam(value = "layers", defaultValue = "") String layers,
            @RequestParam(value = "styles", defaultValue = "") String styles,
            @RequestParam(value = "bbox", defaultValue = "") String bbox,
            @RequestParam(value = "width", defaultValue = "500") int width,
            @RequestParam(value = "height", defaultValue = "500") int height,
            @RequestParam(value = "srs", defaultValue = "EPSG:4326") String srs) {

        String[] split = bbox.split(",");
        if (split.length != 4) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        ShapefileDataStore shapefileDataStore = (ShapefileDataStore) MapServerDataCenter.getShpDataStores(shapefile);

        try {
//            String typeName = shapefileDataStore.getTypeNames()[0];
            FeatureSource<SimpleFeatureType, SimpleFeature> featureSource = shapefileDataStore.getFeatureSource(layers);

            // 创建样式,这里可以根据styleName来应用不同的样式
            Style style = SLD.createSimpleStyle(featureSource.getSchema());

            // 创建地图
            MapContent mapContent = new MapContent();
            Layer layer = new FeatureLayer(featureSource, style);
            mapContent.addLayer(layer);

            //        // 渲染地图
            GTRenderer renderer = new StreamingRenderer();
            renderer.setMapContent(mapContent);

            // 设置渲染选项
            RenderingHints hints = new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            hints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            hints.put(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
            renderer.setJava2DHints(hints);


            // 创建地图图像
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setRenderingHints(hints);
            // 渲染地图, 获取WGS 84坐标系的CoordinateReferenceSystem实例
            renderer.paint(g2d, new Rectangle(width, height), getReferencedEnvelope(split, srs));

            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                // 将BufferedImage写入ByteArrayOutputStream，并指定图片格式（如PNG）
                ImageIO.write(image, "PNG", baos);
                byte[] bytes = baos.toByteArray();

                // 清理资源
                g2d.dispose();
                mapContent.dispose();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.IMAGE_PNG);
                ByteArrayResource resource = new ByteArrayResource(bytes);
                return ResponseEntity.ok().headers(headers).contentLength(bytes.length).body(resource);
            } catch (IOException e) {
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ReferencedEnvelope getReferencedEnvelope(String[] coordinates, String epsg) {
        try {
            // 假设边界框字符串格式为 "minLon,minLat,maxLon,maxLat"
            double minLon = Double.parseDouble(coordinates[0]);
            double minLat = Double.parseDouble(coordinates[1]);
            double maxLon = Double.parseDouble(coordinates[2]);
            double maxLat = Double.parseDouble(coordinates[3]);

            CoordinateReferenceSystem crs = CRS.decode(epsg);
            // 创建ReferencedEnvelope对象
            return new ReferencedEnvelope(minLon, maxLon, minLat, maxLat, crs);
        } catch (Exception e) {
            logger.error("Build referenced envelope failed: " + Arrays.toString(coordinates));
            return null;
        }
    }

    @GetMapping(value = "/feature", produces = MediaType.APPLICATION_XML_VALUE)
    public void getFeatureInfo() {

    }

}
