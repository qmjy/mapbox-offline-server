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

package io.github.qmjy.mapserver.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;


@RestController
@RequestMapping("/api/proxy")
@Tag(name = "代理服务管理", description = "三方在线地图服务接口代理能力")
public class MapServerProxyController {
    private final RestTemplateBuilder restTemplateBuilder;

    public MapServerProxyController(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplateBuilder = restTemplateBuilder;
    }

    /**
     * 高德地图交通瓦片数据加载代理
     * TODO KEY need to implement
     * @return 路况态势瓦片图层
     */
    @GetMapping(value = "/traffictile")
    @ResponseBody
    public ResponseEntity<ByteArrayResource> proxyTrafficTile(
            @Parameter(description = "代理服务器授权Key") @RequestParam(value = "key") String key,
            @Parameter(description = "待查询的底图瓦片坐标x") @RequestParam(value = "x") int x,
            @Parameter(description = "待查询的底图瓦片坐标y") @RequestParam(value = "y") int y,
            @Parameter(description = "待查询的底图瓦片层级zoom_level") @RequestParam(value = "z") int z) {
        if (x < 0 || y < 0 || z < 0) {
            return new ResponseEntity<>(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        RestTemplate build = restTemplateBuilder.build();
        String url = "https://tm.amap.com/trafficengine/mapabc/traffictile?v=1.0&;t=1&x=" + x + "&y=" + y + "&z=" + z;
        ResponseEntity<byte[]> forEntity = build.getForEntity(url, byte[].class);
        byte[] bytes = forEntity.getBody();
        if (bytes != null) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            ByteArrayResource resource = new ByteArrayResource(bytes);
            return ResponseEntity.ok().headers(headers).contentLength(bytes.length).body(resource);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
