/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.project.sync.errors.ApplyGradlePluginQuickFix
import com.android.tools.idea.gradle.project.sync.errors.GetGradleSettingsQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.FixAndroidGradlePluginVersionQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Truth
import com.google.common.truth.Truth.*
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GradleDslMethodNotFoundIssueCheckerTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()


  private val usageTracker = TestUsageTracker(VirtualTimeScheduler())

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(usageTracker)
  }

  @After
  fun cleanUp() {
    usageTracker.close()
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun testCheckIssueWithMethodNotFoundInSettingsFile() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val settingsFile = preparedProject.root.resolve(SdkConstants.FN_SETTINGS_GRADLE)
    settingsFile.writeText("incude ':app'")

    runSyncAndCheckFailure(
      preparedProject,
      { buildIssue ->
        assertThat(buildIssue.title).isEqualTo("Gradle Sync issues.")
        assertThat(buildIssue.description).startsWith("Gradle DSL method not found: 'incude()'")
        // Verify quickFixes.
        assertThat(buildIssue.quickFixes).hasSize(1)
        val quickFix = buildIssue.quickFixes[0]
        assertThat(quickFix).isInstanceOf(OpenFileAtLocationQuickFix::class.java)
        assertThat((quickFix as OpenFileAtLocationQuickFix).myFilePosition.file.path).isEqualTo(settingsFile.path)
      },
      AndroidStudioEvent.GradleSyncFailure.DSL_METHOD_NOT_FOUND
    )
  }

  @Test
  fun testCheckIssueWithMethodNotFoundInBuildFile() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val buildFile = preparedProject.root.resolve(SdkConstants.FN_BUILD_GRADLE)
    buildFile.appendText("\nabdd()")

    runSyncAndCheckFailure(
      preparedProject,
      { buildIssue ->
        assertThat(buildIssue.title).isEqualTo("Gradle Sync issues.")
        assertThat(buildIssue.description).startsWith("Gradle DSL method not found: 'abdd()'")
        assertThat(buildIssue.description).contains("Your project may be using a version of the Android Gradle plug-in that does not contain the " +
                                                    "method (e.g. 'testCompile' was added in 1.1.0).")
        // Verify quickFixes.
        assertThat(buildIssue.quickFixes).hasSize(3)
        assertThat(buildIssue.quickFixes[0]).isInstanceOf(FixAndroidGradlePluginVersionQuickFix::class.java)
        assertThat(buildIssue.quickFixes[1]).isInstanceOf(GetGradleSettingsQuickFix::class.java)
        assertThat(buildIssue.quickFixes[2]).isInstanceOf(ApplyGradlePluginQuickFix::class.java)
      },
      AndroidStudioEvent.GradleSyncFailure.DSL_METHOD_NOT_FOUND
    )
  }

  fun runSyncAndCheckFailure(
    preparedProject: PreparedTestProject,
    verifyBuildIssue: (BuildIssue) -> Unit,
    expectedFailureReported: AndroidStudioEvent.GradleSyncFailure
    ) {
    var capturedException: Exception? = null
    val buildEvents = mutableListOf<BuildEvent>()
    preparedProject.open(
      updateOptions = {
        it.copy(
          verifyOpened = { project ->
            assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult())
              .isEqualTo(ProjectSystemSyncManager.SyncResult.FAILURE)
          },
          syncExceptionHandler = { e: Exception ->
            capturedException = e
          },
          syncViewEventHandler = { buildEvent -> buildEvents.add(buildEvent) }
        )
      }
    ) {  }

    val buildIssue = (capturedException as BuildIssueException).buildIssue
    verifyBuildIssue(buildIssue)

    // Make sure no additional error build events are generated
    assertThat(buildEvents.filterIsInstance<MessageEvent>()).isEmpty()

    val event = usageTracker.usages
      .single { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }

    assertThat(event.studioEvent.gradleSyncFailure).isEqualTo(expectedFailureReported)
  }
}