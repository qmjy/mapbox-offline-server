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

import io.github.qmjy.mapserver.MapServerDataCenter;
import io.github.qmjy.mapserver.config.AppConfig;
import io.github.qmjy.mapserver.model.TilesFileModel;
import io.github.qmjy.mapserver.model.TilesViewModel;
import io.github.qmjy.mapserver.util.SystemUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 系统主页
 * 提供页面访问预览等服务业务场景接口
 */
@Controller
public class MapServerWebController extends BaseController {
    private final Logger logger = LoggerFactory.getLogger(MapServerWebController.class);
    private final MapServerDataCenter mapServerDataCenter;
    private final AppConfig appConfig;

    public MapServerWebController(AppConfig appConfig) {
        this.appConfig = appConfig;
        this.mapServerDataCenter = MapServerDataCenter.getInstance();
    }


    /**
     * 系统首页
     *
     * @return 系统首页
     */
    @GetMapping("")
    public String index(Model model, HttpServletRequest request) {
        model.addAttribute("basePath", super.getBasePath(request));
        return "index";
    }


    @GetMapping("/fonts.html")
    public String fonts() {
        return "fonts";
    }

    @GetMapping("/tools.html")
    public String tools(Model model, HttpServletRequest request) {
        model.addAttribute("basePath", super.getBasePath(request));
        File[] tilesets = (File[]) mapServerDataCenter.getTilesMap().values().stream().map(item -> new File(item.getFilePath())).toArray(File[]::new);
        String selectTileset = "";
        if (tilesets.length > 0) {
            List<File> list = Arrays.stream(tilesets).filter(tileset -> tileset.getName().toLowerCase().contains("china")).toList();
            selectTileset = !list.isEmpty() ? list.getFirst().getName() : tilesets[0].getName();
            model.addAttribute("tilesetName", selectTileset);
        }
        Map<String, Object> tileMetaData = mapServerDataCenter.getTileMetaData(selectTileset);
        model.addAttribute("metaData", tileMetaData);
        return "tools";
    }

    /**
     * 获取支持的瓦片数据库列表
     *
     * @return 瓦片数据库列表
     */
    @GetMapping("/tilesets.html")
    public String listTilesets() {
        return "tilesets";
    }

    /**
     * 提供指定瓦片数据库的地图Mapbox预览页面
     *
     * @param tileset 待预览的地图瓦片数据库库名称，默认为mbtiles扩展名
     * @param request HttpServletRequest
     * @param model   前端页面数据模型
     * @return 地图预览页面
     */
    @GetMapping("/mapbox/{tileset}")
    public String mapbox(@PathVariable("tileset") String tileset, HttpServletRequest request, Model model) {
        if (SystemUtils.checkTilesetName(tileset)) {
            return "error";
        }

        model.addAttribute("basePath", super.getBasePath(request));
        if (tileset.endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES)) {
            model.addAttribute("tilesetName", tileset);
            Map<String, Object> tileMetaData = mapServerDataCenter.getTileMetaData(tileset);
            model.addAttribute("metaData", tileMetaData);

            return "pbf".equals(tileMetaData.get("format")) ? "mapbox-vector" : "mapbox-raster";
        }
        if (tileset.endsWith(AppConfig.FILE_EXTENSION_NAME_TPK)) {
            model.addAttribute("tilesetName", tileset);
            Map<String, Object> tileMetaData = mapServerDataCenter.getTileMetaData(tileset);
            model.addAttribute("metaData", tileMetaData);

            //在tpk中，MIXED— 将在包的中心使用 JPEG 格式，在包的边缘使用 PNG32。
            String format = tileMetaData.get("format").toString();
            return "jpg".equals(format) || "jpeg".equals(format) || "png".equals(format) || "webp".equals(format)
                    ? "mapbox-raster" : "mapbox-vector";
        } else {
            StringBuilder sb = new StringBuilder(appConfig.getDataPath());
            sb.append(File.separator).append("tilesets").append(File.separator).append(tileset).append(File.separator).append("metadata.json");
            try {
                String metaData = FileCopyUtils.copyToString(new FileReader(sb.toString()));
                model.addAttribute("tilesetName", tileset);
                model.addAttribute("metaData", metaData);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return "mapbox-pbf";
        }
    }


