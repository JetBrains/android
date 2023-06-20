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
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.gradle.project.sync.AgpVersionIncompatible
import com.android.tools.idea.gradle.project.sync.AgpVersionTooNew
import com.android.tools.idea.gradle.project.sync.AgpVersionTooOld
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.project.sync.idea.issues.fetchIdeaProjectForGradleProject
import com.android.tools.idea.gradle.project.sync.idea.issues.updateUsageTracker
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.android.tools.idea.gradle.project.upgrade.performForcedPluginUpgrade
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

/**
 * IssueChecker to handle projects with incompatible (too old or mismatched preview) AGP versions.
 */
class AgpVersionNotSupportedIssueChecker: GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first
    val message = rootCause.message ?: ""
    if (message.isBlank()) return null

    val tooOldMatcher = AgpVersionTooOld.PATTERN.matcher(message)
    val incompatiblePreviewMatcher = AgpVersionIncompatible.PATTERN.matcher(message)
    val tooNewMatcher = AgpVersionTooNew.PATTERN.matcher(message)
    val (matcher, userMessage, url) = when {
      tooOldMatcher.find() -> Triple(tooOldMatcher, tooOldMatcher.group(0), TOO_OLD_URL)
      incompatiblePreviewMatcher.find() -> Triple(incompatiblePreviewMatcher, incompatiblePreviewMatcher.group(0), PREVIEW_URL)
      tooNewMatcher.find() -> Triple(tooNewMatcher, tooNewMatcher.group(0), TOO_NEW_URL)
      else -> return null
    }
    val version = AgpVersion.tryParse(matcher.group(1)) ?: return null

    logMetrics(issueData.projectPath)

    val buildIssueComposer = BuildIssueComposer(userMessage)

    if (matcher == tooNewMatcher) {
      return buildIssueComposer.apply {
        addQuickFix(
          "See Android Studio & AGP compatibility options.",
          OpenLinkQuickFix(url)
        )
      }.composeBuildIssue()
    }

    if (!AndroidGradleProjectResolver.shouldDisableForceUpgrades()) {
      fetchIdeaProjectForGradleProject(issueData.projectPath)?.let { project ->
        updateAndRequestSync(project, version)
      }
      buildIssueComposer.addQuickFix(AgpUpgradeQuickFix(version))
    }

    return buildIssueComposer.apply {
      addQuickFix(
        "See Android Studio & AGP compatibility options.",
        OpenLinkQuickFix(url)
      )
    }.composeBuildIssue()
  }

  private fun logMetrics(issueDataProjectPath: String) {
    invokeLater {
      updateUsageTracker(issueDataProjectPath, AndroidStudioEvent.GradleSyncFailure.OLD_ANDROID_PLUGIN)
    }
  }

  override fun consumeBuildOutputFailureMessage(
    message: String,
    failureCause: String,
    stacktrace: String?,
    location: FilePosition?,
    parentEventId: Any,
    messageConsumer: Consumer<in BuildEvent>
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
 * Hyperlink that triggers the showing of the AGP Upgrade Assistant dialog, letting the user
 * upgrade their Android Gradle plugin and Gradle versions.
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

/**
 * Helper method to trigger the forced upgrade prompt and then request a sync if it was successful.
 */
private fun updateAndRequestSync(project: Project, currentAgpVersion: AgpVersion, future: CompletableFuture<Unit>? = null) {
  AndroidExecutors.getInstance().diskIoThreadExecutor.execute {
    performForcedPluginUpgrade(project, currentAgpVersion)
    future?.complete(Unit)
  }
}