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

package io.github.qmjy.mapbox.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * IO工具类
 */
public class IOUtils {

    /**
     * GZIP压缩数据
     *
     * @param data 待压缩的数据
     * @return 压缩后的GZIP数据
     */
    public static byte[] compress(byte[] data) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(out);
            gzip.write(data);
            gzip.close();
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /**
     * 解压缩数据
     *
     * @param compressedData GZIP数据
     * @return 解压后的数据
     */
    public static byte[] decompress(byte[] compressedData) {
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(compressedData);
            GZIPInputStream gunzip = new GZIPInputStream(in);
            byte[] buffer = new byte[2048];
            int n;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while ((n = gunzip.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
            gunzip.close();
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
