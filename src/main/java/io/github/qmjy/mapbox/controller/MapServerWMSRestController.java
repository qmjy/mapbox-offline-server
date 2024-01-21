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

import io.swagger.v3.oas.annotations.tags.Tag;
import org.geotools.ows.ServiceException;
import org.geotools.ows.wms.CRSEnvelope;
import org.geotools.ows.wms.WMSCapabilities;
import org.geotools.ows.wms.WebMapServer;
import org.geotools.ows.wms.request.GetMapRequest;
import org.geotools.ows.wms.response.GetMapResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/wms")
@Tag(name = "地图WMS服务管理", description = "地图wms服务接口能力")
public class MapServerWMSRestController {
    private static final Logger logger = LoggerFactory.getLogger(MapServerWMSRestController.class);
    private final WebMapServer webMapServer;
    private final ResourceLoader resourceLoader;

    public MapServerWMSRestController(WebMapServer webMapServer, ResourceLoader resourceLoader) {
        this.webMapServer = webMapServer;
        this.resourceLoader = resourceLoader;
    }

    /**
     * 返回WMS服务的Capabilities文档，通常是一个XML文件，描述了服务的能力和可用的图层
     *
     * @return 服务的能力和可用的图层
     */
    @GetMapping(value = "/capabilities", produces = MediaType.APPLICATION_XML_VALUE)
    public String getCapabilities() {
        WMSCapabilities capabilities = webMapServer.getCapabilities();
        // 这里可以返回Capabilities的XML表示，你可能需要使用一个Marshaller来转换
        return capabilities.toString();
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
     * @param format 图像格式
     * @return 地图数据
     */
    @GetMapping(value = "/map", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<ByteArrayResource> getMap(
            @RequestParam(name = "layers", required = true) String layers,
            @RequestParam(name = "styles", required = false) String styles,
            @RequestParam(name = "bbox", required = true) String bbox,
            @RequestParam(name = "width", required = true) int width,
            @RequestParam(name = "height", required = true) int height,
            @RequestParam(name = "srs", required = true) String srs,
            @RequestParam(name = "format", required = true) String format) throws IOException {

        String[] split = bbox.split(",");
        if (split.length != 4) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        GetMapRequest request = webMapServer.createGetMapRequest();
        request.addLayer(layers, styles);
        request.setBBox(new CRSEnvelope("EPSG:4326", Double.parseDouble(split[0]),
                Double.parseDouble(split[1]), Double.parseDouble(split[2]), Double.parseDouble(split[3])));
        request.setDimensions(width, height);
        request.setSRS(srs);
        request.setFormat(format);

        try {
            GetMapResponse getMapResponse = webMapServer.issueRequest(request);
            String contentType = getMapResponse.getContentType();
            System.out.printf("获得的ContentType: " + contentType);

            InputStream inputStream = getMapResponse.getInputStream();

            int count = inputStream.available();
            byte[] bytes = new byte[count];
            int read = inputStream.read(bytes);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);  //TODO 依据上面的contentType转换，而不是直接写死
            ByteArrayResource resource = new ByteArrayResource(bytes);
            return ResponseEntity.ok().headers(headers).contentLength(bytes.length).body(resource);
        } catch (ServiceException e) {
            logger.error("WebMapServer issue request failed！");
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

//   public void test(){
//       // 加载Shapefile文件
//       Resource resource = resourceLoader.getResource("classpath:your_shapefile.shp");
//       File shapefile = resource.getFile();
//       ShapefileDataStore dataStore = new ShapefileDataStore(shapefile.toURI().toURL());
//       String typeName = dataStore.getTypeNames()[0];
//       SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
//       // 创建样式
//       Style style = SLD.createSimpleStyle(featureSource.getSchema());
//
//       // 创建地图
//       MapContent map = new MapContent();
//       Layer layer = new FeatureLayer(featureSource, style);
//       map.addLayer(layer);
//       // 渲染地图
//       GTRenderer renderer = new StreamingRenderer();
//       renderer.setMapContent(map);
//       BufferedImage image = renderer.render(null);
//       // 输出图片
//       ImageIO.write(image, "png", outputStream);
//       // 清理资源
//       map.dispose();
//       dataStore.dispose();
//   }
}
