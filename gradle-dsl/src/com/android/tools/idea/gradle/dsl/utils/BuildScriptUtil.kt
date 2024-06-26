/*
 * Copyright (C) 2021 The Android Open Source Project
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
@file:JvmName("BuildScriptUtil")

/**
 * This file replicates some methods from android.sdk.common; if anything is changed here then it should probably
 * also be changed in that module.
 */
package com.android.tools.idea.gradle.dsl.utils

import com.intellij.openapi.util.registry.Registry
import java.io.File

internal fun findGradleBuildFile(dirPath: File) : File {
  val groovyBuildFile = File(dirPath, FN_BUILD_GRADLE)
  if (groovyBuildFile.isFile) return groovyBuildFile
  val kotlinBuildFile = File(dirPath, FN_BUILD_GRADLE_KTS)
  if (kotlinBuildFile.isFile) return kotlinBuildFile
  if (Registry.`is`("android.gradle.declarative.plugin.studio.support", false)) {
    val gradleDeclarativeBuildFile = File(dirPath, FN_BUILD_GRADLE_DECLARATIVE)
    if (gradleDeclarativeBuildFile.isFile) return gradleDeclarativeBuildFile
  }

  // Default to Groovy if none exist.
  return groovyBuildFile
}

internal fun findGradleSettingsFile(dirPath: File) : File {
  val groovySettingsFile = File(dirPath, FN_SETTINGS_GRADLE)
  if (groovySettingsFile.isFile) return groovySettingsFile
  val kotlinSettingsFile = File(dirPath, FN_SETTINGS_GRADLE_KTS)
  if (kotlinSettingsFile.isFile) return kotlinSettingsFile
  if (Registry.`is`("android.gradle.declarative.plugin.studio.support", false)) {
    val gradleDeclarativeSettingsFile = File(dirPath, FN_SETTINGS_GRADLE_DECLARATIVE)
    if (gradleDeclarativeSettingsFile.isFile) return gradleDeclarativeSettingsFile
  }

  // Default to Groovy if none exist.
  return groovySettingsFile
}
