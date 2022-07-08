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
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find
import com.intellij.openapi.module.Module
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

/**
 * A value representing a path to a Gradle project together with the location of the root Gradle build and the name of the included build
 * containing the project.
 */
data class RootBuildRelativeGradleProjectPath(
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
fun RootBuildRelativeGradleProjectPath.buildNamePrefixedGradleProjectPath(): String {
  return when {
    buildName == ":" -> gradleProjectPath
    gradleProjectPath == ":" -> ":$buildName"
    else -> ":$buildName$gradleProjectPath"
  }
}

/**
 * Finds the build at the root of the composite and returns its location together with the name of an (included) build containing this
 * project and the Gradle project path to the project within this build.
 */
fun Module.getRootBuildRelativeGradleProjectPath(): RootBuildRelativeGradleProjectPath? {
  return compositeBuildMap().translateToRootBuildRelative(getGradleProjectPath() ?: return null)
}

private interface CompositeBuildMap {
  fun buildIdToName(buildId: File): String
  fun buildNameToId(buildName: String): File

  companion object {
    val EMPTY = IdeCompositeBuildMap.EMPTY.toCompositeBuildMap()
  }
}

private fun IdeCompositeBuildMap.toCompositeBuildMap(): CompositeBuildMap {
  val byName = builds.associate { it.buildName to it.buildId }
  val byId = builds.associate { it.buildId to it.buildName }
  return object : CompositeBuildMap {
    override fun buildIdToName(buildId: File): String = byId[buildId] ?: error("Build (id='$buildId') not found")
    override fun buildNameToId(buildName: String): File = byName[buildName] ?: error("Build (name='$buildName') not found")
  }
}

fun Module.compositeBuildMapModel(): IdeCompositeBuildMap {
  val linkedProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(this) ?: return IdeCompositeBuildMap.EMPTY

  @Suppress("UnstableApiUsage")
  val projectDataNode =
    ExternalSystemApiUtil.findProjectData(project, GradleConstants.SYSTEM_ID, linkedProjectPath) ?: return IdeCompositeBuildMap.EMPTY

  return find(projectDataNode, AndroidProjectKeys.IDE_COMPOSITE_BUILD_MAP)?.data ?: IdeCompositeBuildMap.EMPTY
}

private fun Module.compositeBuildMap(): CompositeBuildMap {
  val gradleProjectPath = getGradleProjectPath() ?: return CompositeBuildMap.EMPTY
  val rootProjectGradleProjectPath = GradleHolderProjectPath(gradleProjectPath.buildRoot, ":")
  val rootModule = rootProjectGradleProjectPath.resolveIn(project) ?: error("Cannot find root module for $rootProjectGradleProjectPath")
  // We cache the map in the root module of each build.
  return CachedValuesManager.getManager(project).getCachedValue(rootModule) {
    CachedValueProvider.Result(compositeBuildMapModel().toCompositeBuildMap(), ProjectSyncModificationTracker.getInstance(project))
  }
}

private fun CompositeBuildMap.translateToRootBuildRelative(projectPath: GradleProjectPath): RootBuildRelativeGradleProjectPath {
  val rootBuildId = this.buildNameToId(":")
  val buildName = this.buildIdToName(projectPath.buildRootDir)
  return RootBuildRelativeGradleProjectPath(rootBuildId, buildName, projectPath.path)
}
