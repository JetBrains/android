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

import com.android.SdkConstants
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.build.BuildConsoleUtils
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.MessageEvent
import org.junit.Test

class NoVariantsFoundSyncFailureTest : AbstractSyncFailureIntegrationTest() {


  @Test
  fun testNoVariantsFoundFailure() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val buildFile = preparedProject.root.resolve("app").resolve(SdkConstants.FN_BUILD_GRADLE)
    buildFile.appendText("""

      android {
          variantFilter { variant ->
              setIgnore(true)
          }
      }
    """.trimIndent())

    runSyncAndCheckGeneralFailure(
      preparedProject = preparedProject,
      verifySyncViewEvents = { _, buildEvents ->
        // Make sure no additional error build events are generated
        expect.that(buildEvents.filterIsInstance<MessageEvent>()).isEmpty()
        expect.that(buildEvents.filterIsInstance<BuildIssueEvent>()).isEmpty()
        // This Failure is reported to SyncView via finish event failure result failures.
        buildEvents.filterIsInstance<FinishBuildEvent>().single().let { finishBuildEvent ->
          (finishBuildEvent.result as FailureResult).failures.let { failures ->
            expect.that(failures).hasSize(1)
            failures.firstOrNull()?.let {
              expect.that(BuildConsoleUtils.getMessageTitle(it.message!!)).isEqualTo("No variants found for ':app'")
            }
          }
        }
      },
      verifyFailureReported = {
        expect.that(it.gradleSyncFailure).isEqualTo(AndroidStudioEvent.GradleSyncFailure.ANDROID_SYNC_NO_VARIANTS_FOUND)
        expect.that(it.buildOutputWindowStats.buildErrorMessagesList).isEmpty()
        // This failure is thrown during model building, but it is passed with the models instead of failing the build,
        // Thus we have successful build but failed Sync.
        expect.that(it.gradleSyncStats.printPhases()).isEqualTo("""
          SUCCESS : SYNC_TOTAL/GRADLE_CONFIGURE_ROOT_BUILD
          SUCCESS : SYNC_TOTAL/GRADLE_RUN_MAIN_TASKS/GRADLE_RUN_WORK
          SUCCESS : SYNC_TOTAL/GRADLE_RUN_MAIN_TASKS
          FAILURE : SYNC_TOTAL
        """.trimIndent())
        Truth.assertThat(it.gradleFailureDetails.toTestString()).isEqualTo("""
          failure {
            error {
              exception: com.android.tools.idea.gradle.project.sync.AndroidSyncException
                at: [0]com.android.tools.idea.gradle.project.sync.IdeAndroidModelsKt#ideAndroidSyncErrorToException
            }
          }
        """.trimIndent())
      }
    )
  }
}