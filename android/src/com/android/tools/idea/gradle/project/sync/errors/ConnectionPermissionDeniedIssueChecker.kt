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

import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandler.updateUsageTracker
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import java.net.SocketException
import java.util.concurrent.CompletableFuture
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.CONNECTION_DENIED
import com.intellij.openapi.application.invokeLater


class ConnectionPermissionDeniedIssueChecker: GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = issueData.error.message ?: return null
    if (issueData.error !is SocketException || message.isEmpty() || !message.contains("Permission denied: connect")) return null

    // Log metrics.
    invokeLater {
      updateUsageTracker(issueData.projectPath, CONNECTION_DENIED)
    }

    val openLinkQuickFix = OpenLinkQuickFix("https://developer.android.com/studio/troubleshoot.html#project-sync")
    return object : BuildIssue {
      override val title: String = "Connection to the Internet denied."
      override val description: String = buildString {
        appendln(message)
        appendln("\n <a href=\"${openLinkQuickFix.id}\">More details (and potential fix)</a>")
      }
      override val quickFixes: List<BuildIssueQuickFix> = listOf(openLinkQuickFix)
      override fun getNavigatable(project: Project): Navigatable?  = null
    }
  }

  class OpenLinkQuickFix(val link: String) : BuildIssueQuickFix {
    override val id = "OPEN_MORE_DETAILS"
    override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
      BrowserUtil.browse(link)
      return CompletableFuture.completedFuture<Any>(null)
    }
  }
}