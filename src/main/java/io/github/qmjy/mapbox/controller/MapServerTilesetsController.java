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

import io.github.qmjy.mapbox.config.AppConfig;
import io.github.qmjy.mapbox.MapServerDataCenter;
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
import java.util.Optional;


/**
 * Mbtiles支持的数据库访问API。<br>
 * MBTiles 1.3 规范定义：<a href="https://github.com/mapbox/mbtiles-spec/blob/master/1.3/spec.md">MBTiles 1.3</a>
 *
 * @author liushaofeng
 */
@RestController
@RequestMapping("/api/tilesets")
public class MapServerTilesetsController {
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
