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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;

/**
 * MBTiles 1.3 规范定义：
 * <a href="https://github.com/mapbox/mbtiles-spec/blob/master/1.3/spec.md">MBTiles 1.3</a>
 */
@Controller
@RequestMapping("/api/tilesets")
public class MapRestTilesetsController {
    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${data}")
    private String data;

    @GetMapping("")
    @ResponseBody
    public String listStyles() {
        //Create the database table:
//        jdbcTemplate.execute("CREATE TABLE tiles (zoom_level integer, tile_column integer, tile_row integer, tile_data blob);");
//        jdbcTemplate.execute("CREATE UNIQUE INDEX tile_index on tiles (zoom_level, tile_column, tile_row);");
//
//        //Insert a record:
//        jdbcTemplate.execute("INSERT INTO beers VALUES ('Stella')");

        //Read records:
//        List<Beer> beers = jdbcTemplate.query("SELECT * FROM beers",
//                (resultSet, rowNum) -> new Beer(resultSet.getString("name")));
//
//        //Print read records:
//        beers.forEach(System.out::println);

        return "Hello World:" + data;
    }

    /**
     * 提供地图页面预览页面
     *
     * @param tileset 待预览的地图瓦片数据库库名称，默认为mbtiles扩展名
     * @return 地图预览页面
     */
    @GetMapping("/{tileset}")
    public String preview(@PathVariable("tileset") String tileset, Model model) {
        model.addAttribute("tileset", tileset);
        return "mapbox";
    }
}
