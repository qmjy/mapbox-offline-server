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

package io.github.qmjy.mapserver;

import com.graphhopper.GraphHopper;
import com.zaxxer.hikari.HikariDataSource;
import io.github.qmjy.mapserver.config.AppConfig;
import io.github.qmjy.mapserver.model.AdministrativeDivisionNode;
import io.github.qmjy.mapserver.model.FontsFileModel;
import io.github.qmjy.mapserver.model.TilesFileModel;
import lombok.Getter;
import lombok.Setter;
import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.FileDataStoreFinder;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.geojson.GeoJSONReader;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * 地图数据库服务工具
 */
@Component
public class MapServerDataCenter {

    private static final Logger logger = LoggerFactory.getLogger(MapServerDataCenter.class);

    private static final MapServerDataCenter INSTANCE = new MapServerDataCenter();

    /**
     * 瓦片数据库文件模型
     */
    @Getter
    private final Map<String, TilesFileModel> tilesMap = new HashMap<>();
    /**
     * 不会再被加载的文件列表
     */
    private final Set<String> blockedTiles = new HashSet<>();


    private final Map<String, FileDataStore> shpDataStores = new HashMap<>();

    /**
     * 字体文件模型
     */
    @Getter
    private final Map<String, FontsFileModel> fontsMap = new HashMap<>();

    /**
     * 行政区划数据。key:行政级别、value:区划对象列表
     */
    @Getter
    private final Map<Integer, List<SimpleFeature>> administrativeDivisionLevel = new HashMap<>();

    /**
     * 行政区划数据。key:区划ID、value:区划对象
     */
    @Getter
    private final Map<Integer, SimpleFeature> administrativeDivision = new HashMap<>();
    /**
     * 行政区划层级树
     */
    @Getter
    private AdministrativeDivisionNode simpleAdminDivision;

    @Getter
    private final Map<String, GraphHopper> hopperMap = new HashMap<>();

    @Getter
    @Setter
    private boolean mapnikReady = false;

    /**
     * 初始化完成后再启动扫描
     */
    @Getter
    @Setter
    private boolean initialized = false;


    private MapServerDataCenter() {
    }

    public static MapServerDataCenter getInstance() {
        return INSTANCE;
    }

    /**
     * 初始化数据源
     *
     * @param className 驱动名称
     * @param mbtiles   待链接的数据库文件
     */
    public void initJdbcTemplate(String className, File mbtiles) {
        if (!tilesMap.containsKey(mbtiles.getName())) {
            logger.info("Try to load tile of mbtiles: {}", mbtiles.getName());
            TilesFileModel dbFileModel = new TilesFileModel(mbtiles, className);
            if (dbFileModel.isValid()) {
                tilesMap.put(mbtiles.getName(), dbFileModel);
            }
        }
    }

    /**
     * 预加载tpk
     *
     * @param tpk tpk文件
     */
    public void indexTpk(File tpk) {
        if (!tilesMap.containsKey(tpk.getName()) && !blockedTiles.contains(tpk.getName())) {
            logger.info("Try to load tile of tpk: {}", tpk.getName());
            TilesFileModel dbFileModel = new TilesFileModel(tpk);
            if (dbFileModel.isValid()) {
                tilesMap.put(tpk.getName(), dbFileModel);
            } else {
                blockedTiles.add(tpk.getName());
            }
        }
    }

    public void initShapefile(File shapefile) {
        FileDataStore dataStore = null;
        try {
            dataStore = FileDataStoreFinder.getDataStore(shapefile);
        } catch (IOException e) {
            logger.error("FileDataStoreFinder.getDataStore() failed: {}", shapefile.getAbsolutePath());
        }
        shpDataStores.put(shapefile.getName(), dataStore);
    }

    public FileDataStore getShpDataStores(String shapefile) {
        return shpDataStores.get(shapefile);
    }

    public void initHopper(String fileName, GraphHopper hopper) {
        hopperMap.put(fileName, hopper);
    }

    public void initMapnik(boolean ready) {
        MapServerDataCenter.getInstance().setMapnikReady(ready);
    }


    /**
     * 初始化字体库文件
     *
     * @param fontFolder 字体文件目录
     */
    public void initFontsFile(File fontFolder) {
        fontsMap.put(fontFolder.getName(), new FontsFileModel(fontFolder));
    }

