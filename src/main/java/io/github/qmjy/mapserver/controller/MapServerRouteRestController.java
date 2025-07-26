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

package io.github.qmjy.mapserver.controller;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.Translation;
import io.github.qmjy.mapserver.MapServerDataCenter;
import io.github.qmjy.mapserver.util.ResponseMapUtil;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 路径规划
 *
 * @author Shaofeng liu
 */
@RestController
@RequestMapping("/api/route")
@Tag(name = "路径规划", description = "驾车、骑行、步行路径规划")
public class MapServerRouteRestController extends BaseController {

    /**
     * 环境还未就绪
     */
    private static final int ROUTE_ERROR_CODE_NOT_READY = 10001;
    /**
     * 路径不可达
     */
    private static final int ROUTE_ERROR_CODE_CAN_NOT_REACH = 10002;
    /**
     * 路径规划错误
     */
    private static final int ROUTE_ERROR_CODE_ROUTE_ERROR = 10009;

    /**
     * 路径规划
     *
     * @param startLongitude 开始经纬度
     * @param startLatitude  开始纬度
     * @param endLongitude   结束经度
     * @param endLatitude    结束纬度
     * @param routeType      路径规划方式。0：驾车、1：骑行、2：步行
     * @param lang           支持本地语言(0: default)、英语（1）、简体中文（2）。
     * @return 路径规划结果
     */
    @GetMapping("/{osmpbf}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> route(@Parameter(description = "用于导航的osm.pbf文件名，例如：china-latest.osm.pbf") @PathVariable("osmpbf") String osmpbf,
                                                     @Parameter(description = "待规划的起点经度坐标，例如：104.00504") @RequestParam(value = "startLongitude") double startLongitude,
                                                     @Parameter(description = "待规划的起点纬度坐标，例如：30.675252") @RequestParam(value = "startLatitude") double startLatitude,
                                                     @Parameter(description = "待规划的终点经度坐标，例如：104.068374") @RequestParam(value = "endLongitude") double endLongitude,
                                                     @Parameter(description = "待规划的终点纬度坐标，例如：30.66082") @RequestParam(value = "endLatitude") double endLatitude,
                                                     @Parameter(description = "出行方式。0：驾车（default）、1：骑行、2：步行") @RequestParam(value = "routeType", required = false, defaultValue = "0") int routeType,
                                                     @Parameter(description = "支持本地语言(0: default)、英语（1）、简体中文（2）") @RequestParam(value = "lang", required = false, defaultValue = "0") int lang) {
        GraphHopper hopper = MapServerDataCenter.getInstance().getHopperMap().get(osmpbf);
        if (hopper == null) {
            Map<String, Object> ok = ResponseMapUtil.nok(ROUTE_ERROR_CODE_NOT_READY, "数据源未就绪或不存在：" + osmpbf);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ok);
        }

        String profile = getProfile(routeType);
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(profile));

        GHRequest req = new GHRequest(startLatitude, startLongitude, endLatitude, endLongitude)
                .setProfile(profile)
                .setLocale(getLang(lang));
        GHResponse rsp = hopper.route(req);

        try {
            // handle errors
            if (rsp.hasErrors()) throw new RuntimeException(rsp.getErrors().toString());
        } catch (Exception e) {
            Map<String, Object> ok = ResponseMapUtil.nok(ROUTE_ERROR_CODE_ROUTE_ERROR, rsp.getErrors().getFirst().getMessage());
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ok);
        }

        // use the best path, see the GHResponse class for more possibilities.
        List<ResponsePath> responsePaths = rsp.getAll();
        List<HashMap<Object, Object>> paths = new ArrayList<>();
        for (ResponsePath path : responsePaths) {
            HashMap<Object, Object> data = new HashMap<>();
            data.put("timeCostInMs", (path.getTime()) / 1000);
            data.put("distanceInM", path.getDistance());

            Translation tr = hopper.getTranslationMap().getWithFallBack(getLang(lang));
            InstructionList instructions = path.getInstructions();
            for (Instruction instruction : instructions) {
                instruction.getExtraInfoJSON().put("description", instruction.getTurnDescription(tr));
            }
            data.put("instructions", instructions);
            paths.add(data);
        }
        Map<String, Object> ok = ResponseMapUtil.ok(paths);
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