    /**
     * 提供指定瓦片数据库的地图Openlayers预览页面
     *
     * @param tileset 待预览的地图瓦片数据库库名称，默认为mbtiles扩展名
     * @param request HttpServletRequest
     * @param model   前端页面数据模型
     * @return 地图预览页面
     */
    @GetMapping("/openlayers/{tileset}")
    public String openlayers(@PathVariable("tileset") String tileset, HttpServletRequest request, Model model) {
        if (SystemUtils.checkTilesetName(tileset)) {
            return "error";
        }

        model.addAttribute("basePath", super.getBasePath(request));
        if (tileset.endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES)) {
            model.addAttribute("tilesetName", tileset);
            Map<String, Object> tileMetaData = mapServerDataCenter.getTileMetaData(tileset);
            model.addAttribute("metaData", tileMetaData);

            return "pbf".equals(tileMetaData.get("format")) ? "openlayers-vector" : "openlayers-raster";
        } else {
            StringBuilder sb = new StringBuilder(appConfig.getDataPath());
            sb.append(File.separator).append("tilesets").append(File.separator).append(tileset).append(File.separator).append("metadata.json");
            try {
                String metaData = FileCopyUtils.copyToString(new FileReader(sb.toString()));
                model.addAttribute("tilesetName", tileset);
                model.addAttribute("metaData", metaData);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return "openlayers-pbf";
        }
    }


    /**
     * 字体文件目录
     *
     * @param model 前端页面数据模型
     * @return 字体文件展示页面
     */
    @GetMapping("/fonts")
    public String listFonts(Model model) {
        if (StringUtils.hasLength(appConfig.getDataPath())) {
            File dataFolder = new File(appConfig.getDataPath());
            if (dataFolder.isDirectory() && dataFolder.exists()) {
                File tilesetsFolder = new File(dataFolder, "fonts");
                File[] folders = tilesetsFolder.listFiles(File::isDirectory);
                model.addAttribute("fonts", wrapThymeleafModel(folders));
            }
        } else {
            logger.error("请在data目录配置字体数据...");
        }
        return "fonts";
    }

    /**
     * 展示Sprites列表
     *
     * @return Sprites列表
     */
    @GetMapping("/sprites")
    public String listSprites(Model model) {
        if (StringUtils.hasLength(appConfig.getDataPath())) {
            File dataFolder = new File(appConfig.getDataPath());
            if (dataFolder.isDirectory() && dataFolder.exists()) {
                File tilesetsFolder = new File(dataFolder, "sprites");
                File[] styles = tilesetsFolder.listFiles(File::isDirectory);
                model.addAttribute("sprites", wrapThymeleafModel(styles));
            }
        } else {
            logger.error("请在data目录配置sprites数据...");
        }
        return "sprites";
    }

    /**
     * 展示style列表
     *
     * @return style列表
     */
    @GetMapping("/styles")
    public String listStyles(Model model) {
        if (StringUtils.hasLength(appConfig.getDataPath())) {
            File dataFolder = new File(appConfig.getDataPath());
            if (dataFolder.isDirectory() && dataFolder.exists()) {
                File tilesetsFolder = new File(dataFolder, "styles");
                File[] styles = tilesetsFolder.listFiles(pathname -> !pathname.isDirectory() && pathname.getName().endsWith(AppConfig.FILE_EXTENSION_NAME_JSON));
                model.addAttribute("styles", wrapThymeleafModel(styles));
            }
        } else {
            logger.error("请在data目录配置样式数据...");
        }
        return "styles";
    }

    private List<TilesViewModel> wrapThymeleafModel(File[] files) {
        List<TilesViewModel> dataList = new ArrayList<>();
        if (files == null) {
            return dataList;
        }
        for (File file : files) {
            TilesFileModel tilesFileModel = mapServerDataCenter.getTilesMap().get(file.getName());
            if (tilesFileModel != null) {
                // 大mbtiles文件可能加载还未就绪
                dataList.add(new TilesViewModel(file, tilesFileModel.getMetaDataMap()));
            }
        }
        return dataList;
    }
}
