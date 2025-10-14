/*
 * Copyright (c) 2023 QMJY.
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

package io.github.qmjy.mapserver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.qmjy.mapserver.MapServerDataCenter;
import io.github.qmjy.mapserver.config.AppConfig;
import io.github.qmjy.mapserver.model.*;
import io.github.qmjy.mapserver.model.osm.pbf.OsmPbfTileOfReadable;
import io.github.qmjy.mapserver.service.AsyncService;
import io.github.qmjy.mapserver.util.IOUtils;
import io.github.qmjy.mapserver.util.ResponseMapUtil;
import io.github.qmjy.mapserver.util.SystemUtils;
import io.github.qmjy.mapserver.util.VectorTileUtils;
import io.github.sebasbaumh.mapbox.vectortile.adapt.jts.model.JtsMvt;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.geotools.tpk.TPKTile;
import org.geotools.tpk.TPKZoomLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;


/**
 * Mbtiles支持的数据库访问API。<br>
 * MBTiles 1.3 规范定义：<a href="https://github.com/mapbox/mbtiles-spec/blob/master/1.3/spec.md">MBTiles 1.3</a>
 *
 * @author liushaofeng
 */
@RestController
@RequestMapping("/api/tilesets")
@Tag(name = "地图瓦片服务管理", description = "地图瓦片服务接口能力")
public class MapServerTilesetsRestController {
    private static final Logger logger = LoggerFactory.getLogger(MapServerTilesetsRestController.class);
    private final AsyncService asyncService;
    private final MapServerDataCenter mapServerDataCenter;
    private final AppConfig appConfig;

    public MapServerTilesetsRestController(AsyncService asyncService, AppConfig appConfig) {
        this.asyncService = asyncService;
        this.mapServerDataCenter = MapServerDataCenter.getInstance();
        this.appConfig = appConfig;
    }

