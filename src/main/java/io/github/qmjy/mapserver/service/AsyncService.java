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

package io.github.qmjy.mapserver.service;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;

import eu.smartdatalake.athenarc.osmwrangle.tools.OsmPbfParser;
import eu.smartdatalake.athenarc.osmwrangle.utils.Configuration;
import io.github.qmjy.mapserver.MapServerDataCenter;
import io.github.qmjy.mapserver.config.AppConfig;
import io.github.qmjy.mapserver.model.*;
import io.github.qmjy.mapserver.util.IOUtils;
import io.github.qmjy.mapserver.util.JdbcUtils;
import io.github.qmjy.mapserver.util.VectorTileUtils;
import io.github.sebasbaumh.mapbox.vectortile.adapt.jts.model.JtsLayer;
import io.github.sebasbaumh.mapbox.vectortile.adapt.jts.model.JtsMvt;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.jetbrains.annotations.NotNull;
import org.locationtech.jts.geom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.FileCopyUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.util.*;

@Service
public class AsyncService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncService.class);
    private final AppConfig appConfig;

    private final MapServerDataCenter mapServerDataCenter;

    /**
     * taskId:完成百分比
     */
    private final Map<String, Integer> mergeTaskProgress = new HashMap<>();

    public AsyncService(AppConfig appConfig, MapServerDataCenter mapServerDataCenter) {
        this.appConfig = appConfig;
        this.mapServerDataCenter = mapServerDataCenter;
    }

    /**
     * 每10秒检查一次
     */
    @Scheduled(fixedRate = 1000)
    public void processFixedRate() {
        //TODO nothing to do yet
    }

    /**
     * 加载osm.pbf路网数据
     *
     * @param osmPbfFile osm.pbf数据
     */
    @Async("asyncServiceExecutor")
    public void loadOsmPbfRoute(File osmPbfFile) {
        GraphHopper hopper = new GraphHopper();

        hopper.setOSMFile(osmPbfFile.getAbsolutePath());
        // 读取完OSM数据之后会构建路线图，此处配置图的存储路径
        hopper.setGraphHopperLocation(getCacheLocation(osmPbfFile));

        hopper.setProfiles(new Profile("car").setVehicle("car").setTurnCosts(false), new Profile("bike").setVehicle("bike").setTurnCosts(false), new Profile("foot").setVehicle("foot").setTurnCosts(false));
        hopper.importOrLoad();
        MapServerDataCenter.getInstance().initHopper(osmPbfFile.getName(), hopper);
    }

    /**
     * 提取osm.pbf的poi数据。<br/>
     * thanks for the project：<a href="https://github.com/SLIPO-EU/OSMWrangle">OSMWrangle</a>
     *
     * @param osmPbfFile 待提取数据的osm.pbf数据
     */
    @Async("asyncServiceExecutor")
    public void loadOsmPbfPoi(File osmPbfFile) {
        Configuration currentConfig = new Configuration(osmPbfFile, appConfig);

        System.setProperty("org.geotools.referencing.forceXY", "true");

        String outFile = currentConfig.outputDir + IOUtils.getBaseName(currentConfig.inputFiles) + ".nt";

        int sourceSRID = 0;
        int targetSRID = 0;
        OsmPbfParser conv = new OsmPbfParser(currentConfig, osmPbfFile.getAbsolutePath(), outFile, sourceSRID, targetSRID);
        conv.apply();
        conv.close();
    }

    @NotNull
    private static String getCacheLocation(File osmPbfFile) {
        String location = osmPbfFile.getParent() + File.separator + "routing-graph-cache-" + osmPbfFile.getName();
        File file = new File(location);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                LOGGER.error("make dirs failed: " + file.getAbsolutePath());
            }
        }
        return location;
    }

    /**
     * 初始化瓦片数据库的POI信息
     */
    @Async("asyncServiceExecutor")
    public void asyncMbtilesToPOI(File tilesetFile) {
        Map<String, Object> tileMetaData = mapServerDataCenter.getTileMetaData(tilesetFile.getName());
        if ("pbf".equals(tileMetaData.get("format")) || "mvt".equals(tileMetaData.get("format"))) {
            String idxFilePath = tilesetFile.getAbsolutePath() + ".idx";
            if (!new File(idxFilePath).exists()) {
                JdbcTemplate idxJdbcTemp = JdbcUtils.getInstance().getJdbcTemplate(appConfig.getDriverClassName(), idxFilePath);
                idxJdbcTemp.execute("CREATE TABLE poi(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, tile_row INTEGER NOT NULL, tile_column INTEGER NOT NULL, zoom_level INTEGER NOT NULL, geometry TEXT NOT NULL, geometry_type INTEGER NOT NULL);");

                TilesFileModel tilesFileModel = mapServerDataCenter.getTilesFileModel(tilesetFile.getName());
                tilesFileModel.countSize();
                extractPoi2Idx(tilesFileModel, idxJdbcTemp);
                JdbcUtils.getInstance().releaseJdbcTemplate(idxJdbcTemp);
            }
        }
    }

    private void extractPoi2Idx(TilesFileModel tilesFileModel, JdbcTemplate idxJdbcTemp) {
        //只从最高层级解析POI数据
        String maxZoom = (String) tilesFileModel.getMetaDataMap().get("maxzoom");
        JdbcTemplate jdbcTemplate = tilesFileModel.getJdbcTemplate();

        int pageSize = 5000;
        List<PoiCache> cache = new ArrayList<>();

        long totalPage = tilesFileModel.getTilesCount() % pageSize == 0 ? tilesFileModel.getTilesCount() / pageSize : tilesFileModel.getTilesCount() / pageSize + 1;
        for (long currentPage = 0; currentPage < totalPage; currentPage++) {
            List<Map<String, Object>> dataList = jdbcTemplate.queryForList("SELECT * FROM tiles WHERE zoom_level = '" + maxZoom + "' LIMIT " + pageSize + " OFFSET " + currentPage * pageSize);
            for (Map<String, Object> rowDataMap : dataList) {
                byte[] data = (byte[]) rowDataMap.get("tile_data");
                List<PoiCache> poiList = extractPoi((int) rowDataMap.get("tile_row"), (int) rowDataMap.get("tile_column"), (int) rowDataMap.get("zoom_level"), tilesFileModel.isCompressed() ? IOUtils.decompress(data) : data);
                cache.addAll(poiList);
                if (cache.size() > pageSize) {
                    batchUpdate(idxJdbcTemp, cache);
                    cache.clear();
                }
            }
        }
        batchUpdate(idxJdbcTemp, cache);
    }

    private static void batchUpdate(JdbcTemplate idxJdbcTemp, List<PoiCache> poiList) {
        idxJdbcTemp.batchUpdate("INSERT INTO poi(name, geometry, geometry_type, zoom_level, tile_row, tile_column) VALUES (?, ?, ?, ?, ?, ?)", poiList, poiList.size(), (PreparedStatement ps, PoiCache poi) -> {
            ps.setString(1, poi.getName());
            ps.setString(2, poi.getGeometry());
            ps.setInt(3, poi.getGeometryType());
            ps.setInt(4, poi.getZoomLevel());
            ps.setInt(5, poi.getTileRow());
            ps.setInt(6, poi.getTileColumn());
        });
    }

    private List<PoiCache> extractPoi(int tileRow, int tileColumn, int zoomLevel, byte[] data) {
        Optional<JtsMvt> jtsMvt = VectorTileUtils.decodeJtsMvt(new ByteArrayInputStream(data));
        List<PoiCache> objects = new ArrayList<>();
        if (jtsMvt.isPresent()) {
            Collection<JtsLayer> layers = jtsMvt.get().getLayers();
            layers.forEach(geometries -> {
                for (Geometry geometry : geometries.getGeometries()) {
                    LinkedHashMap<String, String> userData = (LinkedHashMap<String, String>) geometry.getUserData();
                    String name = userData.get("name");
                    if (name != null) {
                        switch (geometry) {
                            case Point point -> {
                                objects.add(new PoiCache(name, tileRow, tileColumn, zoomLevel, 0, point));
                            }
                            case MultiPoint multiPoint -> {
//                            objects.add(new PoiCache(name, tileRow, tileColumn, zoomLevel, 1));
                            }
                            case LinearRing linearRing -> {
                                //                    objects.add(new PoiCache(name, tileRow, tileColumn, zoomLevel, 3));
                            }
                            case LineString lineString -> {
                                //                          objects.add(new PoiCache(name, tileRow, tileColumn, zoomLevel, 2));
                            }
                            case MultiLineString multiLineString -> {
                                //           objects.add(new PoiCache(name, tileRow, tileColumn, zoomLevel, 4));
                            }
                            case Polygon polygon -> {
                                //        objects.add(new PoiCache(name, tileRow, tileColumn, zoomLevel, 5));
                            }
                            case MultiPolygon multiPolygon -> {
                                //      objects.add(new PoiCache(name, tileRow, tileColumn, zoomLevel, 6));
                            }
                            case GeometryCollection geometryCollection -> {
                                //     objects.add(new PoiCache(name, tileRow, tileColumn, zoomLevel, 7));
                            }
                            default -> throw new IllegalStateException("Unexpected value: " + geometry);
                        }
                    }
                }
            });
        }
        return objects;
    }


    /**
     * 提交文件合并任务。合并任务失败，则process is -1。
     *
     * @param sourceNamePaths 待合并的文件列表
     * @param targetFilePath  目标文件名字
     */
    @Async("asyncServiceExecutor")
    public void submit(String taskId, List<String> sourceNamePaths, String targetFilePath) {
        mergeTaskProgress.put(taskId, 0);
        Optional<MbtileMergeWrapper> wrapperOpt = arrange(sourceNamePaths);

        if (wrapperOpt.isPresent()) {
            MbtileMergeWrapper wrapper = wrapperOpt.get();
            Map<String, MbtileMergeFile> needMerges = wrapper.getNeedMerges();
            long totalCount = wrapper.getTotalCount();
            String largestFilePath = wrapper.getLargestFilePath();

            long completeCount;
            //直接拷贝最大的文件，提升合并速度
            File targetTmpFile = new File(targetFilePath + ".tmp");
            try {
                FileCopyUtils.copy(new File(largestFilePath), targetTmpFile);
                completeCount = needMerges.get(largestFilePath).getCount();
                mergeTaskProgress.put(taskId, (int) (completeCount * 100 / totalCount));
                needMerges.remove(largestFilePath);
            } catch (IOException e) {
                LOGGER.info("Copy the largest file failed: {}", largestFilePath);
                mergeTaskProgress.put(taskId, -1);
                return;
            }

            JdbcTemplate jdbcTemplate = JdbcUtils.getInstance().getJdbcTemplate(appConfig.getDriverClassName(), targetTmpFile.getAbsolutePath());
            Iterator<Map.Entry<String, MbtileMergeFile>> iterator = needMerges.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, MbtileMergeFile> next = iterator.next();
                mergeTo(next.getValue(), jdbcTemplate);
                completeCount += next.getValue().getCount();
                mergeTaskProgress.put(taskId, (int) (completeCount * 100 / totalCount));
                iterator.remove();
                LOGGER.info("Merged file: {}", next.getValue().getFilePath());
            }

            updateMetadata(wrapper, jdbcTemplate);
            JdbcUtils.getInstance().releaseJdbcTemplate(jdbcTemplate);

            if (targetTmpFile.renameTo(new File(targetFilePath))) {
                mergeTaskProgress.put(taskId, 100);
            } else {
                LOGGER.error("Rename file failed: {}", targetFilePath);
            }
        } else {
            mergeTaskProgress.put(taskId, -1);
        }
    }

    private void updateMetadata(MbtileMergeWrapper wrapper, JdbcTemplate jdbcTemplate) {
        String bounds = wrapper.getMinLon() + "," + wrapper.getMinLat() + "," + wrapper.getMaxLon() + "," + wrapper.getMaxLat();
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
                    LOGGER.error("These Mbtiles files have different formats!");
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


    public void mergeTo(MbtileMergeFile mbtile, JdbcTemplate jdbcTemplate) {
        int pageSize = 5000;
        long totalPage = mbtile.getCount() % pageSize == 0 ? mbtile.getCount() / pageSize : mbtile.getCount() / pageSize + 1;
        for (long currentPage = 0; currentPage < totalPage; currentPage++) {
            List<Map<String, Object>> dataList = mbtile.getJdbcTemplate().queryForList("SELECT * FROM tiles LIMIT " + pageSize + " OFFSET " + currentPage * pageSize);
            jdbcTemplate.batchUpdate("INSERT INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)", dataList, pageSize, (PreparedStatement ps, Map<String, Object> rowDataMap) -> {
                ps.setInt(1, (int) rowDataMap.get("zoom_level"));
                ps.setInt(2, (int) rowDataMap.get("tile_column"));
                ps.setInt(3, (int) rowDataMap.get("tile_row"));
                ps.setBytes(4, (byte[]) rowDataMap.get("tile_data"));
            });
        }
    }

    private MbtileMergeFile wrapModel(String item) {
        JdbcTemplate jdbcTemplate = JdbcUtils.getInstance().getJdbcTemplate(appConfig.getDriverClassName(), item);
        return new MbtileMergeFile(item, jdbcTemplate);
    }


    public String computeTaskId(List<String> sourceNamePaths) {
        Collections.sort(sourceNamePaths);
        StringBuilder sb = new StringBuilder();
        sourceNamePaths.forEach(sb::append);
        return DigestUtils.md5DigestAsHex(sb.toString().getBytes());
    }

    public Optional<MbtilesOfMergeProgress> getTask(String taskId) {
        if (mergeTaskProgress.containsKey(taskId)) {
            Integer i = mergeTaskProgress.get(taskId);
            return Optional.of(new MbtilesOfMergeProgress(taskId, i));
        } else {
            return Optional.empty();
        }
    }

    public void indexPoi(File csvFile) {
        String absolutePath = csvFile.getAbsolutePath();
        String poiFile = absolutePath.substring(0, absolutePath.lastIndexOf(".")) + ".poi";
        if (new File(poiFile).exists()) {
            LOGGER.info("The file of poi already exists: {}", poiFile);
            return;
        }
        JdbcTemplate jdbcTemplate = JdbcUtils.getInstance().getJdbcTemplate(appConfig.getDriverClassName(), poiFile);

        List<String[]> data = new ArrayList<>();
        int pageSize = 50000;
        try (LineIterator lineIterator = FileUtils.lineIterator(csvFile)) {
            int i = 0;
            while (lineIterator.hasNext()) {
                String[] split = lineIterator.nextLine().split("\\|");
                if (i == 0) {
                    createTable(jdbcTemplate, split);
                } else {
                    data.add(split);
                }
                i++;
                if (i % pageSize == 0) {
                    insertTable(jdbcTemplate, data);
                    data.clear();
                }
            }
            insertTable(jdbcTemplate, data);
            JdbcUtils.getInstance().releaseJdbcTemplate(jdbcTemplate);
            LOGGER.info("Index poi of count: {}", i);
        } catch (IOException e) {
            LOGGER.error("Index poi file failed: {}", csvFile.getAbsolutePath());
        }
    }

    private void insertTable(JdbcTemplate jdbcTemplate, List<String[]> data) {
        jdbcTemplate.batchUpdate("INSERT INTO poi (id, name, category, subcategory, lon, lat, srid, wkt, opening_hours, alternative_name, postcode, phone, street, email, last_update, name_en, image, wikipedia, city, country, operator, description, housenumber, international_name, fax, website, other_tags) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?, ?, ?, ?, ?, ?, ?)", data, data.size(), (PreparedStatement ps, String[] row) -> {
            ps.setString(1, row[0]);
            ps.setString(2, row[1]);
            ps.setString(3, row[2]);
            ps.setString(4, row[3]);
            ps.setString(5, row[4]);
            ps.setFloat(6, Float.parseFloat(row[5]));
            ps.setFloat(7, Float.parseFloat(row[6]));
            ps.setString(8, row[7]);
            ps.setString(9, row[8]);
            ps.setString(10, row[9]);
            ps.setString(11, row[10]);
            ps.setString(12, row[11]);
            ps.setString(13, row[12]);
            ps.setString(14, row[13]);
            ps.setString(15, row[14]);
            ps.setString(16, row[15]);
            ps.setString(17, row[16]);
            ps.setString(18, row[17]);
            ps.setString(19, row[18]);
            ps.setString(20, row[19]);
            ps.setString(21, row[20]);
            ps.setString(22, row[21]);
            ps.setString(23, row[22]);
            ps.setString(24, row[23]);
            ps.setString(25, row[24]);
            ps.setString(26, row[25]);
            ps.setString(27, row[26]);
        });
    }


    private void createTable(JdbcTemplate jdbcTemplate, String[] split) {
        StringBuilder sb = new StringBuilder("CREATE TABLE poi(");
        sb.append(split[0].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[1].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[2].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[3].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[4].toLowerCase(Locale.getDefault())).append(" INTEGER NOT NULL,");
        sb.append(split[5].toLowerCase(Locale.getDefault())).append(" INTEGER NOT NULL,");
        sb.append(split[6].toLowerCase(Locale.getDefault())).append(" INTEGER NOT NULL,");
        sb.append(split[7].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[8].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[9].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[10].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[11].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[12].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[13].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[14].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[15].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[16].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[17].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[18].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[19].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[20].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[21].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[22].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[23].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[24].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[25].toLowerCase(Locale.getDefault())).append(" TEXT,");
        sb.append(split[26].toLowerCase(Locale.getDefault())).append(" TEXT)");
        jdbcTemplate.execute(sb.toString());
    }


}
