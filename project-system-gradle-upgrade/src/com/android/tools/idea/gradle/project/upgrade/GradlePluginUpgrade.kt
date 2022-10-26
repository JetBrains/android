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
@file:JvmName("GradlePluginUpgrade")
package com.android.tools.idea.gradle.project.upgrade

import com.android.SdkConstants
import com.android.SdkConstants.GRADLE_PLUGIN_NEXT_MINIMUM_VERSION
import com.android.annotations.concurrency.Slow
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.sync.setup.post.TimeBasedReminder
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.AFTER_MAXIMUM
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.BEFORE_MINIMUM
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.COMPATIBLE
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.DEPRECATED
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility.DIFFERENT_PREVIEW
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.FORCE
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.NO_UPGRADE
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.RECOMMEND
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.STRONGLY_RECOMMEND
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState.NON_MODAL
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.util.SystemProperties
import com.jetbrains.rd.util.first
import org.jetbrains.android.util.AndroidBundle
import java.util.concurrent.TimeUnit

private val LOG = Logger.getInstance(LOG_CATEGORY)

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

/** [upgrade] is true if we recommend; [strongly] if we should recommend and the recommendation should be strong */
data class Recommendation(val upgrade: Boolean, val strongly: Boolean)

/**
 * Checks to see if we should be recommending an upgrade of the Android Gradle Plugin.
 * [current] defaults to the value that is obtained from the [project]; if it can't be found, do not recommend.
 */
@Slow
fun shouldRecommendPluginUpgrade(project: Project): Recommendation {
  // If we don't know the current plugin version then we don't upgrade.
  val current = project.findPluginInfo()?.pluginVersion ?: return Recommendation(false, false)
  val latestKnown = AgpVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
  val published = IdeGoogleMavenRepository.getAgpVersions()
  return shouldRecommendPluginUpgrade(project, current, latestKnown, published)
}

@JvmOverloads
fun shouldRecommendPluginUpgrade(
  project: Project,
  current: AgpVersion,
  latestKnown: AgpVersion,
  published: Set<AgpVersion> = setOf()
): Recommendation {
  // Needed internally for development of Android support lib.
  if (SystemProperties.getBooleanProperty("studio.skip.agp.upgrade", false)) return Recommendation(false, false)

  if (!RecommendedUpgradeReminder(project).shouldAsk()) return Recommendation(false, false)
  return shouldRecommendUpgrade(current, latestKnown, published)
}

/**
 * Shows a notification balloon recommending that the user upgrade the version of the Android Gradle plugin.
 *
 * If they choose to accept this recommendation [performRecommendedPluginUpgrade] will show them a dialog and the option
 * to try and update the version automatically. If accepted this method will trigger a re-sync to pick up the new version.
 */
fun recommendPluginUpgrade(project: Project, current: AgpVersion, strongly: Boolean) {
  val existing = NotificationsManager
    .getNotificationsManager()
    .getNotificationsOfType(ProjectUpgradeNotification::class.java, project)

  if (existing.isEmpty()) {
    val notification = when (strongly) {
      false -> UpgradeSuggestion(
        AndroidBundle.message("project.upgrade.notification.title"), AndroidBundle.message("project.upgrade.notification.body", current))
      true -> DeprecatedAgpUpgradeWarning(
        AndroidBundle.message("project.upgrade.deprecated.notification.title"),
        AndroidBundle.message("project.upgrade.deprecated.notification.body", current, GRADLE_PLUGIN_NEXT_MINIMUM_VERSION)
      )
    }
    notification.notify(project)
  }
}

/**
 * Invokes the AGP Upgrade Assistant Tool Window, allowing the user to update the version of AGP used in their project.
 * If the [currentVersion] is null this method always returns false, with no action taken.
 */
