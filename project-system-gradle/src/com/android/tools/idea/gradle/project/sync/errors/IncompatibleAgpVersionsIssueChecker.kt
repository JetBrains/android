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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.tools.idea.gradle.project.sync.AndroidSyncException
import com.android.tools.idea.gradle.project.sync.AndroidSyncExceptionType
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.function.Consumer

class IncompatibleAgpVersionsIssueChecker: GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first
    if (rootCause !is AndroidSyncException) return null
    if (rootCause.type != AndroidSyncExceptionType.AGP_VERSIONS_MISMATCH) return null
    // Note: no need to report failure to SyncFailureUsageReporter as for AndroidSyncException
    // instances it is reported in AndroidGradleProjectResolver.
    val message = rootCause.message ?: return null
    val messageLines = message.lines()
    if (messageLines.isEmpty()) return null
    return BuildIssueComposer("${messageLines[0]}\n${messageLines[1]}").composeBuildIssue()
  }

  /**
   * This error is not thrown from the build but is passed back as model [com.android.tools.idea.gradle.project.sync.IdeAndroidSyncError]
   * recreated and rethrown in [com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver.populateProjectExtraModels].
   *
   * Because of this there is no actual error in build output, so we don't need to parse anything in this function.
   */
  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return false
  }
}