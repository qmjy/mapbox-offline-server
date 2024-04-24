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

import io.github.qmjy.mapserver.service.AsyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * IO工具类
 */
public class IOUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(IOUtils.class);

    public static void mkdirs(String path) {
        if (!new File(path).exists()) {
            if (!new File(path).mkdirs()) {
                LOGGER.error("mkdirs failed: " + path);
            }
        }
    }


    /**
     * 获取操作系统的临时文件夹目录
     *
     * @return 临时文件夹目录
     */
    public static String getTmpFolder() {
        try {
            return Files.createTempDirectory("wrangle").toFile().getAbsolutePath();
        } catch (IOException e) {
            return System.getProperty("java.io.tmpdir");
        }
    }

    public static String getBaseName(String filename) {
        return removeExtension(getName(filename));
    }

    public static String getName(String filename) {
        if (filename == null) {
            return null;
        } else {
            failIfNullBytePresent(filename);
            int index = indexOfLastSeparator(filename);
            return filename.substring(index + 1);
        }
    }

    public static int indexOfLastSeparator(String filename) {
        if (filename == null) {
            return -1;
        } else {
            int lastUnixPos = filename.lastIndexOf(47);
            int lastWindowsPos = filename.lastIndexOf(92);
            return Math.max(lastUnixPos, lastWindowsPos);
        }
    }

    private static void failIfNullBytePresent(String path) {
        int len = path.length();

        for (int i = 0; i < len; ++i) {
            if (path.charAt(i) == 0) {
                throw new IllegalArgumentException("Null byte present in file/path name. There are no known legitimate use cases for such data, but several injection attacks may use it");
            }
        }

    }

    public static String getExtension(String filename) {
        if (filename == null) {
            return null;
        } else {
            int index = indexOfExtension(filename);
            return index == -1 ? "" : filename.substring(index + 1);
        }
    }

    public static int indexOfExtension(String filename) {
        if (filename == null) {
            return -1;
        } else {
            int extensionPos = filename.lastIndexOf(46);
            int lastSeparator = indexOfLastSeparator(filename);
            return lastSeparator > extensionPos ? -1 : extensionPos;
        }
    }

    public static String removeExtension(String filename) {
        if (filename == null) {
            return null;
        } else {
            failIfNullBytePresent(filename);
            int index = indexOfExtension(filename);
            return index == -1 ? filename : filename.substring(0, index);
        }
    }

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
