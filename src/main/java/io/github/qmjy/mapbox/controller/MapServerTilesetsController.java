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
import io.github.qmjy.mapbox.util.MapServerUtils;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;


/**
 * Mbtiles支持的数据库访问API。<br>
 * MBTiles 1.3 规范定义：<a href="https://github.com/mapbox/mbtiles-spec/blob/master/1.3/spec.md">MBTiles 1.3</a>
 */
@Controller
@RequestMapping("/api/tilesets")
public class MapServerTilesetsController {

    private final Logger logger = LoggerFactory.getLogger(MapServerTilesetsController.class);
    @Autowired
    private MapServerUtils mapServerUtils;
    @Autowired
    private AppConfig appConfig;


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
    public ResponseEntity<ByteArrayResource> loadPbfTitle(@PathVariable("tileset") String tileset, @PathVariable("z") String z,
                                                          @PathVariable("x") String x, @PathVariable("y") String y) {
        if (tileset.endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES)) {
            Optional<JdbcTemplate> jdbcTemplateOpt = mapServerUtils.getDataSource(tileset);
            if (jdbcTemplateOpt.isPresent()) {
                JdbcTemplate jdbcTemplate = jdbcTemplateOpt.get();

                String sql = "SELECT tile_data FROM tiles WHERE zoom_level = " + z + " AND tile_column = " + x + " AND tile_row = " + y;
                try {
                    byte[] bytes = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> rs.getBytes(1));
                    if (bytes != null) {
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(AppConfig.APPLICATION_X_PROTOBUF_VALUE);
                        ByteArrayResource resource = new ByteArrayResource(bytes);
                        return ResponseEntity.ok().headers(headers).contentLength(bytes.length).body(resource);
                    }

                } catch (EmptyResultDataAccessException e) {
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                }
            }
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            StringBuilder sb = new StringBuilder(appConfig.getDataPath());
            sb.append(File.separator).append(tileset).append(File.separator)
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
     * 获取支持的瓦片数据库列表
     *
     * @param model 前端页面数据模型
     * @return 瓦片数据库列表
     */
    @GetMapping("")
    public String listTilesets(Model model) {
        if (StringUtils.hasLength(appConfig.getDataPath())) {
            File dataFolder = new File(appConfig.getDataPath());
            if (dataFolder.isDirectory() && dataFolder.exists()) {
                File tilesetsFolder = new File(dataFolder, "tilesets");
                File[] files = tilesetsFolder.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        if (pathname.isDirectory()) {
                            File[] subFiles = pathname.listFiles();
                            if (subFiles != null) {
                                for (File subFile : subFiles) {
                                    if ("metadata.json".equals(subFile.getName())) {
                                        return true;
                                    }
                                }
                            }
                            return false;
                        } else {
                            return pathname.getName().endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES);
                        }
                    }
                });
                model.addAttribute("tileFiles", files);
            }
        } else {
            System.out.println("请在data目录配置瓦片数据...");
        }
        return "tilesets";
    }


    /**
     * 提供指定瓦片数据库的地图页面预览页面
     *
     * @param tileset 待预览的地图瓦片数据库库名称，默认为mbtiles扩展名
     * @param model   前端页面数据模型
     * @return 地图预览页面
     */
    @GetMapping("/{tileset}")
    public String preview(@PathVariable("tileset") String tileset, Model model) {
        if (tileset.endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES)) {
            model.addAttribute("tilesetName", tileset);
            model.addAttribute("metaData", mapServerUtils.getTileMetaData(tileset));
            return "mapbox-mbtiles";
        } else {
            StringBuilder sb = new StringBuilder(appConfig.getDataPath());
            sb.append(File.separator).append("tilesets").append(File.separator).append(tileset).append(File.separator).append("metadata.json");
            try {
                String metaData = FileCopyUtils.copyToString(new FileReader(new File(sb.toString())));
                model.addAttribute("tilesetName", tileset);
                model.addAttribute("metaData", metaData);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return "mapbox-pbf";
        }
    }
}
