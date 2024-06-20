/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors.integration

import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.externalSystem.issue.BuildIssueException

abstract class AbstractIssueCheckerIntegrationTest : AbstractSyncFailureIntegrationTest() {
  protected fun runSyncAndCheckBuildIssueFailure(
    preparedProject: PreparedTestProject,
    verifyBuildIssue: (BuildIssue) -> Unit,
    expectedFailureReported: AndroidStudioEvent.GradleSyncFailure,
    expectedPhasesReported: String?
  ) {
    runSyncAndCheckGeneralFailure(
      preparedProject = preparedProject,
      verifySyncViewEvents = { buildEvents ->
        // Make sure no additional error build events are generated
        expect.that(buildEvents.filterIsInstance<MessageEvent>()).isEmpty()
        expect.that(buildEvents.filterIsInstance<BuildIssueEvent>()).isEmpty()
        // This Failure is reported to SyncView via finish event failure result failures.
        buildEvents.filterIsInstance<FinishBuildEvent>().single().let { finishBuildEvent ->
          (finishBuildEvent.result as FailureResult).failures.let { failures ->
            expect.that(failures).hasSize(1)
            (failures.firstOrNull()?.error as? BuildIssueException)?.let {
               verifyBuildIssue(it.buildIssue)
            } ?: expect.fail("%s not found in %s", BuildIssueException::class.java.name, FinishBuildEvent::class.java.name)
          }
        }
      },
      verifyFailureReported = {
        expect.that(it.gradleSyncFailure).isEqualTo(expectedFailureReported)
        expect.that(it.buildOutputWindowStats.buildErrorMessagesList).isEmpty()
        if (expectedPhasesReported != null) expect.that(it.gradleSyncStats.printPhases()).isEqualTo(expectedPhasesReported)
      }
    )
  }
}