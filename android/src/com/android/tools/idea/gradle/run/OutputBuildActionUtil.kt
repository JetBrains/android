/*
 * Copyright (C) 2019 The Android Open Source Project
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
@file:JvmName("OutputBuildActionUtil")

package com.android.tools.idea.gradle.run

import com.android.AndroidProjectTypes.PROJECT_TYPE_TEST
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.GradleUtil
import com.intellij.openapi.module.Module

private val agpVersion4dot0 = GradleVersion.tryParseAndroidGradlePluginVersion("4.0.0")!!
private val agpVersion3dot0 = GradleVersion.tryParseAndroidGradlePluginVersion("3.0.0")!!

/**
 * Creates BuildAction to be used during build based on AGP versions.
 * 1. For AGP [3.0, 4.0), use [OutputBuildAction] to obtain post build sync models.
 * 2. For AGP older than 3.0, or 4.0 and newer, don't use BuildAction during build.
 *    1) For AGP older than 3.0, post build sync models are not supported.
 *    2) For AGP 4.0 and newer, post build sync models are deprecated and replaced with output model file, the
 *       file location is known at Gradle sync time, and file content is populated during Build.
 */
fun create(modules: List<Module>): OutputBuildAction? {
  // TODO: return null BuildAction if isAgpEqualToOrGreaterThan(agpVersion4dot0, modules), pending on changes on AGP side.
  if (!isAgpEqualToOrGreaterThan(agpVersion3dot0, modules)) {
    return null
  }
  // AGP < 4.0.0 && AGP >= 3.0.0
  return OutputBuildAction(getModuleGradlePaths(modules))
}

private fun isAgpEqualToOrGreaterThan(version: GradleVersion, modules: List<Module>): Boolean =
  modules.mapNotNull { AndroidModuleModel.get(it)?.modelVersion }.all { it.compareIgnoringQualifiers(version) >= 0 }

/**
 * Get the gradle paths for the given module, all the tested projects (if it is a test app), and dynamic feature modules.
 * These paths will be used by the BuildAction run after build to know all the needed models.
 */
private fun getModuleGradlePaths(modules: List<Module>): Set<String> {
  val gradlePaths = mutableSetOf<String>()
  modules.mapNotNullTo(gradlePaths) { GradleUtil.getGradlePath(it) }
  modules
    .mapNotNull { AndroidModuleModel.get(it) }
    .forEach { androidModel ->
      val androidProject = androidModel.androidProject
      if (androidProject.projectType == PROJECT_TYPE_TEST) {
        gradlePaths.addAll(androidModel.selectedVariant.testedTargetVariants.map { it.targetProjectPath })
      }
      gradlePaths.addAll(androidProject.dynamicFeatures)
    }
  return gradlePaths
}