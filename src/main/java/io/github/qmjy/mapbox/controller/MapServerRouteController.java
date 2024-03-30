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

package io.github.qmjy.mapbox.controller;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import io.github.qmjy.mapbox.MapServerDataCenter;
import io.github.qmjy.mapbox.util.ResponseMapUtil;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


/**
 * 路径规划
 *
 * @author Shaofeng liu
 */
@RestController
@RequestMapping("/api/route")
@Tag(name = "路径规划", description = "驾车、骑行、步行路径规划")
public class MapServerRouteController {


    private static final int ROUTE_ERROR_CODE_NOT_READY = 10001;
    /**
     * 路径不可达
     */
    private static final int ROUTE_ERROR_CODE_CAN_NOT_REACH = 10002;

    /**
     * 路径规划
     *
     * @param startLongitude 开始经纬度
     * @param startLatitude  开始纬度
     * @param endLongitude   结束经度
     * @param endLatitude    结束纬度
     * @param routeType      路径规划方式。0：驾车、1：骑行、2：步行
     * @return 路径规划结果
     */
    @GetMapping("")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> route(@Parameter(description = "待规划的起点经度坐标，例如：103.897418") @RequestParam(value = "startLongitude") double startLongitude,
                                                     @Parameter(description = "待规划的起点纬度坐标，例如：30.791177") @RequestParam(value = "startLatitude") double startLatitude,
                                                     @Parameter(description = "待规划的终点经度坐标，例如：103.895101") @RequestParam(value = "endLongitude") double endLongitude,
                                                     @Parameter(description = "待规划的终点纬度坐标，例如：30.764163") @RequestParam(value = "endLatitude") double endLatitude,
                                                     @Parameter(description = "出行方式。0：驾车（default）、1：骑行、2：步行") @RequestParam(value = "routeType", required = false, defaultValue = "0") int routeType) {
        GraphHopper hopper = MapServerDataCenter.getHopper();

        //输入你的经度纬度，选择交通方式
        GHRequest req = new GHRequest(startLongitude, startLatitude, endLongitude, endLatitude).
                setProfile(getProfile(routeType)).setLocale(Locale.CHINA);
        if (hopper == null) {
            Map<String, Object> ok = ResponseMapUtil.nok(ROUTE_ERROR_CODE_NOT_READY, "环境还未就绪！");
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ok);
        }
        GHResponse rsp = hopper.route(req);

        //如果路线不可达抛异常
        try {
            // handle errors
            if (rsp.hasErrors()) throw new RuntimeException(rsp.getErrors().toString());
        } catch (Exception e) {
            Map<String, Object> ok = ResponseMapUtil.nok(ROUTE_ERROR_CODE_CAN_NOT_REACH, "路径不可达！");
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ok);
        }

        // use the best path, see the GHResponse class for more possibilities.
        ResponsePath path = rsp.getBest();

        HashMap<Object, Object> data = new HashMap<>();
        data.put("timeCostInMs", (path.getTime()) / 1000);
        data.put("timeDistanceInM", path.getDistance());
        data.put("instructions", path.getInstructions());
        Map<String, Object> ok = ResponseMapUtil.ok(data);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ok);
    }

    private String getProfile(int routeType) {
        if (1 == routeType) {
            return "bike";
        } else if (2 == routeType) {
            return "foot";
        } else {
            return "car";
        }
    }
}
