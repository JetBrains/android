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
package com.android.tools.idea.gradle.structure.model

import com.android.ide.common.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.structure.model.meta.asString

enum class PsModuleType(val androidModuleType: IdeAndroidProjectType? = null) {
  UNKNOWN,
  ANDROID_APP(IdeAndroidProjectType.PROJECT_TYPE_APP),
  ANDROID_LIBRARY(IdeAndroidProjectType.PROJECT_TYPE_LIBRARY),
  ANDROID_INSTANTAPP(IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP),
  ANDROID_FEATURE(IdeAndroidProjectType.PROJECT_TYPE_FEATURE),
  ANDROID_DYNAMIC_FEATURE(IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE),
  ANDROID_TEST(IdeAndroidProjectType.PROJECT_TYPE_TEST),
  JAVA,
}

fun moduleTypeFromAndroidModuleType(androidModuleType: IdeAndroidProjectType?): PsModuleType = when (androidModuleType) {
  null -> PsModuleType.UNKNOWN
  IdeAndroidProjectType.PROJECT_TYPE_APP -> PsModuleType.ANDROID_APP
  IdeAndroidProjectType.PROJECT_TYPE_LIBRARY -> PsModuleType.ANDROID_LIBRARY
  IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP -> PsModuleType.ANDROID_INSTANTAPP
  IdeAndroidProjectType.PROJECT_TYPE_FEATURE -> PsModuleType.ANDROID_FEATURE
  IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE -> PsModuleType.ANDROID_DYNAMIC_FEATURE
  IdeAndroidProjectType.PROJECT_TYPE_TEST -> PsModuleType.ANDROID_TEST
  IdeAndroidProjectType.PROJECT_TYPE_ATOM -> PsModuleType.UNKNOWN
}

fun moduleProjectTypeFromPlugin(plugin: String): PsModuleType = when (plugin) {
  "java", "java-library" -> PsModuleType.JAVA
  "com.android.application", "android" -> PsModuleType.ANDROID_APP
  "com.android.library", "android-library" -> PsModuleType.ANDROID_LIBRARY
  "com.android.instantapp" -> PsModuleType.ANDROID_INSTANTAPP
  "com.android.feature" -> PsModuleType.ANDROID_FEATURE
  "com.android.dynamic-feature" -> PsModuleType.ANDROID_DYNAMIC_FEATURE
  "com.android.test" -> PsModuleType.ANDROID_TEST
  else -> PsModuleType.UNKNOWN
}

fun GradleBuildModel.parsedModelModuleType(): PsModuleType =
    plugins()
        .mapNotNull { moduleProjectTypeFromPlugin(it.name().asString().orEmpty()) }
        .firstOrNull { it != PsModuleType.UNKNOWN }
    ?: PsModuleType.UNKNOWN
