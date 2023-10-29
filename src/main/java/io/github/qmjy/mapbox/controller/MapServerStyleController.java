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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;

@Controller
@RequestMapping("/api/styles")
public class MapServerStyleController {
    @Autowired
    private AppConfig appConfig;

    /**
     * 展示style列表
     *
     * @return style列表
     */
    @GetMapping("")
    public String listStyles(Model model) {
        if (StringUtils.hasLength(appConfig.getDataPath())) {
            File dataFolder = new File(appConfig.getDataPath());
            if (dataFolder.isDirectory() && dataFolder.exists()) {
                File tilesetsFolder = new File(dataFolder, "styles");
                File[] styles = tilesetsFolder.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return !pathname.isDirectory() && pathname.getName().endsWith(AppConfig.FILE_EXTENSION_NAME_JSON);
                    }
                });
                model.addAttribute("styles", styles);
            }
        } else {
            System.out.println("请在data目录配置样式数据...");
        }
        return "styles";
    }


    /**
     * 加载样式
     *
     * @return 样式内容
     */
    @ResponseBody
    @GetMapping(value = "/{styleName}", produces = "application/json")
    public ResponseEntity<String> loadStyle(@PathVariable("styleName") String styleName) {
        if (StringUtils.hasLength(appConfig.getDataPath())) {
            StringBuilder sb = new StringBuilder(appConfig.getDataPath());
            sb.append(File.separator).append("styles").append(File.separator).append(styleName);
            try {
                String styleJson = FileCopyUtils.copyToString(new FileReader(sb.toString()));
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                return ResponseEntity.ok().headers(headers).contentLength(styleJson.getBytes().length).body(styleJson);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}