    /**
     * geojson格式的加载行政区划边界数据。
     *
     * @param boundary 行政区划边界
     */
    public void initBoundaryFile(File boundary) {
        try {
            GeoJSONReader reader = new GeoJSONReader(new FileInputStream(boundary));
            SimpleFeatureIterator features = reader.getFeatures().features();
            while (features.hasNext()) {
                SimpleFeature feature = features.next();

                administrativeDivision.put((int) feature.getAttribute("osm_id"), feature);

                int adminLevel = feature.getAttribute("admin_level") == null ? -1 : (int) feature.getAttribute("admin_level");
                if (administrativeDivisionLevel.containsKey(adminLevel)) {
                    List<SimpleFeature> simpleFeatures = administrativeDivisionLevel.get(adminLevel);
                    simpleFeatures.add(feature);
                } else {
                    ArrayList<SimpleFeature> value = new ArrayList<>();
                    value.add(feature);
                    administrativeDivisionLevel.put(adminLevel, value);
                }
            }
            features.close();
            packageModel();
        } catch (IOException e) {
            logger.error("Read OSM file failed：{}", boundary.getAbsolutePath());
        }
    }

    private void packageModel() {
        if (isOldVersion()) {
            administrativeDivision.values().forEach(feature -> {
                if (simpleAdminDivision == null) {
                    simpleAdminDivision = initRootNode(feature);
                } else {
                    Object parentsObj = feature.getAttribute("parents");
                    if (parentsObj != null) {
                        packageAdminTreeByParents(feature, parentsObj);
                    }
                }
            });
        } else {
            List<Integer> levelList = administrativeDivisionLevel.keySet().stream().sorted().toList();
            for (int i = 0; i < levelList.size(); i++) {
                Integer currentLevel = levelList.get(i);
                if (i == 0) {
                    //TODO 假设根节点只有一个
                    SimpleFeature first = administrativeDivisionLevel.get(currentLevel).getFirst();
                    simpleAdminDivision = new AdministrativeDivisionNode(first, -1);
                } else {
                    Integer upperLevel = levelList.get(i - 1);
                    List<SimpleFeature> simpleFeatures = administrativeDivisionLevel.get(upperLevel);
                    for (SimpleFeature node : simpleFeatures) {
                        addAdminLevelNode(administrativeDivisionLevel.get(upperLevel), node);
                    }
                }
            }
        }
    }

    private void addAdminLevelNode(List<SimpleFeature> upperLevelFeatures, SimpleFeature node) {
        for (SimpleFeature upperLevelFeature : upperLevelFeatures) {
            Object geometry = upperLevelFeature.getAttribute("geometry");
            if (geometry instanceof Polygon || geometry instanceof MultiPolygon) {
                Geometry g = (Geometry) geometry;
                if (g.covers((Geometry) node.getAttribute("geometry"))) {
                    add2Parents(simpleAdminDivision, upperLevelFeature, node);
                }
            }
        }
    }

    private void add2Parents(AdministrativeDivisionNode root, SimpleFeature upperLevelFeature, SimpleFeature node) {
        if (root.getId() == (int) upperLevelFeature.getAttribute("osm_id")) {
            root.getChildren().add(new AdministrativeDivisionNode(node, root.getId()));
        } else {
            if (root.getChildren().isEmpty()) {
                return;
            }
            for (AdministrativeDivisionNode child : root.getChildren()) {
                add2Parents(child, upperLevelFeature, node);
            }
        }
    }

    /**
     * 抽取一个判断数据是否为老版本，老版本数据才有parents属性
     *
     * @return 是否为老版本数据
     */
    private boolean isOldVersion() {
        Map.Entry<Integer, SimpleFeature> next = administrativeDivision.entrySet().iterator().next();
        return next.getValue().getAttribute("parents") != null;
    }

    private void packageAdminTreeByParents(SimpleFeature feature, Object parentsObj) {
        String[] parents = parentsObj.toString().split(",");

        AdministrativeDivisionNode tempNode = new AdministrativeDivisionNode(feature, Integer.parseInt(parents[0]));

        for (int i = 0; i < parents.length; i++) {
            int parentId = Integer.parseInt(parents[i]);
            Optional<AdministrativeDivisionNode> nodeOpt = findNode(simpleAdminDivision, parentId);
            if (nodeOpt.isPresent()) {
                AdministrativeDivisionNode child = nodeOpt.get();
                //如果父节点已经在早期全路径时构造过了，则不需要再追加此单节点。
                if (!contains(child, (int) feature.getAttribute("osm_id"))) {
                    child.getChildren().add(tempNode);
                }
                break;
            } else {
                AdministrativeDivisionNode tmp = new AdministrativeDivisionNode(administrativeDivision.get(parentId), Integer.parseInt(parents[i + 1]));
                tmp.getChildren().add(tempNode);
                tempNode = tmp;
            }
        }
    }

