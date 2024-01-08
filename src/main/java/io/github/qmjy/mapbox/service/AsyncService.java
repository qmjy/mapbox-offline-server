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

package io.github.qmjy.mapbox.service;

import io.github.qmjy.mapbox.MapServerDataCenter;
import io.github.qmjy.mapbox.config.AppConfig;
import io.github.qmjy.mapbox.model.MbtilesOfMergeProgress;
import io.github.qmjy.mapbox.model.MbtileInfoToCopy;
import io.github.qmjy.mapbox.util.JdbcUtils;
import org.hsqldb.jdbc.JDBCBlob;
import org.hsqldb.types.BlobData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AsyncService {
    private final Logger logger = LoggerFactory.getLogger(AsyncService.class);
    @Autowired
    private AppConfig appConfig;

    /**
     * taskId:完成百分比
     */
    private Map<String, Integer> taskProgress = new HashMap<>();

    /**
     * 每10秒检查一次，数据有更新则刷新
     */
    @Scheduled(fixedRate = 1000)
    public void processFixedRate() {
        //TODO nothing to do yet
    }

    /**
     * 加载数据文件
     */
    @Async("asyncServiceExecutor")
    public void asyncTask() {
        if (StringUtils.hasLength(appConfig.getDataPath())) {
            File dataFolder = new File(appConfig.getDataPath());
            if (dataFolder.isDirectory() && dataFolder.exists()) {
                wrapTilesFile(dataFolder);
                wrapFontsFile(dataFolder);
                wrapOSMBFile(dataFolder);
            }
        }
    }

    /**
     * 提交文件合并任务
     *
     * @param sourceNamePaths 待合并的文件列表
     * @param targetFilePath  目标文件名字
     */
    @Async("asyncServiceExecutor")
    public void submit(String taskId, List<String> sourceNamePaths, String targetFilePath) {
        taskProgress.put(taskId, 0);

        //filePath:mbtilesDetails
        Map<String, MbtileInfoToCopy> needCopies = new HashMap<>();
        AtomicReference<String> largestFile = new AtomicReference<>("");
        AtomicLong totalCount = new AtomicLong();
        long completeCount = 0;
        sourceNamePaths.forEach(item -> {
            if (largestFile.get().isBlank() || new File(item).length() > new File(largestFile.get()).length()) {
                largestFile.set(item);
            }
            MbtileInfoToCopy value = wrapModel(item);
            totalCount.addAndGet(value.getCount());
            needCopies.put(item, value);
        });


        //直接拷贝最大的文件，提升合并速度
        File targetTmpFile = new File(targetFilePath + ".tmp");
        try {
            FileCopyUtils.copy(new File(largestFile.get()), targetTmpFile);
            completeCount = needCopies.get(largestFile.get()).getCount();
            taskProgress.put(taskId, (int) (completeCount * 100 / totalCount.get()));
            needCopies.remove(largestFile.get());
        } catch (IOException e) {
            logger.info("Copy the largest file failed: {}", largestFile.get());
            taskProgress.put(taskId, -1);
            return;
        }

        Iterator<Map.Entry<String, MbtileInfoToCopy>> iterator = needCopies.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, MbtileInfoToCopy> next = iterator.next();
            mergeTo(next.getValue(), targetTmpFile);
            completeCount += next.getValue().getCount();
            taskProgress.put(taskId, (int) (completeCount * 100 / totalCount.get()));
            iterator.remove();

            logger.info("Merged file: {}", next.getValue().getFilePath());
        }

        boolean b = targetTmpFile.renameTo(new File(targetFilePath));
        System.out.println("重命名文件结果：" + b);
        taskProgress.put(taskId, 100);
    }


    public void mergeTo(MbtileInfoToCopy mbtile, File targetTmpFile) {
        JdbcTemplate jdbcTemplate = JdbcUtils.getInstance().getJdbcTemplate(appConfig.getDriverClassName(), targetTmpFile.getAbsolutePath());
        int pageSize = 5000;
        long totalPage = mbtile.getCount() % pageSize == 0 ? mbtile.getCount() / pageSize : mbtile.getCount() / pageSize + 1;
        for (long currentPage = 0; currentPage < totalPage; currentPage++) {
            List<Map<String, Object>> dataList = mbtile.getJdbcTemplate().queryForList("SELECT * FROM tiles LIMIT " + pageSize + " OFFSET " + currentPage * pageSize);
            jdbcTemplate.batchUpdate("INSERT INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)",
                    dataList,
                    pageSize,
                    (PreparedStatement ps, Map<String, Object> rowDataMap) -> {
                        ps.setInt(1, (int) rowDataMap.get("zoom_level"));
                        ps.setInt(2, (int) rowDataMap.get("tile_column"));
                        ps.setInt(3, (int) rowDataMap.get("tile_row"));
                        ps.setBytes(4, (byte[]) rowDataMap.get("tile_data"));
                    });
        }
        JdbcUtils.getInstance().releaseJdbcTemplate(jdbcTemplate);
    }

    private MbtileInfoToCopy wrapModel(String item) {
        JdbcTemplate jdbcTemplate = JdbcUtils.getInstance().getJdbcTemplate(appConfig.getDriverClassName(), item);
        return new MbtileInfoToCopy(item, jdbcTemplate);
    }


    /**
     * data format from: <a href="https://osm-boundaries.com/">OSM-Boundaries</a>
     *
     * @param dataFolder 行政区划边界数据
     */
    private void wrapOSMBFile(File dataFolder) {
        File boundariesFolder = new File(dataFolder, "OSMB");
        File[] files = boundariesFolder.listFiles();
        if (files != null) {
            for (File boundary : files) {
                if (!boundary.isDirectory() && boundary.getName().endsWith(AppConfig.FILE_EXTENSION_NAME_GEOJSON)) {
                    logger.info("Load boundary file: {}", boundary.getName());
                    MapServerDataCenter.initBoundaryFile(boundary);
                }
            }
        }
    }

    private void wrapFontsFile(File dataFolder) {
        File tilesetsFolder = new File(dataFolder, "fonts");
        File[] files = tilesetsFolder.listFiles();
        if (files != null) {
            for (File fontFolder : files) {
                if (fontFolder.isDirectory()) {
                    MapServerDataCenter.initFontsFile(fontFolder);
                }
            }
        }
    }

    private void wrapTilesFile(File dataFolder) {
        File tilesetsFolder = new File(dataFolder, "tilesets");
        File[] files = tilesetsFolder.listFiles(pathname -> pathname.getName().endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES));

        if (files != null) {
            for (File dbFile : files) {
                logger.info("Load tile file: {}", dbFile.getName());
                MapServerDataCenter.initJdbcTemplate(appConfig.getDriverClassName(), dbFile);
            }
        }
    }


    public String computeTaskId(List<String> sourceNamePaths) {
        Collections.sort(sourceNamePaths);
        StringBuilder sb = new StringBuilder();
        sourceNamePaths.forEach(sb::append);
        return DigestUtils.md5DigestAsHex(sb.toString().getBytes());
    }

    public Optional<MbtilesOfMergeProgress> getTask(String taskId) {
        if (taskProgress.containsKey(taskId)) {
            Integer i = taskProgress.get(taskId);
            return Optional.of(new MbtilesOfMergeProgress(taskId, i));
        } else {
            return Optional.empty();
        }
    }
}
