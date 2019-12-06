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
@file:JvmName("GradlePluginUpgrade")
package com.android.tools.idea.gradle.project.sync.setup.post.upgrade

import com.android.SdkConstants.GRADLE_LATEST_VERSION
import com.android.SdkConstants.GRADLE_PATH_SEPARATOR
import com.android.annotations.concurrency.Slow
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo.ARTIFACT_ID
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo.GROUP_ID
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.hyperlink.SearchInBuildFilesHyperlink
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages
import com.android.tools.idea.project.messages.MessageType.ERROR
import com.android.tools.idea.project.messages.SyncMessage
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ModalityState.NON_MODAL
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

private val LOG = Logger.getInstance("AndroidGradlePluginUpdates")

fun shouldForcePluginUpgrade(project: Project) : Boolean {
  val pluginInfo = project.findPluginInfo() ?: return false
  return shouldForcePluginUpgrade(pluginInfo)
}

@Slow
fun performForcedPluginUpgrade(project: Project) : Boolean {
  val pluginInfo = project.findPluginInfo() ?: return false
  val recommended = LatestKnownPluginVersionProvider.INSTANCE.get()
  LOG.info("Gradle model version: ${pluginInfo.pluginVersion}, recommended version for IDE: $recommended")

  return performForcedPluginUpgrade(project, pluginInfo)
}

@Slow
@VisibleForTesting
fun shouldForcePluginUpgrade(pluginInfo: AndroidPluginInfo) : Boolean {
  val recommended = GradleVersion.parse(pluginInfo.latestKnownPluginVersionProvider.get())
  return shouldPreviewBeForcedToUpgrade(recommended, pluginInfo.pluginVersion)
}

@Slow
@VisibleForTesting
fun performForcedPluginUpgrade(project: Project, pluginInfo: AndroidPluginInfo) : Boolean {
  val recommended = GradleVersion.parse(pluginInfo.latestKnownPluginVersionProvider.get())

  val syncState = GradleSyncState.getInstance(project)
  syncState.syncSucceeded() // Update the sync state before starting a new one.

  val upgradeAccepted = invokeAndWaitIfNeeded(NON_MODAL) {
    ForcedPluginPreviewVersionUpgradeDialog(project, pluginInfo).showAndGet()
  }

  if (upgradeAccepted) {
    // The user accepted the upgrade
    val versionUpdater = AndroidPluginVersionUpdater.getInstance(project)
    versionUpdater.updatePluginVersionAndSync(
      recommended,
      GradleVersion.parse(GRADLE_LATEST_VERSION),
      pluginInfo.pluginVersion
    )
  } else {

    // The user did not accept the upgrade
    val syncMessage = SyncMessage(
      SyncMessage.DEFAULT_GROUP,
      ERROR,
      "The project is using an incompatible version of the ${AndroidPluginInfo.DESCRIPTION}.",
      "Please update your project to use version $recommended."
    )
    val pluginName = GROUP_ID + GRADLE_PATH_SEPARATOR + ARTIFACT_ID
    syncMessage.add(SearchInBuildFilesHyperlink(pluginName))

    GradleSyncMessages.getInstance(project).report(syncMessage)
    syncState.syncFailed("Force plugin upgrade declined", null, null)
  }
  return true
}

@VisibleForTesting
fun shouldPreviewBeForcedToUpgrade(
  recommended: GradleVersion,
  current: GradleVersion?
) : Boolean {
  if (current?.previewType == null) return false
  // e.g recommended: 2.3.0-dev and current: 2.3.0-alpha1
  if (recommended.isSnapshot && current.compareIgnoringQualifiers(recommended) == 0) return false

  if (recommended.isAtLeast(2, 4, 0, "alpha", 8, false)) {
    // 2.4.0-alpha8 introduces many API changes that may break users' builds. Because of this, Studio will allow users to
    // switch to older previews of 2.4.0.
    if (current >= recommended) {
      // The plugin is newer or equal to 2.4.0-alpha8
      return false
    }

    // Allow recent RCs. For example, when using a 3.5 canary IDE, allow 3.4-rc as a Gradle
    // plugin, but not 3.3-rc or 3.4-beta.
    if (current.previewType == "rc" &&
        recommended.previewType != null &&
        current.major == recommended.major &&
        current.minor == recommended.minor - 1) {
      return false
    }

    val isOlderPluginAllowed = current.isPreview &&
                               current.major == 2 &&
                               current.minor == 4 &&
                               current < recommended
    return !isOlderPluginAllowed
  }

  return current < recommended
}

private fun Project.findPluginInfo() : AndroidPluginInfo? {
  val pluginInfo = AndroidPluginInfo.find(this)
  if (pluginInfo == null) {
    LOG.warn("Unable to obtain application's Android Project")
    return null
  }
  return pluginInfo
}