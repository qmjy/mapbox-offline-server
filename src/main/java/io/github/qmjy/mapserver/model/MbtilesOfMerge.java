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

package io.github.qmjy.mapserver.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "待合并mbtiles文件对象信息")
public class MbtilesOfMerge {
    @Schema(description = "待合并的mbtiles文件名称列表，多个名字用';'分割。")
    private String sourceNames;
    @Schema(description = "合并完成后的文件名字，不能与现有的mbtiles文件重名。例如：'target.mbtiles'")
    private String targetName;
}
