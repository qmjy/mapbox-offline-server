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

package io.github.qmjy.mapserver.model;

import lombok.Getter;
import lombok.Setter;
import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * 行政区划数据，树形结构
 *
 * @author liushaofeng
 */
@Getter
public class AdministrativeDivisionNode implements Cloneable {
    private final int id;
    private final int parentId;
    private final String name;
    private final String nameEn;
    private final int adminLevel;
    private final Geometry geometry;
    @Setter
    private List<AdministrativeDivisionNode> children = new ArrayList<>();

    /**
     * 构造方法
     *
     * @param simpleFeature 特性
     * @param parentId      父ID
     */
    public AdministrativeDivisionNode(SimpleFeature simpleFeature, int parentId) {
        this.id = (int) simpleFeature.getAttribute("osm_id");
        this.name = (String) (simpleFeature.getAttribute("local_name") == null ? simpleFeature.getAttribute("name") : simpleFeature.getAttribute("local_name"));
        Object nameEnObj = simpleFeature.getAttribute("name_en");
        this.nameEn = nameEnObj == null ? "" : String.valueOf(nameEnObj);
        this.geometry = (Geometry) simpleFeature.getAttribute("geometry");
        this.parentId = parentId;
        this.adminLevel = simpleFeature.getAttribute("admin_level") == null ? -1 : (int) simpleFeature.getAttribute("admin_level");
    }

    /**
     * 构造方法
     *
     * @param id         id
     * @param parentId   父ID
     * @param name       行政区划节点名称
     * @param nameEn     行政区划节点英文名称
     * @param adminLevel 行政区划级别
     * @param geometry   行政区划地理边界
     */
    public AdministrativeDivisionNode(int id, int parentId, String name, String nameEn, int adminLevel, Geometry geometry) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.nameEn = nameEn;
        this.adminLevel = adminLevel;
        this.geometry = geometry;
    }

    /**
     * 复制一份数据出来，不能在原始引用上修改子节点为空。本clone方法为"/api/geo/admins"接口定制clone方法
     *
     * @return clone后的对象
     */
    @Override
    public AdministrativeDivisionNode clone() {
        try {
            AdministrativeDivisionNode clone = (AdministrativeDivisionNode) super.clone();
            ArrayList<AdministrativeDivisionNode> newChildren = new ArrayList<>();

            List<AdministrativeDivisionNode> oldChildren = this.getChildren();
            for (AdministrativeDivisionNode tmp : oldChildren) {
                newChildren.add(new AdministrativeDivisionNode(tmp.getId(), tmp.getParentId(), tmp.getName(), tmp.getNameEn(), tmp.getAdminLevel(), tmp.getGeometry()));
            }

            clone.setChildren(newChildren);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}