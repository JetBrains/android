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
package com.android.tools.idea.gradle.project.upgrade

import com.android.SdkConstants
import com.android.SdkConstants.GRADLE_PATH_SEPARATOR
import com.android.annotations.concurrency.Slow
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.flags.StudioFlags.DISABLE_FORCED_UPGRADES
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo.ARTIFACT_ID
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo.GROUP_ID
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.sync.hyperlink.SearchInBuildFilesHyperlink
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages
import com.android.tools.idea.gradle.project.sync.setup.post.TimeBasedReminder
import com.android.tools.idea.gradle.project.upgrade.ForcePluginUpgradeReason.NO_FORCE
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.FORCE
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.NO_UPGRADE
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.RECOMMEND
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.android.tools.idea.project.messages.MessageType.ERROR
import com.android.tools.idea.project.messages.SyncMessage
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState.NON_MODAL
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.util.SystemProperties
import org.jetbrains.android.util.AndroidBundle
import java.util.concurrent.TimeUnit

private val LOG = Logger.getInstance("Upgrade Assistant")
val AGP_UPGRADE_NOTIFICATION_GROUP = NotificationGroup("Android Gradle Upgrade Notification", NotificationDisplayType.STICKY_BALLOON, true)

// **************************************************************************
// ** Recommended upgrades
// **************************************************************************

class RecommendedUpgradeReminder(
  project: Project
) : TimeBasedReminder(project, "recommended.upgrade", TimeUnit.DAYS.toMillis(1)) {
  var doNotAskForVersion: String?
    get() =  PropertiesComponent.getInstance(project).getValue("$settingsPropertyRoot.do.not.ask.for.version")
    set(value) = PropertiesComponent.getInstance(project).setValue("$settingsPropertyRoot.do.not.ask.for.version", value)

  @Slow
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
 * [current] defaults to the value that is obtained from the [project], if it can't be found, false is returned.
 */
@Slow
fun shouldRecommendPluginUpgrade(project: Project): Boolean {
  // If we don't know the current plugin version then we don't upgrade.
  val current = project.findPluginInfo()?.pluginVersion ?: return false
  val latestKnown = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
  val published = IdeGoogleMavenRepository.getVersions("com.android.tools.build", "gradle")
  return shouldRecommendPluginUpgrade(project, current, latestKnown, published)
}

@JvmOverloads
fun shouldRecommendPluginUpgrade(
  project: Project,
  current: GradleVersion,
  latestKnown: GradleVersion,
  published: Set<GradleVersion> = setOf()
): Boolean {
  // Needed internally for development of Android support lib.
  if (SystemProperties.getBooleanProperty("studio.skip.agp.upgrade", false)) return false

  if (!RecommendedUpgradeReminder(project).shouldAsk()) return false
  return shouldRecommendUpgrade(current, latestKnown, published)
}

/**
 * Shows a notification balloon recommending that the user upgrade the version of the Android Gradle plugin.
 *
 * If they choose to accept this recommendation [performRecommendedPluginUpgrade] will show them a dialog and the option
 * to try and update the version automatically. If accepted this method will trigger a re-sync to pick up the new version.
 */
fun recommendPluginUpgrade(project: Project) {
  val existing = NotificationsManager
    .getNotificationsManager()
    .getNotificationsOfType(ProjectUpgradeNotification::class.java, project)

  if (existing.isEmpty()) {
    val listener = NotificationListener { notification, _ ->
      notification.expire()
      ApplicationManager.getApplication().executeOnPooledThread { performRecommendedPluginUpgrade(project) }
    }

    val notification = ProjectUpgradeNotification(
      AndroidBundle.message("project.upgrade.notification.title"), AndroidBundle.message("project.upgrade.notification.body"), listener)
    notification.notify(project)
  }
}

/**
 * Shows a [RecommendedPluginVersionUpgradeDialog] to the user prompting them to upgrade to a newer version of
 * the Android Gradle Plugin and Gradle. If the [currentVersion] is null this method always returns false, with
 * no action taken.
 *
 * If the user accepted the upgrade then the file are modified and the project is re-synced. This method uses
 * [AndroidPluginVersionUpdater] in order to perform these operations.
 *
 * Returns true if the project should be synced, false otherwise.
 *
 * Note: The [dialogFactory] argument should not be used outside of tests. It should only be used to mock the
 * result of the dialog.
 *
 */
@Slow
@JvmOverloads
fun performRecommendedPluginUpgrade(
  project: Project,
  currentVersion: GradleVersion? = project.findPluginInfo()?.pluginVersion,
  latestKnown: GradleVersion = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get()),
  dialogFactory: RecommendedPluginVersionUpgradeDialog.Factory = RecommendedPluginVersionUpgradeDialog.Factory()
) : Boolean {
  if (currentVersion == null) return false

  LOG.info("Gradle model version: $currentVersion, latest known version for IDE: $latestKnown")

  val published = IdeGoogleMavenRepository.getVersions("com.android.tools.build", "gradle")
  val state = computeGradlePluginUpgradeState(currentVersion, latestKnown, published)

  LOG.info("Gradle upgrade state: $state")
  if (state.importance != RECOMMEND) return false

  val userAccepted = invokeAndWaitIfNeeded(NON_MODAL) {
    val updateDialog = dialogFactory.create(project, currentVersion, state.target)
    updateDialog.showAndGet()
  }

  if (userAccepted) {
    // The user accepted the upgrade
    showAndInvokeAgpUpgradeRefactoringProcessor(project, currentVersion, state.target)
  }

  return false
}

