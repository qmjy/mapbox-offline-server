/*
 * Copyright (c) 2024 QMJY.
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

package io.github.qmjy.mapbox.util;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

public class JdbcUtils {

    private static final JdbcUtils INSTANCE = new JdbcUtils();

    private JdbcUtils() {
    }

    public static JdbcUtils getInstance() {
        return INSTANCE;
    }

    public JdbcTemplate getJdbcTemplate(String className, String filePath) {
        DataSourceBuilder<?> ds = DataSourceBuilder.create();
        ds.driverClassName(className);
        ds.url("jdbc:sqlite:" + filePath);
        return new JdbcTemplate(ds.build());
    }

    public void releaseJdbcTemplate(JdbcTemplate jdbcTemplate) {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource instanceof HikariDataSource ds) {
            ds.close();
        }
    }
}
