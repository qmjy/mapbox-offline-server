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
import io.github.qmjy.mapbox.model.MbtileMergeFile;
import io.github.qmjy.mapbox.model.MbtileMergeWrapper;
import io.github.qmjy.mapbox.model.MbtilesOfMergeProgress;
import io.github.qmjy.mapbox.util.JdbcUtils;
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

@Service
public class AsyncService {
    private final Logger logger = LoggerFactory.getLogger(AsyncService.class);
    @Autowired
    private AppConfig appConfig;

    /**
     * taskId:完成百分比
     */
    private final Map<String, Integer> taskProgress = new HashMap<>();

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
                wrapMapFile(dataFolder);
                wrapFontsFile(dataFolder);
                wrapOSMBFile(dataFolder);
            }
        }
    }


    /**
     * 提交文件合并任务。合并任务失败，则process is -1。
     *
     * @param sourceNamePaths 待合并的文件列表
     * @param targetFilePath  目标文件名字
     */
    @Async("asyncServiceExecutor")
    public void submit(String taskId, List<String> sourceNamePaths, String targetFilePath) {
        taskProgress.put(taskId, 0);
        Optional<MbtileMergeWrapper> wrapperOpt = arrange(sourceNamePaths);

        if (wrapperOpt.isPresent()) {
            MbtileMergeWrapper wrapper = wrapperOpt.get();
            Map<String, MbtileMergeFile> needMerges = wrapper.getNeedMerges();
            long totalCount = wrapper.getTotalCount();
            String largestFilePath = wrapper.getLargestFilePath();

            long completeCount = 0;
            //直接拷贝最大的文件，提升合并速度
            File targetTmpFile = new File(targetFilePath + ".tmp");
            try {
                FileCopyUtils.copy(new File(largestFilePath), targetTmpFile);
                completeCount = needMerges.get(largestFilePath).getCount();
                taskProgress.put(taskId, (int) (completeCount * 100 / totalCount));
                needMerges.remove(largestFilePath);
            } catch (IOException e) {
                logger.info("Copy the largest file failed: {}", largestFilePath);
                taskProgress.put(taskId, -1);
                return;
            }

            Iterator<Map.Entry<String, MbtileMergeFile>> iterator = needMerges.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, MbtileMergeFile> next = iterator.next();
                mergeTo(next.getValue(), targetTmpFile);
                completeCount += next.getValue().getCount();
                taskProgress.put(taskId, (int) (completeCount * 100 / totalCount));
                iterator.remove();

                logger.info("Merged file: {}", next.getValue().getFilePath());
            }

            updateMetadata(wrapper, targetTmpFile);

            boolean b = targetTmpFile.renameTo(new File(targetFilePath));
            System.out.println("重命名文件结果：" + b);
            taskProgress.put(taskId, 100);
        } else {
            taskProgress.put(taskId, -1);
        }
    }

    private void updateMetadata(MbtileMergeWrapper wrapper, File targetTmpFile) {
        String bounds = wrapper.getMinLon() + "," + wrapper.getMinLat() + "," + wrapper.getMaxLon() + "," + wrapper.getMaxLat();
        JdbcTemplate jdbcTemplate = JdbcUtils.getInstance().getJdbcTemplate(appConfig.getDriverClassName(), targetTmpFile.getAbsolutePath());
        jdbcTemplate.update("UPDATE metadata SET value = " + wrapper.getMinZoom() + " WHERE name = 'minzoom'");
        jdbcTemplate.update("UPDATE metadata SET value = " + wrapper.getMaxZoom() + " WHERE name = 'maxzoom'");
        jdbcTemplate.update("UPDATE metadata SET value = '" + bounds + "' WHERE name = 'bounds'");
    }

    private Optional<MbtileMergeWrapper> arrange(List<String> sourceNamePaths) {
        MbtileMergeWrapper mbtileMergeWrapper = new MbtileMergeWrapper();

        for (String item : sourceNamePaths) {
            if (mbtileMergeWrapper.getLargestFilePath() == null || mbtileMergeWrapper.getLargestFilePath().isBlank() || new File(item).length() > new File(mbtileMergeWrapper.getLargestFilePath()).length()) {
                mbtileMergeWrapper.setLargestFilePath(item);
            }

            MbtileMergeFile value = wrapModel(item);
            Map<String, String> metadata = value.getMetaMap();

            String format = metadata.get("format");
            if (mbtileMergeWrapper.getFormat().isBlank()) {
                mbtileMergeWrapper.setFormat(format);
            } else {
                //比较mbtiles文件格式是否一致
                if (!mbtileMergeWrapper.getFormat().equals(format)) {
                    logger.error("These Mbtiles files have different formats!");
                    return Optional.empty();
                }
            }

            int minZoom = Integer.parseInt(metadata.get("minzoom"));
            if (mbtileMergeWrapper.getMinZoom() > minZoom) {
                mbtileMergeWrapper.setMinZoom(minZoom);
            }

            int maxZoom = Integer.parseInt(metadata.get("maxzoom"));
            if (mbtileMergeWrapper.getMaxZoom() < maxZoom) {
                mbtileMergeWrapper.setMaxZoom(maxZoom);
            }

            //Such as: 120.85098267,30.68516394,122.03475952,31.87872381
            String[] split = metadata.get("bounds").split(",");

            if (mbtileMergeWrapper.getMinLat() > Double.parseDouble(split[1])) {
                mbtileMergeWrapper.setMinLat(Double.parseDouble(split[1]));
            }
            if (mbtileMergeWrapper.getMaxLat() < Double.parseDouble(split[3])) {
                mbtileMergeWrapper.setMaxLat(Double.parseDouble(split[3]));
            }
            if (mbtileMergeWrapper.getMinLon() > Double.parseDouble(split[0])) {
                mbtileMergeWrapper.setMinLon(Double.parseDouble(split[0]));
            }
            if (mbtileMergeWrapper.getMaxLon() < Double.parseDouble(split[2])) {
                mbtileMergeWrapper.setMaxLon(Double.parseDouble(split[2]));
            }

            mbtileMergeWrapper.addToTotal(value.getCount());
            mbtileMergeWrapper.getNeedMerges().put(item, value);
        }
        return Optional.of(mbtileMergeWrapper);
    }


    public void mergeTo(MbtileMergeFile mbtile, File targetTmpFile) {
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

    private MbtileMergeFile wrapModel(String item) {
        JdbcTemplate jdbcTemplate = JdbcUtils.getInstance().getJdbcTemplate(appConfig.getDriverClassName(), item);
        return new MbtileMergeFile(item, jdbcTemplate);
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


    private void wrapMapFile(File dataFolder) {
        File tilesetsFolder = new File(dataFolder, "tilesets");
        searchMbtiles(tilesetsFolder);
        searchTpk(tilesetsFolder);
        searchShapefile(tilesetsFolder);
    }

    private void searchShapefile(File tilesetsFolder) {
        File[] files = tilesetsFolder.listFiles(pathname -> pathname.getName().endsWith(AppConfig.FILE_EXTENSION_NAME_SHP));
        if (files != null) {
            for (File shapefile : files) {
                logger.info("Load shapefile: {}", shapefile.getName());
                MapServerDataCenter.initShapefile(shapefile);
            }
        }
    }

    private void searchTpk(File tilesetsFolder) {
        File[] files = tilesetsFolder.listFiles(pathname -> pathname.getName().endsWith(AppConfig.FILE_EXTENSION_NAME_TPK));
        if (files != null) {
            for (File tpk : files) {
                logger.info("Load tpk tile file: {}", tpk.getName());
                MapServerDataCenter.initTpk(tpk);
            }
        }
    }

    private void searchMbtiles(File tilesetsFolder) {
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
