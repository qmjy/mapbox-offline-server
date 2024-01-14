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

package io.github.qmjy.mapbox.model;

import java.util.HashMap;
import java.util.Map;

public class MbtileMergeWrapper {
    /**
     * 所有需要合并的文件列表。filePath:mbtiles details
     */
    private Map<String, MbtileMergeFile> needMerges = new HashMap<>();
    /**
     * 总计要合并的tiles数据
     */
    private long totalCount = 0;
    /**
     * 最大的文件路径
     */
    private String largestFilePath = "";

    /**
     * 只能同一种格式进行合并
     */
    private String format = "";

    private int minZoom = Integer.MAX_VALUE;
    private int maxZoom = 0;
    private double minLat = Double.MAX_VALUE;
    private double maxLat = 0L;
    private double minLon = Double.MAX_VALUE;
    private double maxLon = 0L;

    public Map<String, MbtileMergeFile> getNeedMerges() {
        return needMerges;
    }

    public void setNeedMerges(Map<String, MbtileMergeFile> needMerges) {
        this.needMerges = needMerges;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public void addToTotal(long data) {
        this.totalCount += data;
    }

    public String getLargestFilePath() {
        return largestFilePath;
    }

    public void setLargestFilePath(String largestFilePath) {
        this.largestFilePath = largestFilePath;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public int getMinZoom() {
        return minZoom;
    }

    public void setMinZoom(int minZoom) {
        this.minZoom = minZoom;
    }

    public int getMaxZoom() {
        return maxZoom;
    }

    public void setMaxZoom(int maxZoom) {
        this.maxZoom = maxZoom;
    }

    public double getMinLat() {
        return minLat;
    }

    public void setMinLat(double minLat) {
        this.minLat = minLat;
    }

    public double getMaxLat() {
        return maxLat;
    }

    public void setMaxLat(double maxLat) {
        this.maxLat = maxLat;
    }

    public double getMinLon() {
        return minLon;
    }

    public void setMinLon(double minLon) {
        this.minLon = minLon;
    }

    public double getMaxLon() {
        return maxLon;
    }

    public void setMaxLon(double maxLon) {
        this.maxLon = maxLon;
    }
}
