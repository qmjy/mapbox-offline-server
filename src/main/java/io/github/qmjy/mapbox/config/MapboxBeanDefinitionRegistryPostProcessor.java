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
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileFilter;

/**
 * 按照用户数据库动态设置数据源
 */
@Component
public class MapboxBeanDefinitionRegistryPostProcessor implements EnvironmentAware, BeanDefinitionRegistryPostProcessor {
    private AppConfig appConfig;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
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

                if (files != null) {
                    for (File dbFile : files) {
                        BeanDefinitionBuilder datasourceBean = BeanDefinitionBuilder.rootBeanDefinition(HikariDataSource.class);
                        datasourceBean.addPropertyValue("jdbcUrl", "jdbc:sqlite:" + dbFile.getAbsolutePath());
                        datasourceBean.addPropertyValue("driverClassName", appConfig.getDriverClassName());
                        registry.registerBeanDefinition(dbFile.getName(), datasourceBean.getBeanDefinition());
                    }
                }
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void setEnvironment(Environment environment) {
        AppConfig appConfig = new AppConfig();
        appConfig.setDataPath(environment.getProperty("data-path"));
        appConfig.setDriverClassName(environment.getProperty("spring.datasource.driver-class-name"));
        this.appConfig = appConfig;
    }
}
