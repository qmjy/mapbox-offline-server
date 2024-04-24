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

import java.io.IOException;
import java.nio.file.Files;

public class TempTest {

   @org.junit.Test
    public void test(){
       try {
           String absolutePath = Files.createTempDirectory("").toFile().getAbsolutePath();
           System.out.println(absolutePath);
           String tmpdir = Files.createTempDirectory("wrangle").toFile().getAbsolutePath();
           System.out.println(tmpdir);
           String tmpDirsLocation = System.getProperty("java.io.tmpdir");
           System.out.println(tmpDirsLocation);
       } catch (IOException e) {
           throw new RuntimeException(e);
       }
    }
}
