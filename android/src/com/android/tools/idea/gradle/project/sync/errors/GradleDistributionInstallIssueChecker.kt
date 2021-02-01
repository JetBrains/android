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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.updateUsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import org.gradle.StartParameter
import org.gradle.wrapper.PathAssembler
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

@JvmField val COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PREFIX = "Could not install Gradle distribution from "

class GradleDistributionInstallIssueChecker : GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = issueData.error.message ?: return null
    if (!message.startsWith(COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PREFIX)) return null

    // Log metrics.
    invokeLater {
      updateUsageTracker(issueData.projectPath, GradleSyncFailure.GRADLE_DISTRIBUTION_INSTALL_ERROR)
    }

    val buildIssueComposer = BuildIssueComposer(message)
    val wrapperConfiguration = GradleUtil.getWrapperConfiguration(issueData.projectPath)
    if (wrapperConfiguration != null) {

      val localDistribution =
        PathAssembler(StartParameter.DEFAULT_GRADLE_USER_HOME).getDistribution(wrapperConfiguration)
      var zipFile = localDistribution.zipFile
      if (zipFile.exists()) {
        try {
          zipFile = zipFile.canonicalFile
        } catch (e : Exception) {}

        buildIssueComposer.addDescription("The cached zip file ${zipFile} may be corrupted.")
        buildIssueComposer.addQuickFix(
          "Delete file and sync project", DeleteFileAndSyncQuickFix(zipFile, GradleSyncStats.Trigger.TRIGGER_QF_GRADLE_DISTRIBUTION_DELETED))
      }
    }

    return buildIssueComposer.composeBuildIssue()
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return failureCause.startsWith(COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PREFIX)
  }

  class DeleteFileAndSyncQuickFix(val file: File, private val syncTrigger: GradleSyncStats.Trigger) : BuildIssueQuickFix {
    override val id = "delete.file.and.sync"

    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
      val future = CompletableFuture<Any>()

      invokeLater {
        if (Messages.showYesNoDialog(project, "Are you sure you want to delete this file?\n\n" + file.path, "Delete File",
                                     null) == Messages.YES) {
          if (FileUtil.delete(file)) {
            GradleSyncInvoker.getInstance().requestProjectSync(project, syncTrigger)
          }
          else {
            Messages.showErrorDialog(project, "Could not delete " + file.path, "Delete File")
          }
        }
        future.complete(null)
      }
      return future
    }
  }
}