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

import com.zaxxer.hikari.HikariDataSource;
import io.github.qmjy.mapbox.config.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;

/**
 * Mbtiles支持的数据库访问API。<br/>
 * MBTiles 1.3 规范定义：<a href="https://github.com/mapbox/mbtiles-spec/blob/master/1.3/spec.md">MBTiles 1.3</a>
 */
@Controller
@RequestMapping("/api/tilesets")
public class MapRestTilesetsController {
    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    public byte[] loadPbfTitle(@PathVariable("tileset") String tileset, @PathVariable("z") String z,
                               @PathVariable("x") String x, @PathVariable("y") String y) {
        System.out.printf("load tileset z: " + z + " x:" + x + " y:" + y);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
//        ImageIO.write(bufferedImage, "pbf", out);
        return out.toByteArray();
    }

    /**
     * 返回png格式瓦片数据
     *
     * @param tileset 瓦片数据库名称
     * @param z       地图缩放层级
     * @param x       地图的x轴瓦片坐标
     * @param y       地图的y轴瓦片坐标
     * @return png格式的瓦片数据
     */
    @GetMapping(value = "/{tileset}/{z}/{x}/{y}.png", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public byte[] loadPngTitle(@PathVariable("tileset") String tileset, @PathVariable("z") String z,
                               @PathVariable("x") String x, @PathVariable("y") String y, HttpServletResponse response) {
        System.out.printf("load tileset z: " + z + " x:" + x + " y:" + y);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
//        ImageIO.write(bufferedImage, "png", out);
        return out.toByteArray();
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
                        return pathname.getName().endsWith("admin.mbtiles");
                    }
                });

                model.addAttribute("tileFiles", files);
            }
        } else {
            System.out.println("未配置data数据目录...");
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
        model.addAttribute("tilesetName", tileset);
        return "mapbox";
    }
}
