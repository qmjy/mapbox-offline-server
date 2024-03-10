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

package io.github.qmjy.mapbox;

import io.github.qmjy.mapbox.model.AdministrativeDivisionTmp;
import io.github.qmjy.mapbox.model.FontsFileModel;
import io.github.qmjy.mapbox.model.MetaData;
import io.github.qmjy.mapbox.model.TilesFileModel;
import lombok.Getter;
import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.FileDataStoreFinder;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.geojson.GeoJSONReader;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.tpk.TPKFile;
import org.geotools.tpk.TPKZoomLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * 地图数据库服务工具
 */
@Component
public class MapServerDataCenter {
    private static final Logger logger = LoggerFactory.getLogger(MapServerDataCenter.class);

    /**
     * 瓦片数据库文件模型
     */
    @Getter
    private static final Map<String, TilesFileModel> tilesMap = new HashMap<>();


    private static final Map<String, Map<Long, TPKZoomLevel>> tpkMap = new HashMap<>();
    private static final Map<String, TPKFile> tpkFileMap = new HashMap<>();


    private static final Map<String, FileDataStore> shpDataStores = new HashMap<>();

    /**
     * 字体文件模型
     */
    private static final Map<String, FontsFileModel> fontsMap = new HashMap<>();

    /**
     * 行政区划数据。key:行政级别、value:区划对象列表
     */
    @Getter
    private static final Map<Integer, List<SimpleFeature>> administrativeDivisionLevel = new HashMap<>();

    /**
     * 行政区划数据。key:区划ID、value:区划对象
     */
    @Getter
    private static final Map<Integer, SimpleFeature> administrativeDivision = new HashMap<>();

    /**
     * 行政区划层级树
     */
    @Getter
    private static AdministrativeDivisionTmp simpleAdminDivision;

    /**
     * 初始化数据源
     *
     * @param className 驱动名称
     * @param file      待链接的数据库文件
     */
    public static void initJdbcTemplate(String className, File file) {
        TilesFileModel dbFileModel = new TilesFileModel(file, className);
        tilesMap.put(file.getName(), dbFileModel);
    }

    /**
     * 初始化TPK文件
     *
     * @param tpk tpk地图数据文件
     */
    public static void initTpk(File tpk) {
        Map<Long, TPKZoomLevel> zoomLevelMap = new HashMap<>();
        TPKFile tpkFile = new TPKFile(tpk, zoomLevelMap);
        tpkMap.put(tpk.getName(), zoomLevelMap);
        tpkFileMap.put(tpk.getName(), tpkFile);
    }

    public static void initShapefile(File shapefile) {
        FileDataStore dataStore = null;
        try {
            dataStore = FileDataStoreFinder.getDataStore(shapefile);
        } catch (IOException e) {
            logger.error("FileDataStoreFinder.getDataStore() failed: " + shapefile.getAbsolutePath());
        }
        shpDataStores.put(shapefile.getName(), dataStore);
    }

    public static FileDataStore getShpDataStores(String shapefile) {
        return shpDataStores.get(shapefile);
    }


    /**
     * TPK文件的元数据
     *
     * @param fileName TPK文件
     * @return tpk文件的元数据
     */
    public MetaData getTpkMetaData(String fileName) {
        MetaData metaData = new MetaData();
        if (StringUtils.hasLength(fileName)) {
            TPKFile tpkFile = tpkFileMap.get(fileName);
            if (tpkFile != null) {
                metaData.setBounds(tpkFile.getBounds().toString());
                metaData.setCrs(tpkFile.getBounds().getCoordinateReferenceSystem().getName().toString());
                metaData.setFormat(tpkFile.getImageFormat().toLowerCase(Locale.getDefault()));
                metaData.setMaxzoom(tpkFile.getMaxZoomLevel());
                metaData.setMinzoom(tpkFile.getMinZoomLevel());
            }
        }
        return metaData;
    }

    /**
     * 获取TPK文件数据
     *
     * @param fileName 文件名
     * @return tpk文件数据
     */
    public TPKFile getTpkData(String fileName) {
        return tpkFileMap.get(fileName);
    }


