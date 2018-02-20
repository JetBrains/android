/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.ide.common.repository.GradleVersion
import com.android.repository.Revision
import com.android.tools.idea.fd.InstantRunConfigurable
import com.android.tools.idea.fd.gradle.InstantRunGradleUtils
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.tools.idea.projectsystem.CapabilityStatus
import com.android.tools.idea.projectsystem.CapabilitySupported
import com.android.tools.idea.projectsystem.CapabilityUpgradeRequired
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

fun getInstantRunCapabilityStatus(module: Module): CapabilityStatus {
  val androidModel = AndroidModuleModel.get(module)
  return if (androidModel == null || InstantRunGradleUtils.modelSupportsInstantRun(androidModel))
    CapabilitySupported()
  else
    CapabilityUpgradeRequired()
}

/** Update versions relevant for Instant Run. Returns true if an upgrade was performed successfully. */
fun updateProjectToInstantRunTools(project: Project): Boolean {
  var pluginVersion = InstantRunGradleUtils.MINIMUM_GRADLE_PLUGIN_VERSION
  val pluginMinRecommendedVersion = GradleVersion.parse(AndroidPluginGeneration.ORIGINAL.latestKnownVersion)
  val gradleLatestVersion = GradleVersion.parse(SdkConstants.GRADLE_LATEST_VERSION)
  // Pick max version of "recommended Gradle plugin" and "minimum required for instant run"
  if (pluginMinRecommendedVersion > pluginVersion) {
    pluginVersion = pluginMinRecommendedVersion
  }

  // Update plugin version
  val updater = AndroidPluginVersionUpdater.getInstance(project)
  val result = updater.updatePluginVersion(pluginVersion, gradleLatestVersion)
  if (result.isPluginVersionUpdated && result.versionUpdateSuccess()) {
    // Should be at least 23.0.2
    var buildToolsVersion = "23.0.2"
    val sdk = AndroidSdks.getInstance().tryToChooseSdkHandler()
    val latestBuildTool = sdk.getLatestBuildTool(StudioLoggerProgressIndicator(InstantRunConfigurable::class.java), false)
    if (latestBuildTool != null) {
      val revision = latestBuildTool.revision
      if (revision > Revision.parseRevision(buildToolsVersion)) {
        buildToolsVersion = revision.toShortString()
      }
    }

    // Also update build files to set build tools version 23.0.2 or the latest build tool revision
    GradleUtil.setBuildToolsVersion(project, buildToolsVersion)

    // Also update Gradle wrapper version
    val gradleWrapper = GradleWrapper.find(project)
    if (gradleWrapper != null) {
      gradleWrapper.updateDistributionUrlAndDisplayFailure(SdkConstants.GRADLE_LATEST_VERSION)
      return true
    }
  }

  return false
}