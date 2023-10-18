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

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileFilter;

@ConfigurationProperties
public class MapboxBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor {

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;
    @Value("${data}")
    private String data;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!StringUtils.hasLength(data)) {
            File dataFolder = new File(data);
            if (dataFolder.isDirectory() && dataFolder.exists()) {
                File tilesetsFolder = new File(dataFolder, "tilesets");
                File[] files = tilesetsFolder.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return pathname.getName().endsWith("admin.mbtiles");
                    }
                });

                if (files != null) {
                    for (File dbFile : files) {
                        BeanDefinitionBuilder datasourceBean = BeanDefinitionBuilder.rootBeanDefinition(HikariDataSource.class);
                        datasourceBean.addPropertyValue("jdbcUrl", "jdbc:sqlite:" + dbFile.getAbsolutePath());
                        datasourceBean.addPropertyValue("driverClassName", driverClassName);
                        registry.registerBeanDefinition(dbFile.getName(), datasourceBean.getBeanDefinition());
                    }
                }
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }
}