// TODO(b/174543899): this is too weak; it doesn't catch modifications to:
//  - the root project's build.gradle[.kts]
//  - gradle-wrapper.properties
//  - gradle properties files
//  - build-adjacent files (e.g. proguard files, AndroidManifest.xml for the change namespacing R classes)
internal fun isCleanEnoughProject(project: Project): Boolean {
  ModuleManager.getInstance(project).modules.forEach { module ->
    val gradleFacet = GradleFacet.getInstance(module) ?: return@forEach
    val buildFile = gradleFacet.gradleModuleModel?.buildFile ?: return@forEach
    when (FileStatusManager.getInstance(project).getStatus(buildFile)) {
      FileStatus.NOT_CHANGED -> return@forEach
      else -> return false
    }
  }
  return true
}

@VisibleForTesting
@JvmOverloads
fun shouldRecommendUpgrade(current: GradleVersion, latestKnown: GradleVersion, published: Set<GradleVersion> = setOf()) : Boolean {
  return computeGradlePluginUpgradeState(current, latestKnown, published).importance == RECOMMEND
}

class ProjectUpgradeNotification(title: String, content: String, listener: NotificationListener)
  : Notification(AGP_UPGRADE_NOTIFICATION_GROUP.displayId, title, content, NotificationType.INFORMATION) {
    init {
      setListener(listener)
    }
  }

fun expireProjectUpgradeNotifications(project: Project?) {
  NotificationsManager
    .getNotificationsManager()
    .getNotificationsOfType(ProjectUpgradeNotification::class.java, project)
    .forEach { it.expire() }
}

// **************************************************************************
// ** Forced upgrades
// **************************************************************************

/**
 * Returns whether, given the [current] version of AGP and the [latestKnown] version to upgrade to (which should be the
 * version returned by [LatestKnownPluginVersionProvider] except for tests), we should force a plugin upgrade to that
 * recommended version.
 */
