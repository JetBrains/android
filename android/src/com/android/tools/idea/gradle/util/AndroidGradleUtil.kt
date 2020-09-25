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
@file:JvmName("AndroidGradleUtil")
package com.android.tools.idea.gradle.util

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.io.IOException

/**
 * Returns a name that should be used when displaying a [Module] to the user. This method should be used unless there is a very
 * good reason why it does not work for you. This method performs as follows:
 *   1 - If the [Module] is not registered as a Gradle module then the module's name is returned.
 *   2 - If the [Module] directly corresponds to a Gradle source set, then the name of the source set is returned.
 *   3 - If the [Module] represents the root Gradle project then the projects name is returned.
 *   4 - If the [Module] represents any other module then the root project, the last part of the Gradle path is used.
 *   5 - If any of 2 to 4 fail, for any reason then we always fall back to just using the [Module]'s name.
 */
fun getDisplayNameForModule(module: Module): String {
  fun getNameFromGradlePath(module: Module) : String? {
    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return null
    // If we have a module per source-set we need ensure that the names we display are the name of the source-set rather than the module
    // name.
    if (GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY == ExternalSystemApiUtil.getExternalModuleType(module)) {
      return GradleProjectResolverUtil.getSourceSetName(module)
    }
    val shortName: String? = ExternalSystemApiUtil.getExternalProjectId(module)
    val isRootModule = StringUtil.equals(ExternalSystemApiUtil.getExternalProjectPath(module),
                                         ExternalSystemApiUtil.getExternalRootProjectPath(
                                           module))
    return if (isRootModule || shortName == null) shortName else StringUtil.getShortName(shortName, ':')
  }
  return getNameFromGradlePath(module) ?: module.name
}

data class GradleProjectPath(val projectRoot: File, val gradleProjectPath: String)

@JvmName("getModuleGradleProjectPath")
fun Module.getGradleProjectPath(): GradleProjectPath? {
  // The external system projectId is:
  // <projectName> for the root module of a main or only build in a composite build
  // :gradle:path for a non-root module of a main or only build in a composite build
  // <projectName> for the root module of an included build
  // <projectName>:gradle:path for a non-root module of an included build
  val externalSystemProjectId = ExternalSystemApiUtil.getExternalProjectId(this) ?: return null
  val gradleProjectPath = ":" + externalSystemProjectId.substringAfter(':', "")
  val rootFolder = File(GradleRunnerUtil.resolveProjectPath(this) ?: return null).let {
    try {
      it.canonicalFile
    }
    catch (e: IOException) {
      it
    }
  }
  return GradleProjectPath(rootFolder, gradleProjectPath)
}