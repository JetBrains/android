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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.flags.StudioFlags.DISABLE_FORCED_UPGRADES
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo.ARTIFACT_ID
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo.GROUP_ID
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.sync.hyperlink.SearchInBuildFilesHyperlink
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages
import com.android.tools.idea.gradle.project.sync.setup.post.TimeBasedReminder
import com.android.tools.idea.project.messages.MessageType.ERROR
import com.android.tools.idea.project.messages.SyncMessage
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState.NON_MODAL
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.util.SystemProperties
import java.util.concurrent.TimeUnit

private val LOG = Logger.getInstance("AndroidGradlePluginUpdates")

// **************************************************************************
// ** Recommended upgrades
// **************************************************************************

class RecommendedUpgradeReminder(
  project: Project
) : TimeBasedReminder(project, "recommended.upgrade", TimeUnit.DAYS.toMillis(1)) {
  var doNotAskForVersion: String?
    get() =  PropertiesComponent.getInstance(project).getValue("$settingsPropertyRoot.do.not.ask.for.version")
    set(value) = PropertiesComponent.getInstance(project).setValue("$settingsPropertyRoot.do.not.ask.for.version", value)

  override fun shouldAsk(currentTime: Long): Boolean {
    val pluginInfo = project.findPluginInfo() ?: return false
    val gradleVersion = pluginInfo.pluginVersion ?: return false
    return doNotAskForVersion != gradleVersion.toString() && super.shouldAsk(currentTime)
  }
}

/**
 * Checks to see if we should be recommending an upgrade of the Android Gradle Plugin.
 *
 * Returns true if we should recommend an upgrade, false otherwise. We recommend an upgrade if any of the following conditions are met:
 * 1 - If the user has never been shown the upgrade (for that version) and the conditions in [shouldRecommendUpgrade] return true.
 * 2 - If the user picked "Remind me tomorrow" and a day has passed.
 *
 * [pluginInfo] should only be overwritten to inject information for tests.
 */
@Slow
@JvmOverloads
fun shouldRecommendPluginUpgrade(project: Project, pluginInfo: AndroidPluginInfo? = project.findPluginInfo()) : Boolean {
  if (!RecommendedUpgradeReminder(project).shouldAsk()) return false
  val current = pluginInfo?.pluginVersion ?: return false
  val recommended = GradleVersion.parse(pluginInfo.latestKnownPluginVersionProvider.get())
  return shouldRecommendUpgrade(recommended, current)
}

/**
 * Shows a [RecommendedPluginVersionUpgradeDialog] to the user prompting them to upgrade to a newer version of
 * the Android Gradle Plugin and Gradle. The information about which versions to upgrade to is contained within
 * the [pluginInfo].
 *
 * If the user accepted the upgrade then the file are modified and the project is re-synced. This method uses
 * [AndroidPluginVersionUpdater] in order to perform these operations.
 *
 * Returns true if the upgrade was performed and the project was synced, false otherwise.
 *
 * Note: The [dialogFactory] and [pluginInfo] arguments should not be used outside of tests. It should only be used to mock the
 * result of the dialog or inject version information.
 *
 */
@Slow
@JvmOverloads
fun performRecommendedPluginUpgrade(
  project: Project,
  pluginInfo: AndroidPluginInfo? = project.findPluginInfo(),
  dialogFactory: RecommendedPluginVersionUpgradeDialog.Factory = RecommendedPluginVersionUpgradeDialog.Factory()
) : Boolean {
  val currentVersion = pluginInfo?.pluginVersion ?: return false
  val recommendedVersion = GradleVersion.parse(pluginInfo.latestKnownPluginVersionProvider.get())

  LOG.info("Gradle model version: $currentVersion, recommended version for IDE: $recommendedVersion, current, recommended")

  val userAccepted = invokeAndWaitIfNeeded(NON_MODAL) {
    val updateDialog = dialogFactory.create(project, currentVersion, recommendedVersion)
    updateDialog.showAndGet()
  }

  if (userAccepted) {
    // The user accepted the upgrade
    val updater = AndroidPluginVersionUpdater.getInstance(project)

    val latestGradleVersion = GradleVersion.parse(GRADLE_LATEST_VERSION)
    val updateResult = updater.updatePluginVersionAndSync(recommendedVersion, latestGradleVersion, currentVersion, false)
    if (updateResult.versionUpdateSuccess()) {
      // plugin version updated and a project sync was requested. No need to continue.
      return true
    }
  }

  return false
}