    /**
     * 返回瓦片集列表
     *
     * @return 瓦片集列表
     */
    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Operation(summary = "瓦片集", description = "当前已经被加载到的有效瓦片集数据")
    public ResponseEntity<Map<String, Object>> getTilesList() {
        Map<String, TilesFileModel> tilesMap = mapServerDataCenter.getTilesMap();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.ok(tilesMap));
    }

    /**
     * 返回瓦片集数据的元数据，默认返回<a href="https://github.com/mapbox/tilejson-spec/tree/master/3.0.0">3.0.0</a>版本
     *
     * @return 瓦片集的元数据
     */
    @GetMapping(value = "/{tileset}/tiles.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Operation(summary = "获取瓦片集数据的元数据信息", description = "获取瓦片集数据的元数据信息")
    public ResponseEntity<Map<String, Object>> getTilesJson(@Parameter(description = "待查询的瓦片数据源或文件夹名字，例如：Chengdu.mbtiles") @PathVariable("tileset") String tileset) {
        TilesFileModel tilesFileModel = mapServerDataCenter.getTilesFileModel(tileset);
        if (SystemUtils.checkTilesetName(tileset)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        if (tilesFileModel == null) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound());
        }

        Map<String, Object> metaDataMap = tilesFileModel.getMetaDataMap();
        Map<String, Object> data = new HashMap<>();
        data.put("tilejson", "3.0.0");
        data.put("tiles", new String[0]);
        if (metaDataMap.get("json") != null) {
            data.put("vector_layers", getJsonObj(metaDataMap.get("json")));
        }
        data.put("bounds", metaDataMap.get("bounds"));
        data.put("center", "[" + metaDataMap.get("center") + "]");
        data.put("data", "[]");
        data.put("fillzoom", "14");
        data.put("grids", "[]");
        data.put("legend", "");
        data.put("maxzoom", metaDataMap.get("maxzoom"));
        data.put("minzoom", metaDataMap.get("minzoom"));
        data.put("name", metaDataMap.get("name"));
        data.put("scheme", "tms");
        data.put("template", "");
        data.put("version", metaDataMap.get("version"));
        data.put("description", metaDataMap.get("description"));
        data.put("format", metaDataMap.get("format"));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(data);
    }

    private List<VectorLayers> getJsonObj(Object json) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            VectorLayersJsonObj vectorLayersJsonObj = objectMapper.readValue(json.toString(), VectorLayersJsonObj.class);
            return vectorLayersJsonObj.getVector_layers();
        } catch (IOException e) {
            logger.error("convert to json object failed!");
        }
        return new ArrayList<>();
    }


    /**
     * 加载图片瓦片数据
     *
     * @param tileset 瓦片数据库名称
     * @param z       地图缩放层级
     * @param x       地图的x轴瓦片坐标
     * @param y       地图的y轴瓦片坐标
     * @return jpg格式的瓦片数据
     */
    @GetMapping(value = "/{tileset}/{z}/{x}/{y}.jpeg", produces = MediaType.IMAGE_JPEG_VALUE)
    @ResponseBody
    @Operation(summary = "获取JPG格式瓦片数据", description = "获取JPG格式瓦片数据。")
    public ResponseEntity<ByteArrayResource> loadJpegTile(@Parameter(description = "待查询的瓦片数据源或文件夹名字，例如：Chengdu.mbtiles") @PathVariable("tileset") String tileset, @Parameter(description = "待查询的底图瓦片层级zoom_level") @PathVariable("z") int z, @Parameter(description = "待查询的底图瓦片坐标x") @PathVariable("x") int x, @Parameter(description = "待查询的底图瓦片坐标y") @PathVariable("y") int y) {
        if (SystemUtils.checkTilesetName(tileset)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        return this.loadJpgTile(tileset, z, x, y);
    }

    /**
     * 加载图片瓦片数据
     *
     * @param tileset 瓦片数据库名称
     * @param z       地图缩放层级
     * @param x       地图的x轴瓦片坐标
     * @param y       地图的y轴瓦片坐标
     * @return jpg格式的瓦片数据
     */
    @GetMapping(value = "/{tileset}/{z}/{x}/{y}.jpg", produces = MediaType.IMAGE_JPEG_VALUE)
    @ResponseBody
    @Operation(summary = "获取JPG格式瓦片数据", description = "获取JPG格式瓦片数据。")
    public ResponseEntity<ByteArrayResource> loadJpgTile(@Parameter(description = "待查询的瓦片数据源或文件夹名字，例如：Chengdu.mbtiles") @PathVariable("tileset") String tileset, @Parameter(description = "待查询的底图瓦片层级zoom_level") @PathVariable("z") int z, @Parameter(description = "待查询的底图瓦片坐标x") @PathVariable("x") int x, @Parameter(description = "待查询的底图瓦片坐标y") @PathVariable("y") int y) {
        if (SystemUtils.checkTilesetName(tileset)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        Optional<byte[]> OptionalResource = getByteArrayResourceResponseEntity(tileset, z, x, y, MediaType.IMAGE_JPEG);
        if (OptionalResource.isPresent()) {
            byte[] bytes = OptionalResource.get();
            return wrapResponse(bytes, MediaType.IMAGE_JPEG);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    /**
     * 加载图片瓦片数据
     *
     * @param tileset 瓦片数据库名称
     * @param z       地图缩放层级
     * @param x       地图的x轴瓦片坐标
     * @param y       地图的y轴瓦片坐标
     * @return png格式的瓦片数据
     */
    @GetMapping(value = "/{tileset}/{z}/{x}/{y}.webp", produces = AppConfig.IMAGE_WEBP_VALUE)
    @ResponseBody
    @Operation(summary = "获取WEBP格式瓦片数据", description = "获取WEBP格式瓦片数据。")
    public ResponseEntity<ByteArrayResource> loadWebpTile(@Parameter(description = "待查询的瓦片数据源或文件夹名字，例如：Chengdu.mbtiles") @PathVariable("tileset") String tileset, @Parameter(description = "待查询的底图瓦片层级zoom_level") @PathVariable("z") int z, @Parameter(description = "待查询的底图瓦片坐标x") @PathVariable("x") int x, @Parameter(description = "待查询的底图瓦片坐标y") @PathVariable("y") int y) {
        if (SystemUtils.checkTilesetName(tileset)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        if (tileset.endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES)) {
            Optional<byte[]> OptionalResource = getByteArrayResourceResponseEntity(tileset, z, x, y, AppConfig.IMAGE_WEBP);
            if (OptionalResource.isPresent()) {
                byte[] bytes = OptionalResource.get();
                return wrapResponse(bytes, AppConfig.IMAGE_WEBP);
            }
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }


    /**
     * 加载图片瓦片数据
     *
     * @param tileset 瓦片数据库名称
     * @param z       地图缩放层级
     * @param x       地图的x轴瓦片坐标
     * @param y       地图的y轴瓦片坐标
     * @return png格式的瓦片数据
     */
    @GetMapping(value = "/{tileset}/{z}/{x}/{y}.png", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    @Operation(summary = "获取PNG格式瓦片数据", description = "获取PNG格式瓦片数据。")
    public ResponseEntity<ByteArrayResource> loadPngTile(@Parameter(description = "待查询的瓦片数据源或文件夹名字，例如：Chengdu.mbtiles") @PathVariable("tileset") String tileset, @Parameter(description = "待查询的底图瓦片层级zoom_level") @PathVariable("z") int z, @Parameter(description = "待查询的底图瓦片坐标x") @PathVariable("x") int x, @Parameter(description = "待查询的底图瓦片坐标y") @PathVariable("y") int y) {
        if (SystemUtils.checkTilesetName(tileset)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        Optional<byte[]> OptionalResource = getByteArrayResourceResponseEntity(tileset, z, x, y, MediaType.IMAGE_PNG);
        if (OptionalResource.isPresent()) {
            byte[] bytes = OptionalResource.get();
            return wrapResponse(bytes, MediaType.IMAGE_PNG);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }


    /**
     * 加载pbf格式的瓦片数据
     *
     * @param tileset 瓦片数据库名称
     * @param z       地图缩放层级
     * @param x       地图的x轴瓦片坐标
     * @param y       地图的y轴瓦片坐标
     * @return pbf格式的瓦片数据
     */
    @GetMapping(value = "/{tileset}/{z}/{x}/{y}.pbf", produces = "application/x-protobuf")
    @ResponseBody
    @Operation(summary = "获取PBF格式瓦片数据", description = "获取PBF格式瓦片数据。")
    public ResponseEntity<ByteArrayResource> loadPbfTile(@Parameter(description = "待查询的瓦片数据源或文件夹名字，例如：Chengdu.mbtiles") @PathVariable("tileset") String tileset, @Parameter(description = "待查询的底图瓦片层级zoom_level") @PathVariable("z") int z, @Parameter(description = "待查询的底图瓦片坐标x") @PathVariable("x") int x, @Parameter(description = "待查询的底图瓦片坐标y") @PathVariable("y") int y) {
        if (SystemUtils.checkTilesetName(tileset)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        if (tileset.endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES)) {
            Optional<byte[]> optionalRes = getBytesFromSqlite(tileset, z, x, y, null);
            if (optionalRes.isPresent()) {
                byte[] bytes = optionalRes.get();
                return wrapResponse(bytes, AppConfig.APPLICATION_X_PROTOBUF_VALUE);
            } else {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
        } else {
            if (tileset.indexOf(".") > 0) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            String sb = appConfig.getDataPath() + File.separator + "tilesets" + File.separator + tileset + File.separator + z + File.separator + x + File.separator + y + AppConfig.FILE_EXTENSION_NAME_PBF;
            File pbfFile = new File(sb);
            if (pbfFile.exists()) {
                try {
                    byte[] buffer = FileCopyUtils.copyToByteArray(pbfFile);
                    return wrapResponse(IOUtils.decompress(buffer), AppConfig.APPLICATION_X_PROTOBUF_VALUE);
                } catch (IOException e) {
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                }
            }
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }


    /**
     * 解析mapbox vector pbf原始数据,以JSON格式展示解析结果。
     *
     * @param tileset 瓦片数据库名称
     * @param z       地图缩放层级
     * @param x       地图的x轴瓦片坐标
     * @param y       地图的y轴瓦片坐标
     * @return pbf解码后的json格式数据
     */
    @GetMapping(value = "/{tileset}/{z}/{x}/{y}/pbf", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Operation(summary = "获取mapbox vector pbf原始数据", description = "解析mapbox vector pbf原始数据，以可读的json格式进行展示。瓦片文件名称和坐标地址须正确。目前只支持本服务器上mapbox vector规范下的PBF瓦片数据解析。")
    public ResponseEntity<Map<String, Object>> decodePbf(HttpServletRequest req, @Parameter(description = "待查询的瓦片数据源或文件夹名字，例如：Chengdu.mbtiles") @PathVariable("tileset") String tileset, @Parameter(description = "待查询的底图瓦片层级zoom_level") @PathVariable("z") int z, @Parameter(description = "待查询的底图瓦片坐标x") @PathVariable("x") int x, @Parameter(description = "待查询的底图瓦片坐标y") @PathVariable("y") int y) {
        OsmPbfTileOfReadable tileOfReadable = new OsmPbfTileOfReadable(req, z, x, y);
        if (SystemUtils.checkTilesetName(tileset)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        Optional<byte[]> optionalBytes = getPbfBytes(tileset, z, x, y);
        if (optionalBytes.isPresent()) {
            byte[] bytes = optionalBytes.get();
            tileOfReadable.setTileLength(bytes.length);
            Optional<JtsMvt> jtsMvt = VectorTileUtils.decodeJtsMvt(new ByteArrayInputStream(bytes));
            if (jtsMvt.isPresent()) {
                tileOfReadable.wrapStatistics(jtsMvt.get());
                Map<String, Object> ok = ResponseMapUtil.ok(tileOfReadable);
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ok);
            }
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound());
    }

    private Optional<byte[]> getPbfBytes(String tileset, int z, int x, int y) {
        if (tileset.endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES)) {
            return getBytesFromSqlite(tileset, z, x, y, null);
        } else {
            if (!tileset.contains(".")) {
                String sb = appConfig.getDataPath() + File.separator + "tilesets" + File.separator + tileset + File.separator + z + File.separator + x + File.separator + y + AppConfig.FILE_EXTENSION_NAME_PBF;
                File pbfFile = new File(sb);
                if (pbfFile.exists()) {
                    try {
                        return Optional.of(FileCopyUtils.copyToByteArray(pbfFile));
                    } catch (IOException e) {
                        logger.error("Load pbf file failed!");
                    }
                }
            }
            return Optional.empty();
        }
    }

    /**
     * 释放万片资源
     *
     * @param tileset 待释放的瓦片资源
     * @return 释放结果
     */
    @DeleteMapping(value = "/{tileset}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Operation(summary = "释放mbtiles文件", description = "释放mbtiles文件。")
    public ResponseEntity<Object> release(@Parameter(description = "待释放的瓦片数据源或文件夹名字，例如：admin.mbtiles。") @PathVariable("tileset") String tileset) {
        mapServerDataCenter.releaseDataSource(tileset);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("Success");
    }


    /**
     * 获取指定底图数据的元数据
     *
     * @param tileset 底图数据文件名称
     * @return 元数据
     */
    @GetMapping(value = "/{tileset}/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Operation(summary = "获取底图数据的元数据", description = "获取底图数据的元数据。")
    public ResponseEntity<Map<String, Object>> metadata(@Parameter(description = "待查询的瓦片数据源或文件夹名字，例如：admin.mbtiles。") @PathVariable("tileset") String tileset) {
        if (SystemUtils.checkTilesetName(tileset)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        if (tileset.endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES)) {
            Optional<JdbcTemplate> jdbcTemplateOpt = mapServerDataCenter.getDataSource(tileset);
            if (jdbcTemplateOpt.isPresent()) {
                JdbcTemplate jdbcTemplate = jdbcTemplateOpt.get();
                try {
                    List<Map<String, Object>> maps = jdbcTemplate.queryForList("SELECT * FROM metadata");
                    return ResponseEntity.ok().body(ResponseMapUtil.ok(wrapMap(maps)));
                } catch (EmptyResultDataAccessException e) {
                    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound());
                }
            }
        }

        StringBuilder sb = new StringBuilder(appConfig.getDataPath());
        sb.append(File.separator).append("tilesets").append(File.separator).append(tileset).append(File.separator).append("metadata.json");
        if (new File(sb.toString()).exists()) {
            try {
                String s = Files.readString(Path.of(sb.toString()));
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.ok(s));
            } catch (IOException e) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.notFound());
    }

    private Map<String, Object> wrapMap(List<Map<String, Object>> maps) {
        Map<String, Object> result = new HashMap<>();
        for (Map<String, Object> map : maps) {
            result.put(String.valueOf(map.get("name")), map.get("value"));
        }
        return result;
    }


    /**
     * 将多个mbtiles文件合并成一个mbtiles文件。
     * 部分文件不存在则跳过，所有不存在或者目标文件已存在则合并失败。
     *
     * @return 任务任务ID和进度。
     */
    @PostMapping(value = "/merge")
    @ResponseBody
    @Operation(summary = "合并多个Mbtiles文件", description = "将多个mbtiles文件合并成一个mbtiles文件。")
    @Parameter(name = "mergeInfo", description = "合并对象模型，多个文件用英文分号分割")
    @ApiResponse(responseCode = "200", description = "成功响应", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    public ResponseEntity<Map<String, Object>> merge(@RequestBody MbtilesOfMerge mergeInfo) {
        List<String> sourceNamePaths = new ArrayList<>();
        String basePath = appConfig.getDataPath() + File.separator + "tilesets" + File.separator;

        String[] split = mergeInfo.getSourceNames().split(";");
        for (String fileName : split) {
            if (new File(basePath + fileName).exists() || fileName.toLowerCase(Locale.getDefault()).endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES)) {
                sourceNamePaths.add(basePath + fileName);
            }
        }
        if (sourceNamePaths.isEmpty()) {
            Map<String, Object> ok = ResponseMapUtil.nok(ResponseMapUtil.STATUS_NOT_FOUND, "至少得存在一个合法的mbtiles文件，且文件已存在！");
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ok);
        }

        if (new File(basePath + mergeInfo.getTargetName()).exists()) {
            Map<String, Object> ok = ResponseMapUtil.nok(ResponseMapUtil.STATUS_RESOURCE_ALREADY_EXISTS, "目标资源已存在！");
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ok);
        }
        String taskId = asyncService.computeTaskId(sourceNamePaths);
        Optional<MbtilesOfMergeProgress> taskOpt = asyncService.getTask(taskId);
        if (taskOpt.isPresent()) {
            Map<String, Object> ok = ResponseMapUtil.ok(taskOpt.get());
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ok);
        } else {
            asyncService.submit(taskId, sourceNamePaths, basePath + mergeInfo.getTargetName());
            Map<String, Object> ok = ResponseMapUtil.ok(new MbtilesOfMergeProgress(taskId, 0));
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ok);
        }
    }

    public static byte[] generateImage(MediaType mediaType, String text) throws IOException {
        if (mediaType == null) {
            return new byte[0];
        }

        if (!mediaType.equals(AppConfig.IMAGE_WEBP)
                && !mediaType.equals(MediaType.IMAGE_GIF)
                && !mediaType.equals(MediaType.IMAGE_PNG)
                && !mediaType.equals(MediaType.IMAGE_JPEG)) {
            logger.error("不支持的图片格式: {}", mediaType);
            return new byte[0];
        }

        int width = 256;
        int height = 256;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.GRAY);
        g2d.fillRect(0, 0, width, height);

        g2d.setColor(Color.BLACK);

        Font font = new Font("Arial", Font.BOLD, 13);
        g2d.setFont(font);
        int textWidth = g2d.getFontMetrics().stringWidth(text);
        int textHeight = g2d.getFontMetrics().getHeight();
        int x = (width - textWidth) / 2;
        int y = (height / 2) - (textHeight / 2);
        g2d.drawString(text, x, y);

        Font subtitleFont = new Font("Arial", Font.PLAIN, 10);
        g2d.setFont(subtitleFont);

        String subtitle = "https://github.com/qmjy/mapbox-offline-server";
        int subtitleWidth = g2d.getFontMetrics().stringWidth(subtitle);
        int subtitleX = (width - subtitleWidth) / 2;
        int subtitleY = y + textHeight;

        g2d.drawString(subtitle, subtitleX, subtitleY);

        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, mediaType.getSubtype(), baos);

        return baos.toByteArray();
    }

    private ResponseEntity<ByteArrayResource> wrapResponse(byte[] data, @Nullable MediaType mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        ByteArrayResource resource = new ByteArrayResource(data);
        return ResponseEntity.ok().headers(headers).contentLength(data.length).body(resource);
    }

    private Optional<byte[]> getByteArrayResourceResponseEntity(String tileset, int z, int x, int y, MediaType defaultMediaType) {
        String extension = tileset.substring(tileset.lastIndexOf('.') + 1);
        return switch (extension) {
            case "mbtiles" -> getBytesFromSqlite(tileset, z, x, y, defaultMediaType);
            case "tpk" -> getBytesFromTpk(tileset, z, x, y, defaultMediaType);
            default -> Optional.empty();
        };
    }

    private Optional<byte[]> getBytesFromTpk(String tileset, int zoom, int x, int y, MediaType defaultMediaType) {
        TilesFileModel tilesFileModel = mapServerDataCenter.getTilesMap().get(tileset);
        TPKZoomLevel tpkZoomLevel = tilesFileModel.getZoomLevelMap().get((long) zoom);
        if (tpkZoomLevel == null) {
            return Optional.empty();
        }
        List<TPKTile> tiles = tilesFileModel.getTpkFile().getTiles(zoom,
                tpkZoomLevel.getMaxRow(), tpkZoomLevel.getMinRow(), tpkZoomLevel.getMinColumn(), tpkZoomLevel.getMaxColumn(),
                defaultMediaType.getSubtype());
        for (TPKTile tile : tiles) {
            if (tile.col == x && tile.row == y) {
                return tile.tileData.length == 0 ? Optional.empty() : Optional.of(tile.tileData);
            }
        }
        return Optional.empty();
    }

    private Optional<byte[]> getBytesFromSqlite(String tileset, int z, int x, int y, MediaType defaultMediaType) {
        TilesFileModel tilesFileModel = mapServerDataCenter.getTilesMap().get(tileset);
        if (tilesFileModel == null) {
            return Optional.empty();
        }

        Optional<JdbcTemplate> jdbcTemplateOpt = mapServerDataCenter.getDataSource(tileset);
        if (jdbcTemplateOpt.isPresent()) {
            JdbcTemplate jdbcTemplate = jdbcTemplateOpt.get();
            String sql = "SELECT tile_data FROM tiles WHERE zoom_level = " + z + " AND tile_column = " + x + " AND tile_row = " + y;
            try {
                byte[] value = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> rs.getBytes(1));
                return value == null ? getDefaultTile(z, x, y, defaultMediaType) : Optional.of(tilesFileModel.isCompressed() ? IOUtils.decompress(value) : value);
            } catch (EmptyResultDataAccessException e) {
                return getDefaultTile(z, x, y, defaultMediaType);
            }
        }
        return Optional.empty();
    }

    private Optional<byte[]> getDefaultTile(int z, int x, int y, MediaType defaultMediaType) {
        if (appConfig.isEnableDefaultTile() && defaultMediaType != null) {
            try {
                String text = z + "/" + x + "/" + y + "." + defaultMediaType.getSubtype() + " not found!";
                byte[] bytes = generateImage(defaultMediaType, text);
                if (bytes.length != 0) {
                    return Optional.of(bytes);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return Optional.empty();
    }
}
