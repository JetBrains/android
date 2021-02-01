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
package com.android.tools.idea.gradle.project.sync.idea.issues

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor.FULL_SYNC_KEY
import com.android.tools.idea.gradle.project.sync.AndroidSyncException
import com.android.tools.idea.gradle.project.upgrade.ForcedPluginPreviewVersionUpgradeDialog
import com.android.tools.idea.gradle.project.upgrade.performForcedPluginUpgrade
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_AGP_VERSION_UPDATED
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import java.util.concurrent.CompletableFuture

/**
 * Exception thrown when the version of the Android Gradle plugin is not supported by this version of studio.
 */
class AgpUpgradeRequiredException(
  val project: Project?,
  val modelVersion: GradleVersion
) : AndroidSyncException()

/**
 *  Checks to see if an [AgpUpgradeRequiredException] has been thrown, if it has prompts the user to update their
 *  Android Gradle plugin (and Gradle) version to the latest ones.
 *
 *  This checker also emits a message along with a link (to re-trigger the prompt) to the build output window to
 *  let the user know that something went wrong.
 */
class AgpUpgradeRequiredIssueChecker : GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    if (issueData.error !is AgpUpgradeRequiredException) return null

    val modelVersion = (issueData.error as AgpUpgradeRequiredException).modelVersion
    val quickfix = AgpUpgradeQuickFix(modelVersion)

    // TODO: Consult UX and see if we can remove this dialog auto-triggering.
    val project = (issueData.error as AgpUpgradeRequiredException).project
    // TODO(b/159995302): this check is intended to mean "is this a sync under Android Studio's control", as opposed to syncs
    //  that might be triggered by some other process (e.g. from the platform's project import).  At the moment, there are
    //  circumstances where we get multiple syncs on open/import, and we should ideally interrupt the user's flow by showing
    //  a modal dialog only once.
    if (project?.getUserData(FULL_SYNC_KEY) != null) {
      updateAndRequestSync(project, modelVersion)
    }

    return object : BuildIssue {
      override val title: String = "Android Gradle plugin upgrade required"
      override val description: String = buildString {
        appendln("The project is using an incompatible version ($modelVersion) of the ${AndroidPluginInfo.DESCRIPTION}.")
        appendln("<a href=\"${quickfix.id}\">Upgrade to the latest version</a>")
      }
      override val quickFixes: List<BuildIssueQuickFix> = listOf(quickfix)
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }
}

/**
 * Hyperlink that triggered the showing of the [ForcedPluginPreviewVersionUpgradeDialog] letting the user
 * upgrade there Android Gradle plugin and Gradle versions.
 */
private class AgpUpgradeQuickFix(val currentAgpVersion: GradleVersion) : BuildIssueQuickFix {
  override val id: String = "android.gradle.plugin.forced.update"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Unit>()
    updateAndRequestSync(project, currentAgpVersion, future)
    return future
  }
}

/**
 * Helper method to trigger the forced upgrade prompt and then request a sync if it was successful.
 */
private fun updateAndRequestSync(project: Project, currentAgpVersion: GradleVersion, future: CompletableFuture<Unit>? = null) {
  AndroidExecutors.getInstance().ioThreadExecutor.execute {
    val success = performForcedPluginUpgrade(project, currentAgpVersion)
    if (success) {
      val request = GradleSyncInvoker.Request(TRIGGER_AGP_VERSION_UPDATED)
      GradleSyncInvoker.getInstance().requestProjectSync(project, request)
    }
    future?.complete(Unit)
  }
}
