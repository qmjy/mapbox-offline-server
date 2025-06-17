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

package io.github.qmjy.mapserver.util;


import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;


public class CoordinateTransformUtilTest {

    @Test
    public void test() {
        //经过百度匹配获得的坐标，默认是bd09mc（百度墨卡托坐标）
        double lng = 106.53121868831617;
        double lat = 29.627868273995304;
        //bd09mc转为GCJ02坐标系：即火星坐标系
        Coordinate gcj02 = CoordinateTransformUtil.bd09ToGcj02(lat, lng);
        //GCJ02坐标系转WGS84坐标系：即地球坐标系
        Coordinate wgs84 = CoordinateTransformUtil.gcj02ToGps84(gcj02.getX(), gcj02.getY());
        System.out.println(wgs84.getX() + "," + wgs84.getY());
    }
}