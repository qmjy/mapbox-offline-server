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

package io.github.qmjy.mapserver.model;

import lombok.Getter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class MbtileMergeFile {
    private final JdbcTemplate jdbcTemplate;
    private final String filePath;
    private final Map<String, String> metaMap = new HashMap<>();
    private long count;

    public MbtileMergeFile(String item, JdbcTemplate jdbcTemplate) {
        this.filePath = item;
        this.jdbcTemplate = jdbcTemplate;
        init();
    }

    private void init() {
        List<Map<String, Object>> mapList = jdbcTemplate.queryForList("SELECT * FROM metadata");
        mapList.forEach(item -> {
            metaMap.put(item.get("name").toString(), item.get("value").toString());
        });

        List<Map<String, Object>> maps = jdbcTemplate.queryForList("SELECT COUNT(*) AS count FROM tiles");
        this.count = Long.parseLong(maps.getFirst().get("count").toString());
    }

}
