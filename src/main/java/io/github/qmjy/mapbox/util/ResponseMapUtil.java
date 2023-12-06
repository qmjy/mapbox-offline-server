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

package io.github.qmjy.mapbox.util;

import java.util.HashMap;
import java.util.Map;

/**
 * RESTful响应map工具
 *
 * @author liushaofeng
 */
public class ResponseMapUtil {
    /**
     * 响应正常的map封装
     *
     * @return 响应结果
     */
    public static Map<String, Object> ok() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("msg", "");
        map.put("data", "Nothing");
        return map;
    }

    /**
     * 响应正常的map封装
     *
     * @param data 待返回的数据类型
     * @return 响应结果
     */
    public static Map<String, Object> ok(Object data) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("msg", "");
        map.put("data", data);
        return map;
    }

    /**
     * 响应不正常的map封装
     *
     * @param msg 待返回的错误消息
     * @return 响应结果
     */
    public static Map<String, Object> nok(String msg) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("code", 1);
        map.put("msg", msg);
        map.put("data", "");
        return map;
    }
}
