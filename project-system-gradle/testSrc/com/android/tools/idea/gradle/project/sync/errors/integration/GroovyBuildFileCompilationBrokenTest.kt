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

import com.android.SdkConstants
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import org.junit.Test

class GroovyBuildFileCompilationBrokenTest: AbstractSyncFailureIntegrationTest() {

  private fun runSyncAndCheckFailure(
    preparedProject: PreparedTestProject,
    expectedErrorNodeNameVerifier: (String) -> Unit
  ) = runSyncAndCheckGeneralFailure(
    preparedProject = preparedProject,
    verifySyncViewEvents = { buildEvents ->
      // Expect single MessageEvent on Sync Output
      buildEvents.filterIsInstance<MessageEvent>().let { events ->
        expect.that(events).hasSize(1)
        events.firstOrNull()?.let {
          expectedErrorNodeNameVerifier(it.message)
          expect.that(it.group).isEqualTo("Other Messages")
        }
      }
      // Make sure no additional error build issue events are generated
      expect.that(buildEvents.filterIsInstance<BuildIssueEvent>()).isEmpty()
      expect.that(buildEvents.finishEventFailures()).isEmpty()
    },
    verifyFailureReported = {
      expect.that(it.gradleSyncFailure).isEqualTo(AndroidStudioEvent.GradleSyncFailure.GROOVY_COMPILATION_ERROR)
      expect.that(it.buildOutputWindowStats.buildErrorMessagesList.map { it.errorShownType })
        .containsExactly(BuildErrorMessage.ErrorType.UNKNOWN_ERROR_TYPE)
      expect.that(it.gradleSyncStats.printPhases()).isEqualTo("""
          FAILURE : SYNC_TOTAL/GRADLE_CONFIGURE_ROOT_BUILD
          FAILURE : SYNC_TOTAL
        """.trimIndent())
    },
  )

  @Test
  fun testBrokenGroovyCompilation1() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    preparedProject.root.resolve(SdkConstants.FN_BUILD_GRADLE).appendText("\n)(")

    runSyncAndCheckFailure(
      preparedProject = preparedProject,
      expectedErrorNodeNameVerifier = {
        expect.that(it).startsWith("Unexpected input: '(\"This is a simple application!\")")
      }
    )
  }

  @Test
  fun testBrokenGroovyCompilation2() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    preparedProject.root.resolve("app/" + SdkConstants.FN_BUILD_GRADLE).let {
      //This should result in '}throw new RuntimeException()' breaking compilation with missed space.
      it.appendText("throw new RuntimeException()")
    }

    runSyncAndCheckFailure(
      preparedProject = preparedProject,
      expectedErrorNodeNameVerifier = {
        expect.that(it).startsWith("Unexpected input: 'throw'")
      }
    )
  }
}