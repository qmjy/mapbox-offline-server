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

package io.github.qmjy.mapbox.config;

import io.github.qmjy.mapbox.util.MapDbServerUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileFilter;

@Component
@Order(value = 1)
public class DataSourceApplicationRunner implements ApplicationRunner {

    @Autowired
    private AppConfig appConfig;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (StringUtils.hasLength(appConfig.getDataPath())) {
            File dataFolder = new File(appConfig.getDataPath());
            if (dataFolder.isDirectory() && dataFolder.exists()) {
                File tilesetsFolder = new File(dataFolder, "tilesets");
                File[] files = tilesetsFolder.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return pathname.getName().endsWith(".mbtiles");
                    }
                });

                if (files != null) {
                    for (File dbFile : files) {
                        MapDbServerUtils.initJdbcTemplate(appConfig.getDriverClassName(), dbFile);
                    }
                }
            }
        }
    }
}