@VisibleForTesting
fun shouldRecommendUpgrade(recommended: GradleVersion, current: GradleVersion) : Boolean {
  // Do not upgrade to snapshot version when major versions are same.
  if (recommended.isSnapshot && current.compareIgnoringQualifiers(recommended) == 0) return false
  // Upgrade from preview to non-snapshot preview version is handled by force upgrade.
  if (current.isPreview && recommended.isPreview && !recommended.isSnapshot) return false
  // Stable to new preview version. e.g 3.3.0 to 3.4.0-alpha01
  if (!current.isPreview && recommended.isPreview && current.compareIgnoringQualifiers(recommended) < 0) return true
  return current < recommended
}

// **************************************************************************
// ** Forced upgrades
// **************************************************************************

/**
 * Returns whether or not the given [current] version requires that the user be force to
 * upgrade their Android Gradle Plugin.
 *
 * If a [project] is given warnings for disabled upgrades are emitted.
 *
 * [recommended] should only be overwritten to inject information for tests.
 */
fun shouldForcePluginUpgrade(
  project: Project?,
  current: GradleVersion?,
  recommended: GradleVersion = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
  ) : Boolean {
  // We don't care about forcing upgrades when running unit tests.
  if (ApplicationManager.getApplication().isUnitTestMode) return false
  // Or when the skip upgrades property is set.
  if (SystemProperties.getBooleanProperty("studio.skip.agp.upgrade", false)) return false
  // Or when the StudioFlag is set (only available internally).
  if (StudioFlags.DISABLE_FORCED_UPGRADES.get()) {
    if (project != null) {
      displayForceUpdatesDisabledMessage(project)
    }
    return false
  }
  
  // Now we can check the actual version information.
  return shouldForcePluginUpgrade(current, recommended)
}

/**
 * This method should ONLY be used in tests.
 */
@VisibleForTesting
fun shouldForcePluginUpgrade(
  current: GradleVersion?,
  recommended: GradleVersion
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

/**
 * Prompts the user to perform a required upgrade to ensure that the versions of the Android Gradle Plugin is
 * compatible with the running version of studio.
 *
 * We offer the user two ways to correct this, either we can attempt to edit the files automatically or the user can opt
 * to manually fix the problem.
 *
 * Returns true if the upgrade was performed automatically, false otherwise.
 */
@Slow
fun performForcedPluginUpgrade(
  project: Project,
  currentPluginVersion: GradleVersion?,
  newPluginVersion: GradleVersion = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
) : Boolean {
  val upgradeAccepted = invokeAndWaitIfNeeded(NON_MODAL) {
    ForcedPluginPreviewVersionUpgradeDialog(project, currentPluginVersion).showAndGet()
  }

  if (upgradeAccepted) {
    // The user accepted the upgrade
    val versionUpdater = AndroidPluginVersionUpdater.getInstance(project)
    versionUpdater.updatePluginVersionAndSync(
      newPluginVersion,
      GradleVersion.parse(GRADLE_LATEST_VERSION),
      currentPluginVersion,
      true
    )
  } else {
    // The user did not accept the upgrade
    val syncMessage = SyncMessage(
      SyncMessage.DEFAULT_GROUP,
      ERROR,
      "The project is using an incompatible version of the ${AndroidPluginInfo.DESCRIPTION}.",
      "Please update your project to use version $newPluginVersion."
    )
    val pluginName = GROUP_ID + GRADLE_PATH_SEPARATOR + ARTIFACT_ID
    syncMessage.add(SearchInBuildFilesHyperlink(pluginName))

    GradleSyncMessages.getInstance(project).report(syncMessage)
    return false
  }
  return true
}

fun displayForceUpdatesDisabledMessage(project: Project) {
  // Show a warning as a reminder that errors seen may be due to this option.
  val msg = "Forced upgrades are disabled, errors seen may be due to incompatibilities between " +
            "the Android Gradle Plugin and the version of Android Studio.\nTo re-enable forced updates " +
            "please go to 'Tools > Internal Actions > Edit Studio Flags' and set " +
            "'${DISABLE_FORCED_UPGRADES.displayName}' to 'Off'."
  val notification = AGP_UPGRADE_NOTIFICATION_GROUP.createNotification(msg, MessageType.WARNING)
  notification.notify(project)
}

private fun Project.findPluginInfo() : AndroidPluginInfo? {
  val pluginInfo = AndroidPluginInfo.find(this)
  if (pluginInfo == null) {
    LOG.warn("Unable to obtain application's Android Project")
    return null
  }
  return pluginInfo
}