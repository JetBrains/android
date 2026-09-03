/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors.runsGradleErrors

import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import com.intellij.openapi.project.Project
import java.io.File

abstract class AbstractIssueCheckerIntegrationTest : AbstractSyncFailureIntegrationTest() {
  protected fun runSyncAndCheckBuildIssueFailure(
    preparedProject: PreparedTestProject,
    overrideGradleJdkPath: File? = null,
    verifyBuildIssue: (Project, BuildIssue) -> Unit,
    expectedFailureReported: AndroidStudioEvent.GradleSyncFailure,
    expectedPhasesReported: String?,
    expectedFailureDetailsString: String?,
  ) =
    runSyncAndCheckBuildIssuesFailure(
      preparedProject = preparedProject,
      overrideGradleJdkPath = overrideGradleJdkPath,
      verifyBuildIssues = { p, issues ->
        if (issues.size > 1) {
          expect.fail("There is more than a single failure issue:\n${issues.descriptions()}")
        }
        issues.verifyIssueSafely(0, { verifyBuildIssue(p, it) })
      },
      expectedFailuresReported = listOf(expectedFailureReported),
      expectedPhasesReported = expectedPhasesReported,
      expectedFailureDetailsString = expectedFailureDetailsString,
    )

  protected fun runSyncAndCheckBuildIssuesFailure(
    preparedProject: PreparedTestProject,
    overrideGradleJdkPath: File? = null,
    verifyBuildIssues: (Project, List<BuildIssue?>) -> Unit,
    expectedFailuresReported: List<AndroidStudioEvent.GradleSyncFailure>,
    expectedPhasesReported: String?,
    expectedFailureDetailsString: String?,
  ) {
    runSyncAndCheckGeneralFailure(
      preparedProject = preparedProject,
      overrideGradleJdkPath = overrideGradleJdkPath,
      verifySyncViewEvents = { project, buildEvents ->
        // Make sure no additional error build events are generated
        expect.that(buildEvents.filterIsInstance<MessageEvent>()).isEmpty()
        expect.that(buildEvents.filterIsInstance<BuildIssueEvent>()).isEmpty()
        // This Failure is reported to SyncView via finish event failure result failures.
        buildEvents.filterIsInstance<FinishBuildEvent>().single().let { finishBuildEvent ->
          (finishBuildEvent.result as FailureResult).failures.let { failures ->
            val failureIssues: List<BuildIssue?> = failures.map { it.error as BuildIssueException }.distinct().flatMap { it.buildIssues }
            if (failureIssues.isEmpty()) {
              expect.fail("%s not found in %s", BuildIssueException::class.java.name, FinishBuildEvent::class.java.name)
            } else {
              verifyBuildIssues(project, failureIssues)
            }
          }
        }
      },
      verifyFailureReported = {
        expect.that(it.gradleFailureDetails.detectedGradleSyncFailuresList).isEqualTo(expectedFailuresReported)
        expect.that(it.buildOutputWindowStats.buildErrorMessagesList).isEmpty()
        if (expectedPhasesReported != null) expect.that(it.gradleSyncStats.printPhases()).isEqualTo(expectedPhasesReported)
        if (expectedFailureDetailsString != null)
          Truth.assertThat(it.gradleFailureDetails.toTestString()).isEqualTo(expectedFailureDetailsString)
      },
    )
  }

  fun List<BuildIssue?>.descriptions() =
    this.joinToString(separator = "\n---\n") { it?.let { i -> "${i.title}:\n${i.description}" } ?: "<null>" }

  fun List<BuildIssue?>.verifyIssueSafely(issueIndex: Int, verifyBuildIssue: (BuildIssue) -> Unit) {
    if (!this.indices.contains(issueIndex)) {
      expect.fail("Requested issue index $issueIndex is out of bounds ${this.indices}. Issues: ${descriptions()}")
    } else {
      this[issueIndex]?.let { verifyBuildIssue(it) } ?: expect.fail("Issue is null.")
    }
  }
}
