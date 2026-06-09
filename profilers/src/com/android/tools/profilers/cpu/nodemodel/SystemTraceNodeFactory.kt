/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.profilers.cpu.nodemodel

import com.intellij.openapi.util.text.StringUtil

/**
 * This Factory returns instances of {@link SystemTraceNodeModel}s, guaranteeing that nodes that represents a same object would be mapped to
 * a single instance.
 */
class SystemTraceNodeFactory {
  private val nodeMap = mutableMapOf<String, SystemTraceNodeModel>()

  fun getNode(name: String): SystemTraceNodeModel {
    return nodeMap.getOrPut(name) {
      val safeName = StringUtil.escapeXmlEntities(name)
      val canonicalName = NUMBER_SUFFIX_PATTERN.replace(safeName, "")
      SystemTraceNodeModel(canonicalName, safeName)
    }
  }

  companion object {
    // Pattern to match names ending with space+number. Eg: Frame 1234, Choreographer#doFrame 1234.
    private val NUMBER_SUFFIX_PATTERN = Regex(" (\\d)+$")
  }
}