fun versionsShouldForcePluginUpgrade(
  current: GradleVersion,
  latestKnown: GradleVersion
) : Boolean {
  return computeForcePluginUpgradeReason(current, latestKnown) != NO_FORCE
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
  currentPluginVersion: GradleVersion,
  newPluginVersion: GradleVersion = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
) : Boolean {
  val upgradeAccepted = invokeAndWaitIfNeeded(NON_MODAL) {
    ForcedPluginPreviewVersionUpgradeDialog(project, currentPluginVersion).showAndGet()
  }

  if (upgradeAccepted) {
    // The user accepted the upgrade
    val assistantInvoker = project.getService(AssistantInvoker::class.java)
    val processor = assistantInvoker.createProcessor(project, currentPluginVersion, newPluginVersion)
    val runProcessor = assistantInvoker.showAndGetAgpUpgradeDialog(processor)
    if (runProcessor) {
      DumbService.getInstance(project).smartInvokeLater { processor.run() }
    }
    return false
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

data class GradlePluginUpgradeState(
  val importance: Importance,
  val target: GradleVersion,
) {
  enum class Importance {
    NO_UPGRADE,
    RECOMMEND,
    FORCE,
  }
}

fun computeGradlePluginUpgradeState(
  current: GradleVersion,
  latestKnown: GradleVersion,
  published: Set<GradleVersion>
): GradlePluginUpgradeState {
  when (computeForcePluginUpgradeReason(current, latestKnown)) {
    ForcePluginUpgradeReason.MINIMUM -> {
      val minimum = GradleVersion.parse(SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION)
      val earliestStable = published
        .filter { !it.isPreview }
        .filter { it >= minimum }
        .filter { it <= latestKnown }
        .groupBy { GradleVersion(it.major, it.minor) }
        .minByOrNull { it.key }
        ?.value
        ?.maxOrNull()
      return GradlePluginUpgradeState(FORCE, earliestStable ?: latestKnown)
    }
    ForcePluginUpgradeReason.PREVIEW -> {
      val seriesAcceptableStable = published
        .filter { !it.isPreview }
        .filter { GradleVersion(it.major, it.minor) == GradleVersion(current.major, current.minor) }
        .filter { it <= latestKnown }
        .maxOrNull()
      // For the forced upgrade of a preview, we prefer the latest stable release in the same series as the preview, if one exists.  If
      // there is no such release, we have no option but to force an upgrade to the latest known version.  (This will happen, for example,
      // running a Canary Studio in series X+1 on a project using a Beta AGP from series X, until the Final AGP and Studio for series
      // X are released.)
      return GradlePluginUpgradeState(FORCE, seriesAcceptableStable ?: latestKnown)
    }
    NO_FORCE -> Unit
  }

  if (current >= latestKnown) return GradlePluginUpgradeState(NO_UPGRADE, current)
  if (!current.isPreview || current.previewType == "rc") {
    // If our latestKnown is stable, recommend it.
    if (!latestKnown.isPreview || latestKnown.previewType == "rc") return GradlePluginUpgradeState(RECOMMEND, latestKnown)
    // Otherwise, look for a newer published stable.
    val laterStable = published.filter { !it.isPreview }.filter { it > current }.maxOrNull()
                      ?: return GradlePluginUpgradeState(NO_UPGRADE, current)
    return GradlePluginUpgradeState(RECOMMEND, laterStable)
  }
  else if (current.previewType == "alpha" || current.previewType == "beta") {
    if (latestKnown.isSnapshot) {
      // If latestKnown is -dev and current is in the same series, leave it alone.
      if (latestKnown.compareIgnoringQualifiers(current) == 0) return GradlePluginUpgradeState(NO_UPGRADE, current)
      // If latestKnown is -dev and current is a preview from an earlier series, recommend an upgrade.
      return GradlePluginUpgradeState(RECOMMEND, latestKnown)
    }
    throw IllegalStateException("Unreachable: handled by computeForcePluginUpgradeReason")
  }
  else {
    // Current is a snapshot.
    throw IllegalStateException("Unreachable: handled by computeForcePluginUpgradeReason")
  }
}

fun AndroidPluginInfo.maybeRecommendPluginUpgrade(project: Project) {
  this.pluginVersion?.let { currentAgpVersion ->
    val latestKnown = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
    executeOnPooledThread {
      val published = IdeGoogleMavenRepository.getVersions("com.android.tools.build", "gradle")
      if (shouldRecommendPluginUpgrade(project, currentAgpVersion, latestKnown, published)) recommendPluginUpgrade(project)
    }
  }
}

@Slow
fun Project.findPluginInfo() : AndroidPluginInfo? {
  val pluginInfo = AndroidPluginInfo.find(this)
  if (pluginInfo == null) {
    LOG.warn("Unable to obtain application's Android Project")
    return null
  }
  return pluginInfo
}

internal fun releaseNotesUrl(v: GradleVersion): String = when {
  v.isPreview -> "https://developer.android.com/studio/preview/features#android_gradle_plugin_${v.major}${v.minor}"
  else -> "https://developer.android.com/studio/releases/gradle-plugin#${v.major}-${v.minor}-0"
}