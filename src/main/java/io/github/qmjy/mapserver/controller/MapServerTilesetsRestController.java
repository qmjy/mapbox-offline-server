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

import com.wdtinc.mapbox_vector_tile.adapt.jts.model.JtsMvt;
import io.github.qmjy.mapserver.MapServerDataCenter;
import io.github.qmjy.mapserver.config.AppConfig;
import io.github.qmjy.mapserver.model.MbtilesOfMerge;
import io.github.qmjy.mapserver.model.MbtilesOfMergeProgress;
import io.github.qmjy.mapserver.model.MetaData;
import io.github.qmjy.mapserver.model.osm.pbf.OsmPbfTileOfReadable;
import io.github.qmjy.mapserver.service.AsyncService;
import io.github.qmjy.mapserver.util.IOUtils;
import io.github.qmjy.mapserver.util.ResponseMapUtil;
import io.github.qmjy.mapserver.util.VectorTileUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.geotools.tpk.TPKFile;
import org.geotools.tpk.TPKTile;
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


/**
 * Mbtiles、TPK支持的数据库访问API。<br>
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

    public MapServerTilesetsRestController(AsyncService asyncService, MapServerDataCenter mapServerDataCenter, AppConfig appConfig) {
        this.asyncService = asyncService;
        this.mapServerDataCenter = mapServerDataCenter;
        this.appConfig = appConfig;
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
    public ResponseEntity<ByteArrayResource> loadJpegTile(@PathVariable("tileset") String tileset, @PathVariable("z") int z, @PathVariable("x") int x, @PathVariable("y") int y) {
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
    public ResponseEntity<ByteArrayResource> loadJpgTile(@PathVariable("tileset") String tileset, @PathVariable("z") int z, @PathVariable("x") int x, @PathVariable("y") int y) {
        if (tileset.endsWith(AppConfig.FILE_EXTENSION_NAME_TPK)) {
            String lowerCase = mapServerDataCenter.getTpkMetaData(tileset).getFormat().toLowerCase(Locale.getDefault());
            if (!lowerCase.endsWith("jpg") && !lowerCase.endsWith("jpeg")) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            return getByteArrayResourceResponseEntityInTpk(tileset, z, x, y);
        } else {
            Optional<byte[]> OptionalResource = getByteArrayResourceResponseEntity(tileset, z, x, y);
            if (OptionalResource.isPresent()) {
                byte[] bytes = OptionalResource.get();
                return wrapResponse(bytes, MediaType.IMAGE_JPEG);
            }
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
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
    public ResponseEntity<ByteArrayResource> loadWebpTile(@PathVariable("tileset") String tileset, @PathVariable("z") int z, @PathVariable("x") int x, @PathVariable("y") int y) {
        if (tileset.endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES)) {
            Optional<byte[]> OptionalResource = getByteArrayResourceResponseEntity(tileset, z, x, y);
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
    public ResponseEntity<ByteArrayResource> loadPngTile(@PathVariable("tileset") String tileset, @PathVariable("z") int z, @PathVariable("x") int x, @PathVariable("y") int y) {
        if (tileset.endsWith(AppConfig.FILE_EXTENSION_NAME_TPK)) {
            if (!mapServerDataCenter.getTpkMetaData(tileset).getFormat().toLowerCase(Locale.getDefault()).endsWith("png")) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            return getByteArrayResourceResponseEntityInTpk(tileset, z, x, y);
        } else {
            Optional<byte[]> OptionalResource = getByteArrayResourceResponseEntity(tileset, z, x, y);
            if (OptionalResource.isPresent()) {
                byte[] bytes = OptionalResource.get();
                return wrapResponse(bytes, MediaType.IMAGE_PNG);
            }
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
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
    public ResponseEntity<ByteArrayResource> loadPbfTile(@PathVariable("tileset") String tileset, @PathVariable("z") int z, @PathVariable("x") int x, @PathVariable("y") int y) {
        if (tileset.endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES)) {
            Optional<byte[]> optionalRes = getBytesFromSqlite(tileset, z, x, y);
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

    private ResponseEntity<ByteArrayResource> getByteArrayResourceResponseEntityInTpk(String tileset, int z, int x, int y) {
        String format = mapServerDataCenter.getTpkMetaData(tileset).getFormat();
        TPKFile tpkData = mapServerDataCenter.getTpkData(tileset);
        List<TPKTile> tiles = tpkData.getTiles(z, tpkData.getMaxColumn(z), 0, 0, tpkData.getMaxRow(z), format);
        if (tiles != null) {
            for (TPKTile tile : tiles) {
                if (tile.row == y && tile.col == x) {
                    return wrapResponse(tile.tileData, AppConfig.APPLICATION_X_PROTOBUF_VALUE);
                }
            }
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
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
    public ResponseEntity<Map<String, Object>> decodePbf(HttpServletRequest req,
                                                         @PathVariable("tileset") String tileset,
                                                         @PathVariable("z") int z,
                                                         @PathVariable("x") int x,
                                                         @PathVariable("y") int y) {
        OsmPbfTileOfReadable tileOfReadable = new OsmPbfTileOfReadable(req, z, x, y);

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
            return getBytesFromSqlite(tileset, z, x, y);
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
     * 获取指定底图数据的元数据
     *
     * @param tileset 底图数据文件名称
     * @return 元数据
     */
    @GetMapping(value = "/{tileset}/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Operation(summary = "获取底图数据的元数据", description = "获取底图数据的元数据。")
    public ResponseEntity<Map<String, Object>> metadata(@Parameter(description = "待查询的底图文件或文件夹名字，例如：admin.mbtiles。") @PathVariable("tileset") String tileset) {
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

        if (tileset.endsWith(AppConfig.FILE_EXTENSION_NAME_TPK)) {
            MetaData tpkMetaData = mapServerDataCenter.getTpkMetaData(tileset);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ResponseMapUtil.ok(tpkMetaData));
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

    private ResponseEntity<ByteArrayResource> wrapResponse(byte[] data, @Nullable MediaType mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        ByteArrayResource resource = new ByteArrayResource(data);
        return ResponseEntity.ok().headers(headers).contentLength(data.length).body(resource);
    }

    private Optional<byte[]> getByteArrayResourceResponseEntity(String tileset, int z, int x, int y) {
        if (tileset.endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES)) {
            return getBytesFromSqlite(tileset, z, x, y);
        }
        return Optional.empty();
    }

    private Optional<byte[]> getBytesFromSqlite(String tileset, int z, int x, int y) {
        boolean compressed = MapServerDataCenter.getTilesMap().get(tileset).isCompressed();
        Optional<JdbcTemplate> jdbcTemplateOpt = mapServerDataCenter.getDataSource(tileset);
        if (jdbcTemplateOpt.isPresent()) {
            JdbcTemplate jdbcTemplate = jdbcTemplateOpt.get();
            String sql = "SELECT tile_data FROM tiles WHERE zoom_level = " + z + " AND tile_column = " + x + " AND tile_row = " + y;
            try {
                byte[] value = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> rs.getBytes(1));
                return value == null ? Optional.empty() : Optional.of(compressed ? IOUtils.decompress(value) : value);
            } catch (EmptyResultDataAccessException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

}
