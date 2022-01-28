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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.gradle.model.IdeModuleDependency
import com.android.tools.idea.gradle.model.IdeModuleSourceSet
import com.android.tools.idea.gradle.model.buildId
import com.android.tools.idea.gradle.model.projectPath
import com.android.tools.idea.gradle.model.sourceSet
import com.android.utils.FileUtils
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil
import java.io.File
import java.io.IOException

data class GradleProjectPathCore(val buildRoot: @SystemIndependent String, val path: String)

data class GradleProjectPath(
  val core: GradleProjectPathCore,
  val sourceSet: IdeModuleSourceSet
) {
  constructor (buildRoot: File, path: String, sourceSet: IdeModuleSourceSet) : this(buildRoot.path, path, sourceSet)
  constructor (buildRoot: @SystemIndependent String, path: String, sourceSet: IdeModuleSourceSet): this(
      core = GradleProjectPathCore(FileUtil.toSystemIndependentName(buildRoot), path),
      sourceSet
  )

  val buildRoot: @SystemIndependent String = core.buildRoot
  val path: String get() = core.path
}

internal fun Module.getGradleProjectPathCore(useCanonicalPath: Boolean = false): GradleProjectPathCore? {
  // The external system projectId is:
  // <projectName-uniqualized-by-Gradle> for the root module of a main or only build in a composite build
  // :gradle:path for a non-root module of a main or only build in a composite build
  // <projectName-uniqualized-by-Gradle> for the root module of an included build
  // <projectName-uniqualized-by-Gradle>:gradle:path for a non-root module of an included build
  // NOTE: The project name uniqualization is performed by Gradle and may be version dependent. It should not be assumed to match
  //       any Gradle project name or any Gradle included build name.
  val externalSystemProjectId = ExternalSystemApiUtil.getExternalProjectId(this) ?: return null
  val gradleProjectPath = ":" + externalSystemProjectId.substringAfter(':', "")
  val rootFolder = File(GradleRunnerUtil.resolveProjectPath(this) ?: return null).let {
    if (useCanonicalPath) {
      try {
        it.canonicalFile
      }
      catch (e: IOException) {
        it
      }
    }
    else {
      it.absoluteFile
    }
  }
  return GradleProjectPathCore(FileUtils.toSystemIndependentPath(rootFolder.path), gradleProjectPath)
}

internal fun GradleProjectPathCore.toGradleProjectPath(): GradleProjectPath = GradleProjectPath(this,IdeModuleSourceSet.MAIN)

fun Module.getGradleProjectPath(): GradleProjectPath? = getGradleProjectPathCore()?.toGradleProjectPath()
internal fun IdeModuleDependency.getGradleProjectPath(): GradleProjectPath = GradleProjectPath(buildId, projectPath, sourceSet)