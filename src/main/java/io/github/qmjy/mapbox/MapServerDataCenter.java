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

import io.github.qmjy.mapbox.model.AdministrativeDivisionVo;
import io.github.qmjy.mapbox.model.FontsFileModel;
import io.github.qmjy.mapbox.model.TilesFileModel;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.geojson.GeoJSONReader;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
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
    private static final Map<String, TilesFileModel> tilesMap = new HashMap<>();
    /**
     * 字体文件模型
     */
    private static final Map<String, FontsFileModel> fontsMap = new HashMap<>();

    /**
     * 行政区划数据。key:行政级别、value:区划对象列表
     */
    private static final Map<Integer, List<SimpleFeature>> administrativeDivisionLevel = new HashMap<>();

    /**
     * 行政区划数据。key:区划ID、value:区划对象
     */
    private static final Map<Integer, SimpleFeature> administrativeDivision = new HashMap<>();

    /**
     * 行政区划层级树
     */
    private static AdministrativeDivisionVo simpleAdminDivision;

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

                int adminLevel = (int) feature.getAttribute("admin_level");
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
        Integer[] array = administrativeDivisionLevel.keySet().toArray(new Integer[0]);
        Arrays.sort(array);
        for (int level : array) {
            List<SimpleFeature> simpleFeatures = administrativeDivisionLevel.get(level);
            if (simpleAdminDivision == null) {
                simpleAdminDivision = new AdministrativeDivisionVo(simpleFeatures.getFirst(), 0);
                continue;
            }
            for (SimpleFeature simpleFeature : simpleFeatures) {
                int adminLevel = (int) simpleFeature.getAttribute("admin_level");
                String[] parents = simpleFeature.getAttribute("parents").toString().split(",");
                for (String parentIdStr : parents) {
                    int parentId = Integer.parseInt(parentIdStr);
                    if (parentId == simpleAdminDivision.getId()) {
                        simpleAdminDivision.getChildren().add(new AdministrativeDivisionVo(simpleFeature, parentId));
                        continue;
                    }
                    Optional<AdministrativeDivisionVo> parentNode = findParentNode(simpleAdminDivision.getChildren(), parentId, adminLevel);
                    if (parentNode.isPresent()) {
                        AdministrativeDivisionVo administrativeDivisionVo = parentNode.get();
                        administrativeDivisionVo.getChildren().add(new AdministrativeDivisionVo(simpleFeature, parentId));
                        break;
                    }
                }
                //TODO any node can not find parent?
            }
        }
    }

    /**
     * 找到指定ID的父节点
     *
     * @param list              带查找的子节点
     * @param parentId          父节点ID
     * @param currentAdminLevel 当前节点行政区划级别
     * @return 找到的父节点ID
     */
    private static Optional<AdministrativeDivisionVo> findParentNode(List<AdministrativeDivisionVo> list, int parentId, int currentAdminLevel) {
        if (!list.isEmpty()) {
            for (AdministrativeDivisionVo child : list) {
                if (child.getId() == parentId) {
                    return Optional.of(child);
                } else {
                    List<AdministrativeDivisionVo> children = child.getChildren();
                    if (children.isEmpty()) {
                        continue;
                    }
                    Optional<AdministrativeDivisionVo> parentNode = findParentNode(children, parentId, currentAdminLevel);
                    if (parentNode.isPresent()) {
                        return parentNode;
                    }
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

    /**
     * 级联行政区划
     *
     * @param langType 可选参数，支持本地语言(0:default)和英语(1)。
     * @return 对应语言的级联数据
     */
    public static AdministrativeDivisionVo getSimpleAdminDivisionByLang(int langType) {
        AdministrativeDivisionVo clone = simpleAdminDivision.clone();
        //TODO 按照语言返回基本数据
        return clone;
    }

    public static Map<Integer, List<SimpleFeature>> getAdministrativeDivisionLevel() {
        return administrativeDivisionLevel;
    }

    public static Map<Integer, SimpleFeature> getAdministrativeDivision() {
        return administrativeDivision;
    }

    public static AdministrativeDivisionVo getSimpleAdminDivision() {
        return simpleAdminDivision;
    }
}
