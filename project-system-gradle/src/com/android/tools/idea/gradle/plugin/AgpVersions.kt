/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.plugin

import com.android.Version
import com.android.annotations.concurrency.Slow
import com.android.ide.common.gradle.Component
import com.android.ide.common.repository.AgpVersion
import com.android.ide.common.repository.MavenRepositories
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility
import com.android.tools.idea.gradle.project.upgrade.computeAndroidGradlePluginCompatibility
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.ui.GuiTestingService
import com.android.tools.idea.util.StudioPathManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.nio.file.Files
import com.android.ide.common.gradle.Version as GradleVersion

object AgpVersions {
  private val LOG: Logger
    get() = Logger.getInstance("#com.android.tools.idea.gradle.plugin.AgpVersions")

  private val ANDROID_GRADLE_PLUGIN_VERSION = AgpVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION)
  private val LAST_STABLE_ANDROID_GRADLE_PLUGIN_VERSION = AgpVersion.parseStable(Version.LAST_STABLE_ANDROID_GRADLE_PLUGIN_VERSION)

  private val AGP_APP_PLUGIN_MARKER = Component(
    "com.android.application", "com.android.application.gradle.plugin",
    GradleVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION))

  @JvmStatic
  val studioFlagOverride: AgpVersion?
    get() {
      val override = StudioFlags.AGP_VERSION_TO_USE.get()
      if (override.isEmpty()) return null
      if (override.equals("stable", true) || !IdeInfo.getInstance().isAndroidStudio) {
        LOG.info(
          "Android Gradle Plugin version overridden to latest stable version $LAST_STABLE_ANDROID_GRADLE_PLUGIN_VERSION by Studio flag ${StudioFlags.AGP_VERSION_TO_USE.id}=stable")
        return LAST_STABLE_ANDROID_GRADLE_PLUGIN_VERSION
      }
      val version = AgpVersion.tryParse(override) ?: throw IllegalStateException(
        "Invalid value '$override' for Studio flag ${StudioFlags.AGP_VERSION_TO_USE.id}. Expected Android Gradle plugin version (e.g. '8.0.2') or 'stable'")
      LOG.info(
        "Android Gradle Plugin version overridden to custom version $version by Studio flag ${StudioFlags.AGP_VERSION_TO_USE.id}=$override")
      return version
    }

  @JvmStatic
  val newProject: AgpVersion
    get() {
      // Allow explicit override by the studio flag
      studioFlagOverride?.let { return it }

      // When running from sources allow fallback to the latest stable if AGP has not been built locally
      if (StudioPathManager.isRunningFromSources() && ApplicationManager.getApplication() != null && !GuiTestingService.isInTestingMode()) {
        val repoPaths = EmbeddedDistributionPaths.getInstance().findAndroidStudioLocalMavenRepoPaths()
        for (repoPath in repoPaths) {
          if (Files.isDirectory(MavenRepositories.getArtifactDirectory(repoPath.toPath(), AGP_APP_PLUGIN_MARKER))) {
            return ANDROID_GRADLE_PLUGIN_VERSION // Found locally built AGP
          }
        }
        LOG.info(
          "Android Gradle plugin $ANDROID_GRADLE_PLUGIN_VERSION not locally built, " +
          "falling back to latest stable version ${Version.LAST_STABLE_ANDROID_GRADLE_PLUGIN_VERSION}. " +
          "${if(repoPaths.isEmpty()) "(no injected repos)" else "(searched injected repos: ${repoPaths.joinToString(File.pathSeparator)}"})")
        return LAST_STABLE_ANDROID_GRADLE_PLUGIN_VERSION // No locally built AGP exists, use stable version

      }
      // In packaged studio and for tests, use the AGP that was built alongside Studio
      return ANDROID_GRADLE_PLUGIN_VERSION
    }

  /** The highest known Android Gradle plugin version. Usually just the version that was built alongside this version of Studio */
  @JvmStatic
  val latestKnown: AgpVersion get() {
    return studioFlagOverride?.takeIf { it > ANDROID_GRADLE_PLUGIN_VERSION } ?: ANDROID_GRADLE_PLUGIN_VERSION
  }

  @Slow
  fun getAvailableVersions(): Set<AgpVersion> {
    return IdeGoogleMavenRepository.getAgpVersions().union(getDevelopmentLocalRepoVersions())
  }

  /**
   * Returns the list of versions to show in the new project wizard for development versions of Android Studio.
   *
   * The returned set contains latest version from each series which is supported by Studio, in descending order.
   *
   * Should not be called on the UI thread as [getAvailableVersions] may hit the network and do file I/O to check for new versions.
   */
  @Slow
  fun getNewProjectWizardVersions(): Set<AgpVersion> {
    return getNewProjectWizardVersions(latestKnown = latestKnown, availableVersions = getAvailableVersions())
  }

  @VisibleForTesting
  fun getNewProjectWizardVersions(latestKnown: AgpVersion, availableVersions: Set<AgpVersion>): Set<AgpVersion> {
    val include = setOf(AndroidGradlePluginCompatibility.COMPATIBLE, AndroidGradlePluginCompatibility.DEPRECATED)
    var minOfCurrentSeries = AgpVersion(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
    val recommended = mutableListOf<AgpVersion>()
    availableVersions.sortedDescending().forEach { version ->
      // Go from latest first, and include latest from each series that is compatible
      if (version < minOfCurrentSeries &&
          include.contains(computeAndroidGradlePluginCompatibility(version, latestKnown)) ) {
        minOfCurrentSeries = if (version.isSnapshot) {
          // Treat -dev as special case, so also include the latest release version from the current series, if present.
          version
        } else {
          // Exclude all older versions from the current series
          AgpVersion.parse(version.toString().substringBefore("-") + "-alpha01")
        }
        recommended.add(version)
      }
    }
    return recommended.toSet()
  }

  @Slow
  private fun getDevelopmentLocalRepoVersions(): List<AgpVersion> {
    return EmbeddedDistributionPaths.getInstance().findAndroidStudioLocalMavenRepoPaths().asSequence()
      .flatMap { MavenRepositories.getAllVersions(it.toPath(), AGP_APP_PLUGIN_MARKER.module) }
      .mapNotNullTo(mutableListOf()) { AgpVersion.tryParse(it.toString()) }
  }
}