    /**
     * 初始化字体库文件
     *
     * @param fontFolder 字体文件目录
     */
    public static void initFontsFile(File fontFolder) {
        fontsMap.put(fontFolder.getName(), new FontsFileModel(fontFolder));
    }

    /**
     * geojson格式的加载行政区划边界数据。
     *
     * @param boundary 行政区划边界
     */
    public static void initBoundaryFile(File boundary) {
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
            logger.error("Read OSM file failed：" + boundary.getAbsolutePath());
        }
    }

    private static void packageModel() {
        administrativeDivision.values().forEach(feature -> {
            if (simpleAdminDivision == null) {
                simpleAdminDivision = initRootNode(feature);
            } else {
                Object parentsObj = feature.getAttribute("parents");
                if (parentsObj != null) {
                    String[] parents = parentsObj.toString().split(",");

                    AdministrativeDivisionTmp tempNode = new AdministrativeDivisionTmp(feature, Integer.parseInt(parents[0]));

                    for (int i = 0; i < parents.length; i++) {
                        int parentId = Integer.parseInt(parents[i]);
                        Optional<AdministrativeDivisionTmp> nodeOpt = findNode(simpleAdminDivision, parentId);
                        if (nodeOpt.isPresent()) {
                            AdministrativeDivisionTmp child = nodeOpt.get();
                            //如果父节点已经在早期全路径时构造过了，则不需要再追加此单节点。
                            if (!contains(child, (int) feature.getAttribute("osm_id"))) {
                                child.getChildren().add(tempNode);
                            }
                            break;
                        } else {
                            AdministrativeDivisionTmp tmp = new AdministrativeDivisionTmp(administrativeDivision.get(parentId), Integer.parseInt(parents[i + 1]));
                            tmp.getChildren().add(tempNode);
                            tempNode = tmp;
                        }
                    }
                }
            }
        });
    }

    private static boolean contains(AdministrativeDivisionTmp child, int parentId) {
        for (AdministrativeDivisionTmp item : child.getChildren()) {
            if (item.getId() == parentId) {
                return true;
            }
        }
        return false;
    }


    private static AdministrativeDivisionTmp initRootNode(SimpleFeature feature) {
        Object parents = feature.getAttribute("parents");
        if (parents == null) {
            return new AdministrativeDivisionTmp(feature, -1);
        } else {
            String[] split = parents.toString().split(",");
            List<AdministrativeDivisionTmp> children = new ArrayList<>();
            AdministrativeDivisionTmp tmp = null;
            for (int i = 0; i < split.length; i++) {
                int osmId = Integer.parseInt(split[i]);
                if (i + 1 > split.length - 1) {
                    tmp = new AdministrativeDivisionTmp(administrativeDivision.get(osmId), -1);
                    tmp.setChildren(children);
                } else {
                    tmp = new AdministrativeDivisionTmp(administrativeDivision.get(osmId), Integer.parseInt(split[i + 1]));
                    tmp.setChildren(children);
                    children = new ArrayList<>();
                    children.add(tmp);
                }
            }
            return tmp;
        }
    }

    private static Optional<AdministrativeDivisionTmp> findNode(AdministrativeDivisionTmp tmp, int parentId) {
        if (tmp.getId() == parentId) {
            return Optional.of(tmp);
        }
        List<AdministrativeDivisionTmp> children = tmp.getChildren();
        for (AdministrativeDivisionTmp item : children) {
            if (item.getId() == parentId) {
                return Optional.of(item);
            } else {
                Optional<AdministrativeDivisionTmp> childOpt = findNode(item, parentId);
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
    public Map<String, String> getTileMetaData(String fileName) {
        if (StringUtils.hasLength(fileName)) {
            TilesFileModel model = tilesMap.get(fileName);
            return model.getMetaDataMap();
        } else {
            return new HashMap<>();
        }
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
            return Optional.of(fontsFileModel);
        } else {
            return Optional.empty();
        }
    }
}
