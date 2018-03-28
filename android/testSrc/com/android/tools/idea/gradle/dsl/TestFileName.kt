/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl

import com.intellij.openapi.util.io.FileUtil
import java.io.File

enum class TestFileName(val path: String) {
  AAPT_OPTIONS_PARSE_ELEMENTS_ONE("aaptOptions/parseElementsOne"),
  AAPT_OPTIONS_PARSE_ELEMENTS_TWO("aaptOptions/parseElementsTwo"),
  AAPT_OPTIONS_EDIT_ELEMENTS("aaptOptions/editElements"),
  AAPT_OPTIONS_EDIT_IGNORE_ASSET_PATTERN("aaptOptions/editIgnoreAssetPattern"),
  AAPT_OPTIONS_ADD_ELEMENTS("aaptOptions/addElements"),
  AAPT_OPTIONS_REMOVE_ELEMENTS("aaptOptions/removeElements"),
  AAPT_OPTIONS_REMOVE_ONE_ELEMENT("aaptOptions/removeOneElementInList"),
  AAPT_OPTIONS_REMOVE_LAST_ELEMENT("aaptOptions/removeLastElementInList"),

  ;
  fun toFile(basePath: String, extension: String): File {
    val path = FileUtil.toSystemDependentName(basePath) + File.separator + FileUtil.toSystemDependentName(path) + extension
    return File(path)
  }
}