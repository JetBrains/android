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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.fetchIdeaProjectForGradleProject
import com.android.tools.idea.gradle.project.sync.quickFixes.FixAndroidGradlePluginVersionQuickFix
import com.google.common.annotations.VisibleForTesting
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.function.Consumer

class NdkToolchainMissingABIIssueChecker: GradleIssueChecker {
  private val ERROR_MESSAGE = "No toolchains found in the NDK toolchains folder for ABI with prefix: "
  private val VALID_ABIS = listOf("mips64el-linux-android", "mipsel-linux-android")

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first.message ?: return null
    if (!message.startsWith(ERROR_MESSAGE)) return null
    val buildIssueComposer = BuildIssueComposer(message)
    val valid = VALID_ABIS.stream().anyMatch { message.endsWith(it) }
    if (valid && !isArtifactVersionOver3dot0(getAndroidPluginArtifactModel(issueData.projectPath))) {
      buildIssueComposer.addDescription("This version of the NDK may be incompatible with the Android Gradle plugin version 3.0 or older.\n\n" +
                                 "Please use plugin version 3.1 or newer.")
      buildIssueComposer.addQuickFix(
        "Upgrade plugin to version ${GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())} and sync project",
        FixAndroidGradlePluginVersionQuickFix(null, null))
    }
    return buildIssueComposer.composeBuildIssue()
  }

  /**
   * Checks to see if the given model has a version above 3.0
   *
   * @param artifactModel the model to check, if null false is returned
   * @return whether the artifactModel if not null and has a version above 3.0
   */
  private fun isArtifactVersionOver3dot0(artifactModel: ArtifactDependencyModel?): Boolean {
    if (artifactModel == null) return false
    return isVersionOver3dot0(artifactModel.version().toString())
  }

  @VisibleForTesting
  fun isVersionOver3dot0(version: String): Boolean {
    val versionOnly = GradleCoordinate.parseVersionOnly(version)
    return versionOnly.majorVersion > 3 || versionOnly.majorVersion == 3 && versionOnly.minorVersion > 0
  }

  /**
   * Attempts to find the artifact model that represents the Android Gradle Plugin in the projects root build file.
   * @param project the project to use
   * @return the artifact model if found, null otherwise
   */
  private fun getAndroidPluginArtifactModel(projectPath: String): ArtifactDependencyModel? {
    // Fetch the IDEA project that contains the gradle project.
    val ideaProject = fetchIdeaProjectForGradleProject(projectPath) ?: return null


    val projectBuildModel = ProjectBuildModel.getOrLog(ideaProject) ?: return null
    val rootModel = projectBuildModel.projectBuildModel ?: return null
    val dependencyModels = rootModel.buildscript().dependencies().artifacts()
    return dependencyModels.stream().filter { dependency -> isAndroidPlugin(dependency) }.findFirst().orElse(null)
  }

  /**
   * Checks to see if the given [ArtifactDependencyModel] represents the Android Gradle Plugin.
   *
   * @param artifactModel the model to check
   * @return whether or not artifactModel represents the Android Gradle Plugin
   */
  private fun isAndroidPlugin(artifactModel: ArtifactDependencyModel): Boolean {
    return artifactModel.group().toString() == "com.android.tools.build" && artifactModel.name().toString() == "gradle"
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return failureCause.startsWith(ERROR_MESSAGE)
  }
}