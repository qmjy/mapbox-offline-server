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

package io.github.qmjy.mapbox.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 行政区划数据，用于前端返回
 *
 * @author liushaofeng
 */
public class AdministrativeDivision {
    @Schema(description = "行政区划节点ID", example = "-2110264")
    private int id = 0;
    @Schema(description = "行政区划父节点ID列表", example = "-913068")
    private int parentId = 0;
    @Schema(description = "行政区划本地名称", example = "成都市")
    private String name = "";
    @Schema(description = "行政区划级别", example = "3")
    private int adminLevel = 0;
    @Schema(description = "子行政区划", example = "")
    private List<AdministrativeDivision> children = new ArrayList<>();

    /**
     * 构造方法，此模型用于返回前端数据
     *
     * @param adminDivision 行政区划数据全集
     * @param langType      可选参数，支持本地语言(0:default)和英语(1)。
     */
    public AdministrativeDivision(AdministrativeDivisionTmp adminDivision, int langType) {
        this.id = adminDivision.getId();
        this.parentId = adminDivision.getParentId();
        this.name = langType == 0 ? adminDivision.getName() : adminDivision.getNameEn();
        this.adminLevel = adminDivision.getAdminLevel();
        this.children = wrap(adminDivision.getChildren(), langType);
    }

    private List<AdministrativeDivision> wrap(List<AdministrativeDivisionTmp> children, int langType) {
        List<AdministrativeDivision> list = new ArrayList<>();
        children.forEach(item -> {
            list.add(new AdministrativeDivision(item, langType));
        });
        return list;
    }

    public int getId() {
        return id;
    }

    public int getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public int getAdminLevel() {
        return adminLevel;
    }

    public List<AdministrativeDivision> getChildren() {
        return children;
    }
}
