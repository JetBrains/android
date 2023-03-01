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

import com.android.tools.idea.gradle.model.IdeModuleSourceSet
import com.android.tools.idea.gradle.model.impl.IdeModuleSourceSetImpl
import com.android.utils.FileUtils
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getExternalModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.text.nullize
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getGradleIdentityPathOrNull
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

sealed class GradleProjectPath {
  abstract val buildRoot: @SystemIndependent String
  abstract val path: String
}

data class GradleHolderProjectPath constructor(
  override val buildRoot: @SystemIndependent String,
  override val path: String
) : GradleProjectPath()

data class GradleSourceSetProjectPath constructor(
  override val buildRoot: @SystemIndependent String,
  override val path: String,
  val sourceSet: IdeModuleSourceSet
) : GradleProjectPath()

val GradleProjectPath.buildRootDir: File get() = File(buildRoot)
fun GradleProjectPath.toHolder(): GradleHolderProjectPath = GradleHolderProjectPath(buildRoot, path)

fun GradleProjectPath.resolve(absoluteOrRelativeGradlePath: String): GradleHolderProjectPath {
  return GradleHolderProjectPath(
    buildRoot = buildRoot,
    path = when {
      absoluteOrRelativeGradlePath.startsWith(":") -> absoluteOrRelativeGradlePath
      path == ":" -> ":$absoluteOrRelativeGradlePath"
      else -> "$path:$absoluteOrRelativeGradlePath"
    }
  )
}

fun GradleProjectPath.toSourceSetPath(sourceSet: IdeModuleSourceSet): GradleSourceSetProjectPath {
  return GradleSourceSetProjectPath(buildRoot, path, sourceSet)
}

internal fun Module.internalGetGradleProjectPath(): GradleProjectPath? {
  val buildRootFolder = File(GradleRunnerUtil.resolveProjectPath(this) ?: return null)
  // The external system projectId is:
  // <projectName-uniqualized-by-Gradle> for the root module of a main or only build in a composite build
  // :gradle:path for a non-root module of a main or only build in a composite build
  // <projectName-uniqualized-by-Gradle> for the root module of an included build
  // <projectName-uniqualized-by-Gradle>:gradle:path for a non-root module of an included build
  // NOTE: The project name uniqualization is performed by Gradle and may be version dependent. It should not be assumed to match
  //       any Gradle project name or any Gradle included build name.

  val isSourceSet = getExternalModuleType(this) == GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY
  val externalSystemId = ExternalSystemApiUtil.getExternalProjectId(this) ?: return null
  val gradlePath = getGradleIdentityPathOrNull(this) ?: return null
  return createGradleProjectPath(gradlePath, externalSystemId, isSourceSet, buildRootFolder)
}

@VisibleForTesting
fun createGradleProjectPath(
  gradlePath: String,
  externalSystemId: String,
  isSourceSet: Boolean,
  buildRootFolder: File
): GradleProjectPath? {
  val buildRoot = FileUtils.toSystemIndependentPath(buildRootFolder.path)

  return if (isSourceSet) {
    val sourceSetName = externalSystemId.substringAfterLast(':', "").nullize() ?: return null
    GradleSourceSetProjectPath(buildRoot, gradlePath, IdeModuleSourceSetImpl.wellKnownOrCreate(sourceSetName))
  }
  else {
    GradleHolderProjectPath(buildRoot, gradlePath)
  }
}

fun Module.getGradleProjectPath(): GradleProjectPath? {
  return CachedValuesManager.getManager(project).getCachedValue(this) {
    CachedValueProvider.Result.create(
      internalGetGradleProjectPath(),
      ProjectRootModificationTracker.getInstance(project)
    )
  }
}

fun Project.findModule(gradleProjectPath: GradleProjectPath): Module? {
  return CachedValuesManager.getManager(this).getCachedValue(this) {
    val moduleMap = ModuleManager.getInstance(this)
      .modules
      .mapNotNull {
        (it.getGradleProjectPath() ?: return@mapNotNull null) to it
      }
      .toMap()
    CachedValueProvider.Result.create(
      moduleMap,
      ProjectRootModificationTracker.getInstance(this)
    )
  }[gradleProjectPath]
}

fun GradleProjectPath.resolveIn(project: Project): Module? = project.findModule(this)
