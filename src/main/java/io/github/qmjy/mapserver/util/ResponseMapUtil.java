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

package io.github.qmjy.mapserver.util;

import java.util.HashMap;
import java.util.Map;

/**
 * RESTful响应map工具
 *
 * @author liushaofeng
 */
public class ResponseMapUtil {

    /**
     * 响应结果：OK
     */
    public static final int STATUS_OK = 0;
    /**
     * 响应结果：资源未找到
     */
    public static final int STATUS_NOT_FOUND = 1;
    /**
     * 响应结果：API参数设置错误
     */
    public static final int STATUS_PARAM_CONFIG_ERROR_API = 2;

    /**
     * 响应结果：App参数设置错误
     */
    public static final int STATUS_PARAM_CONFIG_ERROR_APP = 3;

    /**
     * 相应结果：资源已存在
     */
    public static final int STATUS_RESOURCE_ALREADY_EXISTS = 4;


    /**
     * 相应结果：超出范围
     */
    public static final int STATUS_RESOURCE_OUT_OF_RANGE = 5;
    /**
     * 响应结果：其他
     */
    public static final int STATUS_OTHERS = 9;

    /**
     * 响应正常的map封装
     *
     * @return 响应结果
     */
    public static Map<String, Object> ok() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("code", STATUS_OK);
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
        map.put("code", STATUS_OK);
        map.put("msg", "");
        map.put("data", data);
        return map;
    }

    /**
     * 找不到资源
     *
     * @return 找不到资源的相应消息
     */
    public static Map<String, Object> notFound() {
        return notFound("Not found!");
    }

    /**
     * 找不到资源，并携带消息说明
     *
     * @param msg 待返回的提示内容
     * @return 找不到资源的相应消息
     */
    public static Map<String, Object> notFound(String msg) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("code", STATUS_NOT_FOUND);
        map.put("msg", msg);
        map.put("data", "");
        return map;
    }

    /**
     * 响应不正常的map封装
     *
     * @param code 响应码
     * @param msg  待返回的错误消息
     * @return 响应结果
     */
    public static Map<String, Object> nok(int code, String msg) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("msg", msg);
        map.put("data", "");
        return map;
    }
}