    private boolean contains(AdministrativeDivisionNode child, int parentId) {
        for (AdministrativeDivisionNode item : child.getChildren()) {
            if (item.getId() == parentId) {
                return true;
            }
        }
        return false;
    }


    private AdministrativeDivisionNode initRootNode(SimpleFeature feature) {
        Object parents = feature.getAttribute("parents");
        if (parents == null) {
            return new AdministrativeDivisionNode(feature, -1);
        } else {
            String[] split = parents.toString().split(",");
            List<AdministrativeDivisionNode> children = new ArrayList<>();
            AdministrativeDivisionNode tmp = null;
            for (int i = 0; i < split.length; i++) {
                int osmId = Integer.parseInt(split[i]);
                if (i + 1 > split.length - 1) {
                    tmp = new AdministrativeDivisionNode(administrativeDivision.get(osmId), -1);
                    tmp.setChildren(children);
                } else {
                    tmp = new AdministrativeDivisionNode(administrativeDivision.get(osmId), Integer.parseInt(split[i + 1]));
                    tmp.setChildren(children);
                    children = new ArrayList<>();
                    children.add(tmp);
                }
            }
            return tmp;
        }
    }

    private Optional<AdministrativeDivisionNode> findNode(AdministrativeDivisionNode tmp, int parentId) {
        if (tmp.getId() == parentId) {
            return Optional.of(tmp);
        }
        List<AdministrativeDivisionNode> children = tmp.getChildren();
        for (AdministrativeDivisionNode item : children) {
            if (item.getId() == parentId) {
                return Optional.of(item);
            } else {
                Optional<AdministrativeDivisionNode> childOpt = findNode(item, parentId);
                if (childOpt.isPresent()) {
                    return childOpt;
                }
            }
        }
        return Optional.empty();
    }


    /**
     * 通过文件名获取数据源
     *
     * @param fileName 数据库文件名称
     * @return 数据库数据源
     */
    public Optional<JdbcTemplate> getDataSource(String fileName) {
        if (StringUtils.hasLength(fileName)) {
            TilesFileModel model = tilesMap.get(fileName);
            return Optional.of(model.getJdbcTemplate());
        } else {
            return Optional.empty();
        }
    }

    public void releaseDataSource(String fileName) {
        if (fileName.endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES)) {
            if (StringUtils.hasLength(fileName) && tilesMap.containsKey(fileName)) {
                TilesFileModel remove = tilesMap.remove(fileName);
                JdbcTemplate jdbcTemplate = remove.getJdbcTemplate();
                //执行检查点操作，将所有WAL内容写入主数据库文件
                jdbcTemplate.execute("PRAGMA wal_checkpoint(FULL)");
                DataSource dataSource = jdbcTemplate.getDataSource();
                if (dataSource instanceof HikariDataSource hikariDataSource) {
                    hikariDataSource.close();
                }
            }
        }

        if (fileName.endsWith(AppConfig.FILE_EXTENSION_NAME_TPK)) {
            if (StringUtils.hasLength(fileName) && tilesMap.containsKey(fileName)) {
                TilesFileModel remove = tilesMap.remove(fileName);
                remove.setTpkFile(null);
                remove.setZoomLevelMap(null);
            }
        }

    }

    /**
     * 返回瓦片文件对象
     *
     * @param fileName 瓦片集文件名称
     * @return 瓦片集文件对象
     */
    public TilesFileModel getTilesFileModel(String fileName) {
        return tilesMap.get(fileName);
    }


    /**
     * 返回瓦片数据库文件的元数据
     *
     * @param fileName 瓦片数据库文件名
     * @return 瓦片元数据
     */
    public Map<String, Object> getTileMetaData(String fileName) {
        if (StringUtils.hasLength(fileName)) {
            TilesFileModel model = tilesMap.get(fileName);
            if (model != null) {
                return model.getMetaDataMap();
            }
        }
        return new HashMap<>();
    }


    /**
     * 获取字符文件目录
     *
     * @param fontName 字体文件名
     * @return 字体文件目录
     */
    public Optional<FontsFileModel> getFontFolder(String fontName) {
        if (StringUtils.hasLength(fontName)) {
            FontsFileModel fontsFileModel = fontsMap.get(fontName);
            if (fontsFileModel != null) {
                return Optional.of(fontsFileModel);
            }
        }
        return Optional.empty();
    }
}
