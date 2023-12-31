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

package io.github.qmjy.mapbox.controller;

import io.github.qmjy.mapbox.MapServerDataCenter;
import io.github.qmjy.mapbox.config.AppConfig;
import io.github.qmjy.mapbox.model.MbtilesOfMerge;
import io.github.qmjy.mapbox.model.MbtilesOfMergeProgress;
import io.github.qmjy.mapbox.service.AsyncService;
import io.github.qmjy.mapbox.util.ResponseMapUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;


/**
 * Mbtiles支持的数据库访问API。<br>
 * MBTiles 1.3 规范定义：<a href="https://github.com/mapbox/mbtiles-spec/blob/master/1.3/spec.md">MBTiles 1.3</a>
 *
 * @author liushaofeng
 */
@RestController
@RequestMapping("/api/tilesets")
@Tag(name = "地图瓦片服务管理", description = "Mapbox离线服务接口能力")
public class MapServerTilesetsRestController {
    @Autowired
    private AsyncService asyncService;
    @Autowired
    private MapServerDataCenter mapServerDataCenter;
    @Autowired
    private AppConfig appConfig;

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
    public ResponseEntity<ByteArrayResource> loadJpgTile(@PathVariable("tileset") String tileset, @PathVariable("z") String z,
                                                         @PathVariable("x") String x, @PathVariable("y") String y) {
        return getByteArrayResourceResponseEntity(tileset, z, x, y, MediaType.IMAGE_JPEG);
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
    public ResponseEntity<ByteArrayResource> loadPngTile(@PathVariable("tileset") String tileset, @PathVariable("z") String z,
                                                         @PathVariable("x") String x, @PathVariable("y") String y) {
        return getByteArrayResourceResponseEntity(tileset, z, x, y, MediaType.IMAGE_PNG);
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
    public ResponseEntity<ByteArrayResource> loadPbfTile(@PathVariable("tileset") String tileset, @PathVariable("z") String z,
                                                         @PathVariable("x") String x, @PathVariable("y") String y) {
        if (tileset.endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES)) {
            return getArrayResourceResponseEntity(tileset, z, x, y, AppConfig.APPLICATION_X_PROTOBUF_VALUE);
        } else {
            StringBuilder sb = new StringBuilder(appConfig.getDataPath());
            sb.append(File.separator).append("tilesets").append(File.separator).append(tileset).append(File.separator)
                    .append(z).append(File.separator).append(x).append(File.separator).append(y).append(AppConfig.FILE_EXTENSION_NAME_PBF);
            File pbfFile = new File(sb.toString());
            if (pbfFile.exists()) {
                try {
                    byte[] buffer = FileCopyUtils.copyToByteArray(pbfFile);
                    IOUtils.readFully(Files.newInputStream(pbfFile.toPath()), buffer);
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(AppConfig.APPLICATION_X_PROTOBUF_VALUE);
                    ByteArrayResource resource = new ByteArrayResource(buffer);
                    return ResponseEntity.ok().headers(headers).contentLength(buffer.length).body(resource);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
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
    @Parameter(name = "mergeInfo", description = "合并对象模型")
    @ApiResponse(responseCode = "200", description = "成功响应", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    public ResponseEntity<Map<String, Object>> merge(@RequestBody MbtilesOfMerge mergeInfo) {
        List<String> sourceNamePaths = new ArrayList<>();
        String basePath = appConfig.getDataPath() + File.separator + "tilesets" + File.separator;

        String[] split = mergeInfo.getSourceNames().split(";");
        for (String fileName : split) {
            String filePath = basePath + fileName;
            if (new File(filePath).exists() || fileName.toLowerCase(Locale.getDefault()).endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES)) {
                sourceNamePaths.add(filePath);
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

    private ResponseEntity<ByteArrayResource> getByteArrayResourceResponseEntity(String tileset, String z, String x, String y, MediaType mediaType) {
        if (tileset.endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES)) {
            return getArrayResourceResponseEntity(tileset, z, x, y, mediaType);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<ByteArrayResource> getArrayResourceResponseEntity(String tileset, String z, String x, String y, MediaType mediaType) {
        Optional<JdbcTemplate> jdbcTemplateOpt = mapServerDataCenter.getDataSource(tileset);
        if (jdbcTemplateOpt.isPresent()) {
            JdbcTemplate jdbcTemplate = jdbcTemplateOpt.get();

            String sql = "SELECT tile_data FROM tiles WHERE zoom_level = " + z + " AND tile_column = " + x + " AND tile_row = " + y;
            try {
                byte[] bytes = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> rs.getBytes(1));
                if (bytes != null) {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(mediaType);
                    ByteArrayResource resource = new ByteArrayResource(bytes);
                    return ResponseEntity.ok().headers(headers).contentLength(bytes.length).body(resource);
                }
            } catch (EmptyResultDataAccessException e) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
}
