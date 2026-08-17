/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.gradle.plugin.AgpVersions
import com.android.tools.idea.gradle.project.sync.AgpVersionIncompatible
import com.android.tools.idea.gradle.project.sync.AgpVersionTooNew
import com.android.tools.idea.gradle.project.sync.AgpVersionTooOld
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.project.sync.idea.issues.fetchIdeaProjectForGradleProject
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.android.tools.idea.gradle.project.upgrade.performForcedPluginUpgrade
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toCanonicalPath
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData

/** IssueChecker to handle projects with incompatible (too old or mismatched preview) AGP versions. */
class AgpVersionNotSupportedIssueChecker : GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = issueData.failure.rootCause
    val message = rootCause.message ?: ""
    val rootCauseClassName = rootCause.className ?: return null
    if (message.isBlank()) return null
    if (!rootCauseClassName.contains("com.android.tools.idea.gradle.project.sync.AndroidSyncException")) return null
    // Note: no need to report failure to SyncFailureUsageReporter as for AndroidSyncException
    // instances it is reported in AndroidGradleProjectResolver.

    val (agpVersion, userMessage, url) =
      when {
        AgpVersionTooOld.ALWAYS_PRESENT_STRINGS.all { message.contains(it) } -> {
          val tooOldMatcher = AgpVersionTooOld.PATTERN.matcher(message)
          if (!tooOldMatcher.find()) return null
          Triple(tooOldMatcher.group(1), tooOldMatcher.group(0), TOO_OLD_URL)
        }
        AgpVersionIncompatible.ALWAYS_PRESENT_STRINGS.all { message.contains(it) } -> {
          val incompatiblePreviewMatcher = AgpVersionIncompatible.pattern(AgpVersions.latestKnown).matcher(message)
          if (!incompatiblePreviewMatcher.find()) return null
          Triple(incompatiblePreviewMatcher.group(1), incompatiblePreviewMatcher.group(0), PREVIEW_URL)
        }
        AgpVersionTooNew.ALWAYS_PRESENT_STRINGS.all { message.contains(it) } -> {
          val tooNewMatcher = AgpVersionTooNew.pattern(AgpVersions.latestKnown).matcher(message)
          if (!tooNewMatcher.find()) return null
          Triple(tooNewMatcher.group(1), tooNewMatcher.group(0), TOO_NEW_URL)
        }
        else -> return null
      }
    val buildIssueComposer = BuildIssueComposer(userMessage)

    return buildIssueComposer.run {
      if (AgpVersionIncompatible.ALWAYS_PRESENT_STRINGS.all { message.contains(it) }) {
        AgpVersion.tryParse(agpVersion)?.let { version ->
          if (!AndroidGradleProjectResolver.shouldDisableForceUpgrades()) {
            fetchIdeaProjectForGradleProject(issueData.projectRoot.toCanonicalPath())?.let { project ->
              updateAndRequestSync(project, version)
            }
          }
          addQuickFix(AgpUpgradeQuickFix(version))
        }
      }
      // JetBrains patch
      if (IdeInfo.getInstance().isAndroidStudio) {
        addQuickFix("See Android Studio & AGP compatibility options.", OpenLinkQuickFix(url))
      }
      else {
        enableFutureAgpVersionSupportQuickFix(AgpVersionTooNew.ALWAYS_PRESENT_STRINGS.all { message.contains(it) })
      }
      composeBuildIssue()
    }
  }

  override fun consumeBuildOutputFailureMessage(
    message: String,
    failureCause: String,
    stacktrace: String?,
    location: FilePosition?,
    parentEventId: Any,
    messageConsumer: Consumer<in BuildEvent>,
  ): Boolean {
    return AgpVersionTooOld.ALWAYS_PRESENT_STRINGS.all { failureCause.contains(it) } ||
      AgpVersionIncompatible.ALWAYS_PRESENT_STRINGS.all { failureCause.contains(it) } ||
      AgpVersionTooNew.ALWAYS_PRESENT_STRINGS.all { failureCause.contains(it) }
  }

  companion object {
    private const val TOO_OLD_URL = "https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility"
    private const val PREVIEW_URL = "https://developer.android.com/studio/preview/features#agp-previews"
    private const val TOO_NEW_URL = "https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility"
  }
}

/**
 * Hyperlink that triggers the showing of the AGP Upgrade Assistant dialog, letting the user upgrade their Android Gradle plugin and Gradle
 * versions.
 */
class AgpUpgradeQuickFix(val currentAgpVersion: AgpVersion) : DescribedBuildIssueQuickFix {
  override val id: String = "android.gradle.plugin.forced.update"
  override val description: String = "Upgrade to a supported version"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Unit>()
    updateAndRequestSync(project, currentAgpVersion, future)
    return future
  }
}

/** Helper method to trigger the forced upgrade prompt and then request a sync if it was successful. */
private fun updateAndRequestSync(project: Project, currentAgpVersion: AgpVersion, future: CompletableFuture<Unit>? = null) {
  AndroidExecutors.getInstance().diskIoThreadExecutor.execute {
    performForcedPluginUpgrade(project, currentAgpVersion)
    future?.complete(Unit)
  }
}
