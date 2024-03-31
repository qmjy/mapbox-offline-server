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


import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;

/**
 * Controller 公共常用方法
 *
 * @author liushaofeng
 */
public class BaseController {
    /**
     * 获取请求页面基础地址
     *
     * @param request HttpServletRequest
     * @return 请求地址前缀
     */
    public String getBasePath(HttpServletRequest request) {
        String path = request.getContextPath();
        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + path;
    }

    protected Locale getLang(int lang) {
        return switch (lang) {
            case 1 -> Locale.US;
            case 2 -> Locale.CHINA;
            default -> Locale.getDefault();
        };
    }
}