@Slow
@JvmOverloads
fun performRecommendedPluginUpgrade(
  project: Project,
  currentVersion: AgpVersion? = project.findPluginInfo()?.pluginVersion,
  latestKnown: AgpVersion = AgpVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
) {
  if (currentVersion == null) return

  LOG.info("Gradle model version: $currentVersion, latest known version for IDE: $latestKnown")

  val published = IdeGoogleMavenRepository.getAgpVersions()
  val state = computeGradlePluginUpgradeState(currentVersion, latestKnown, published)

  LOG.info("Gradle upgrade state: $state")
  if (!setOf(RECOMMEND, STRONGLY_RECOMMEND).contains(state.importance)) return

  showAndInvokeAgpUpgradeRefactoringProcessor(project, currentVersion, state.target)
  return
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
fun shouldRecommendUpgrade(current: AgpVersion, latestKnown: AgpVersion, published: Set<AgpVersion> = setOf()) : Recommendation {
  computeGradlePluginUpgradeState(current, latestKnown, published).importance.let { importance ->
    return Recommendation(setOf(RECOMMEND, STRONGLY_RECOMMEND).contains(importance), importance == STRONGLY_RECOMMEND)
  }
}

// **************************************************************************
// ** Forced upgrades
// **************************************************************************

/**
 * Returns whether, given the [current] version of AGP and the [latestKnown] version to Studio (which should be the
 * version returned by [LatestKnownPluginVersionProvider] except for tests), we should consider the AGP version
 * compatible with the running IDE.  If the versions are incompatible, we will have caused sync to fail; in most cases we
 * will attempt to offer an upgrade, but some cases (e.g. a newer [current] than [latestKnown]) the user will be responsible
 * for action to get the project to a working state.
 */
fun versionsAreIncompatible(
  current: AgpVersion,
  latestKnown: AgpVersion
) : Boolean {
  return !setOf(COMPATIBLE, DEPRECATED).contains(computeAndroidGradlePluginCompatibility(current, latestKnown))
}

/**
 * Called when the AGP and Android Studio versions are mutually incompatible (and the AGP version is not newer than the latest supported
 * version of AGP in this Android Studio).  Pops up a modal dialog to offer the user an upgrade (as minimal as possible) to the version
 * of AGP used by the project.  The user may dismiss the dialog in order to make changes manually; if they leave the modal upgrade
 * flow without completing an upgrade, we report a Sync message noting the existing incompatibility.  Returns when the modal flow is
 * complete: any upgrade will have been scheduled but might not have completed by the time this returns.
 */
@Slow
fun performForcedPluginUpgrade(
  project: Project,
  currentPluginVersion: AgpVersion,
  newPluginVersion: AgpVersion = computeGradlePluginUpgradeState(
    currentPluginVersion,
    AgpVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get()),
    IdeGoogleMavenRepository.getAgpVersions()
  ).target
) {
  val upgradeAccepted = invokeAndWaitIfNeeded(NON_MODAL) {
    ForcedPluginPreviewVersionUpgradeDialog(project, currentPluginVersion, newPluginVersion).showAndGet()
  }

  if (upgradeAccepted) {
    // The user accepted the upgrade: show upgrade details and offer the action.
    // Note: we retrieve a RefactoringProcessorInstantiator as a project service for the convenience of tests.
    val refactoringProcessorInstantiator = project.getService(RefactoringProcessorInstantiator::class.java)
    val processor = refactoringProcessorInstantiator.createProcessor(project, currentPluginVersion, newPluginVersion)
    // Enable only the minimum number of processors for a forced upgrade
    processor.componentRefactoringProcessors.forEach { component ->
      component.isEnabled = component.necessity().let { it == MANDATORY_CODEPENDENT || it == MANDATORY_INDEPENDENT }
    }
    val runProcessor = refactoringProcessorInstantiator.showAndGetAgpUpgradeDialog(processor)
    if (runProcessor) {
      DumbService.getInstance(project).smartInvokeLater { processor.run() }
      // upgrade refactoring scheduled
      return
    }
  }
}

data class GradlePluginUpgradeState(
  val importance: Importance,
  val target: AgpVersion,
) {
  enum class Importance {
    NO_UPGRADE,
    RECOMMEND,
    STRONGLY_RECOMMEND,
    FORCE,
  }
}

