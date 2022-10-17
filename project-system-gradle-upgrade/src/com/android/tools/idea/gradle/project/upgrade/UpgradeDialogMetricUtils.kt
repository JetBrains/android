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
package com.android.tools.idea.gradle.project.upgrade

import com.android.SdkConstants.GRADLE_LATEST_VERSION
import com.android.ide.common.repository.AgpVersion
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.tools.idea.stats.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradlePluginUpgradeDialogStats
import com.intellij.openapi.project.Project

fun recordUpgradeDialogEvent(
  project: Project,
  oldPluginVersion: AgpVersion?,
  newPluginVersion: AgpVersion,
  userAction: GradlePluginUpgradeDialogStats.UserAction
) = recordUpgradeDialogEvent(project, oldPluginVersion.toString(), newPluginVersion.toString(), userAction)

fun recordUpgradeDialogEvent(
  project: Project,
  oldPluginVersion: String?,
  newPluginVersion: String,
  userAction: GradlePluginUpgradeDialogStats.UserAction
) {
  val dialogStats = GradlePluginUpgradeDialogStats.newBuilder()
    .setRecommendedGradleVersion(GRADLE_LATEST_VERSION)
    .setRecommendedAndroidGradlePluginVersion(newPluginVersion)
    .setUserAction(userAction)

  if (oldPluginVersion != null) {
    dialogStats.currentAndroidGradlePluginVersion = oldPluginVersion
  }

  val oldGradleVersion = GradleWrapper.find(project)?.gradleVersion
  if (oldGradleVersion != null) {
    dialogStats.currentGradleVersion = oldGradleVersion
  }

  UsageTracker.log(AndroidStudioEvent.newBuilder()
                     .setCategory(AndroidStudioEvent.EventCategory.PROJECT_SYSTEM)
                     .setKind(AndroidStudioEvent.EventKind.GRADLE_PLUGIN_UPGRADE_DIALOG)
                     .setGradlePluginUpgradeDialog(
                       dialogStats
                     ).withProjectId(project))
}
