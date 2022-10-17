/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.gradle.model.IdeCompositeBuildMap
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

/**
 * A value representing a path to a Gradle project together with the location of the root Gradle build and the name of the included build
 * containing the project.
 */
data class BuildRelativeGradleProjectPath(
  /**
   * The build ID (directory containing the settings file) of the root build of this project.
   *
   * Note, this directory might be different from the root directory of the root project of the root build if the root project directory is
   * relocated.
   */
  val rootBuildId: File,

  /**
   * The name of the included build containing this project or ":" if this project belongs to the root build.
   */
  val buildName: String,

  /**
   * Returns the Gradle project path of the module (excluding the build name, if in an included build).
   */
  val gradleProjectPath: String
)

/**
 * Returns a string which can be used to refer to the given Gradle project when invoking Gradle at the level of [this.rootBuildId].
 */
fun BuildRelativeGradleProjectPath.buildNamePrefixedGradleProjectPath(): String {
  return when {
    buildName == ":" -> gradleProjectPath
    gradleProjectPath == ":" -> ":$buildName"
    else -> ":$buildName$gradleProjectPath"
  }
}

fun BuildRelativeGradleProjectPath.rootBuildPath(): String = toSystemIndependentName(rootBuildId.path)

/**
 * If the version of Gradle the project is synced with supports invoking tasks directly from included builds, finds the build at the root
 * of the composite and returns its location together with the name of an (included) build containing this project and the Gradle project
 * path to the project within this build. Otherwise, returns the location of the included build and the Gradle project path relative to the
 * included build.
 */
fun Module.getBuildAndRelativeGradleProjectPath(): BuildRelativeGradleProjectPath? {
  return compositeBuildMap().translateToBuildAndRelativeProjectPath(getGradleProjectPath() ?: return null)
}

interface CompositeBuildMap {
  fun buildIdToName(buildId: File): String
  fun buildNameToId(buildName: String): File
  val gradleSupportsDirectTaskInvocation: Boolean

  companion object {
    val EMPTY = IdeCompositeBuildMap.EMPTY.toCompositeBuildMap()
  }
}

fun IdeCompositeBuildMap.toCompositeBuildMap(): CompositeBuildMap {
  val byName = builds.associate { it.buildName to it.buildId }
  val byId = builds.associate { it.buildId to it.buildName }
  return object : CompositeBuildMap {
    override fun buildIdToName(buildId: File): String = byId[buildId] ?: error("Build (id='$buildId') not found")
    override fun buildNameToId(buildName: String): File = byName[buildName] ?: error("Build (name='$buildName') not found")
    override val gradleSupportsDirectTaskInvocation: Boolean
      get() = this@toCompositeBuildMap.gradleSupportsDirectTaskInvocation
  }
}

fun Module.findCompositeBuildMapModel(): IdeCompositeBuildMap {
  val linkedProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(this) ?: return IdeCompositeBuildMap.EMPTY

  val projectDataNode =
    ProjectDataManager.getInstance().getExternalProjectData(project, GradleConstants.SYSTEM_ID, linkedProjectPath)?.externalProjectStructure
      ?: return IdeCompositeBuildMap.EMPTY

  return find(projectDataNode, AndroidProjectKeys.IDE_COMPOSITE_BUILD_MAP)?.data ?: IdeCompositeBuildMap.EMPTY
}

internal fun Module.compositeBuildMap(): CompositeBuildMap {
  val gradleProjectPath = getGradleProjectPath() ?: return CompositeBuildMap.EMPTY
  val rootProjectGradleProjectPath = GradleHolderProjectPath(gradleProjectPath.buildRoot, ":")
  val rootModule = rootProjectGradleProjectPath.resolveIn(project) ?: error("Cannot find root module for $rootProjectGradleProjectPath")
  // We cache the map in the root module of each build.
  return CachedValuesManager.getManager(project).getCachedValue(rootModule, rootModule::createCompositeBuildMapCachedValue)
}

private fun Module.createCompositeBuildMapCachedValue(): CachedValueProvider.Result<CompositeBuildMap> {
  return CachedValueProvider.Result(
    findCompositeBuildMapModel().toCompositeBuildMap(),
    ProjectSyncModificationTracker.getInstance(project)
  )
}

fun CompositeBuildMap.translateToBuildAndRelativeProjectPath(projectPath: GradleProjectPath): BuildRelativeGradleProjectPath {
  return when (gradleSupportsDirectTaskInvocation) {
    false -> BuildRelativeGradleProjectPath(projectPath.buildRootDir, ":", projectPath.path)
    true -> {
      val rootBuildId = this.buildNameToId(":")
      val buildName = this.buildIdToName(projectPath.buildRootDir)
      BuildRelativeGradleProjectPath(rootBuildId, buildName, projectPath.path)
    }
  }
}