fun computeGradlePluginUpgradeState(
  current: AgpVersion,
  latestKnown: AgpVersion,
  published: Set<AgpVersion>
): GradlePluginUpgradeState {
  val compatibility = computeAndroidGradlePluginCompatibility(current, latestKnown)
  when (compatibility) {
    BEFORE_MINIMUM -> {
      val minimum = AgpVersion.parse(SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION)
      val earliestStable = published
        .filter { !it.isPreview }
        .filter { it >= minimum }
        .filter { it <= latestKnown }
        .groupBy { AgpVersion(it.major, it.minor) }
        .minByOrNull { it.key }
        ?.value
        ?.maxOrNull()
      return GradlePluginUpgradeState(FORCE, earliestStable ?: latestKnown)
    }
    DIFFERENT_PREVIEW -> {
      val seriesAcceptableStable = published
        .filter { !it.isPreview }
        .filter { AgpVersion(it.major, it.minor) == AgpVersion(current.major, current.minor) }
        .filter { it <= latestKnown }
        .maxOrNull()
      // For the forced upgrade of a preview, we prefer the latest stable release in the same series as the preview, if one exists.  If
      // there is no such release, we have no option but to force an upgrade to the latest known version.  (This will happen, for example,
      // running a Canary Studio in series X+1 on a project using a Beta AGP from series X, until the Final AGP and Studio for series
      // X are released.)
      return GradlePluginUpgradeState(FORCE, seriesAcceptableStable ?: latestKnown)
    }
    AFTER_MAXIMUM -> return GradlePluginUpgradeState(FORCE, latestKnown)
    COMPATIBLE, DEPRECATED -> Unit
  }

  if (current >= latestKnown) return GradlePluginUpgradeState(NO_UPGRADE, current)
  val recommendationStrength = when (compatibility) {
    DEPRECATED -> STRONGLY_RECOMMEND
    COMPATIBLE -> RECOMMEND
    else -> throw IllegalStateException("Unreachable: forced upgrade state previously handled")
  }

  if (!current.isPreview || current.previewType == "rc") {
    val acceptableStables = published
      .asSequence()
      .filter { !it.isPreview }
      .filter { it > current }
      .filter { it <= latestKnown }
      // We use the fact that groupBy preserves order both of keys and of entries in the list value.
      .sorted()
      .groupBy { AgpVersion(it.major, it.minor) }
      .asSequence()
      .groupBy { it.key.major }

    if (acceptableStables.isEmpty()) {
      // The first two cases here are unlikely, but theoretically possible, if somehow our published information is out of date
      return when {
        // If our latestKnown is stable, recommend it.
        !latestKnown.isPreview -> GradlePluginUpgradeState(recommendationStrength, latestKnown)
        latestKnown.previewType == "rc" -> GradlePluginUpgradeState(recommendationStrength, latestKnown)
        // Don't recommend upgrades from stable to preview.
        else -> GradlePluginUpgradeState(NO_UPGRADE, current)
      }
    }

    if (!acceptableStables.containsKey(current.major)) {
      // We can't upgrade to a new version of our current series, but there are upgrade targets (acceptableStables is not empty).  We
      // must be at the end of a major series, so recommend the latest compatible in the next major series.
      return GradlePluginUpgradeState(recommendationStrength, acceptableStables.first().value.last().value.last())
    }

    val currentSeriesCandidates = acceptableStables[current.major]!!
    val nextSeriesCandidates = acceptableStables.keys.firstOrNull { it > current.major }?.let { acceptableStables[it]!! }

    if (currentSeriesCandidates.maxOf { it.key } == AgpVersion(current.major, current.minor)) {
      // We have a version of the most recent series of our current major, though not the most up-to-date version of that.  If there's a
      // later stable series, recommend upgrading to that, otherwise recommend upgrading our point release.
      return GradlePluginUpgradeState(recommendationStrength, (nextSeriesCandidates ?: currentSeriesCandidates).last().value.last())
    }

    // Otherwise, we must have newer minor releases from our current major series.  Recommend upgrading to the latest minor release.
    return GradlePluginUpgradeState(recommendationStrength, currentSeriesCandidates.last().value.last())
  }
  else if (current.previewType == "alpha" || current.previewType == "beta") {
    if (latestKnown.isSnapshot) {
      // If latestKnown is -dev and current is in the same series, leave it alone.
      if (latestKnown.compareIgnoringQualifiers(current) == 0) return GradlePluginUpgradeState(NO_UPGRADE, current)
      // If latestKnown is -dev and current is a preview from an earlier series, recommend an upgrade.
      return GradlePluginUpgradeState(recommendationStrength, latestKnown)
    }
    throw IllegalStateException("Unreachable: forced upgrade state previously handled")
  }
  else {
    // Current is a snapshot.
    throw IllegalStateException("Unreachable: forced upgrade state previously handled")
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

internal fun releaseNotesUrl(v: AgpVersion): String = when {
  v.isPreview -> "https://developer.android.com/studio/preview/features#android_gradle_plugin_${v.major}${v.minor}"
  else -> "https://developer.android.com/studio/releases/gradle-plugin#${v.major}-${v.minor}-0"
}