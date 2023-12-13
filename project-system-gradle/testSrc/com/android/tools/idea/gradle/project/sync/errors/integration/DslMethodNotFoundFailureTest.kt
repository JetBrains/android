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
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.openapi.externalSystem.model.LocationAwareExternalSystemException
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DslMethodNotFoundFailureTest {

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

  private fun runSyncAndCheckFailure(
    preparedProject: PreparedTestProject,
    expectedErrorNodeNameVerifier: (String) -> Unit
  ) {
    var capturedException: Exception? = null
    val buildEvents = mutableListOf<BuildEvent>()
    preparedProject.open(
      updateOptions = {
        it.copy(
          verifyOpened = { project ->
            Truth.assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult())
              .isEqualTo(ProjectSystemSyncManager.SyncResult.FAILURE)
          },
          syncExceptionHandler = { e: Exception ->
            capturedException = e
          },
          syncViewEventHandler = { buildEvent -> buildEvents.add(buildEvent) }
        )
      }
    ) { }

    Truth.assertThat(capturedException).isInstanceOf(LocationAwareExternalSystemException::class.java)

    expectedErrorNodeNameVerifier(buildEvents.filterIsInstance<MessageEvent>().single().message)

    val event = usageTracker.usages
      .single { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }

    Truth.assertThat(event.studioEvent.gradleSyncFailure).isEqualTo(AndroidStudioEvent.GradleSyncFailure.DSL_METHOD_NOT_FOUND)
  }

  @Test
  fun testCheckIssueWithMethodNotFoundInSettingsFile() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val settingsFile = preparedProject.root.resolve(SdkConstants.FN_SETTINGS_GRADLE)
    settingsFile.writeText("incude ':app'")

    runSyncAndCheckFailure(
      preparedProject = preparedProject,
      expectedErrorNodeNameVerifier = {
        Truth.assertThat(it).isEqualTo(
          "Could not find method incude() for arguments [:app] on settings 'project' of type org.gradle.initialization.DefaultSettings")
      }
    )
  }

  @Test
  fun testMethodNotFoundInBuildFileRoot() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val buildFile = preparedProject.root.resolve(SdkConstants.FN_BUILD_GRADLE)
    buildFile.appendText("\nabdd()")

    runSyncAndCheckFailure(
      preparedProject = preparedProject,
      expectedErrorNodeNameVerifier = {
        Truth.assertThat(it).isEqualTo(
          "Could not find method abdd() for arguments [] on root project 'project' of type org.gradle.api.Project")
      }
    )
  }

  @Test
  fun testPropertyNotFoundInBuildFileRoot() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val buildFile = preparedProject.root.resolve(SdkConstants.FN_BUILD_GRADLE)
    buildFile.appendText("\nabdd = \"abc\"")

    runSyncAndCheckFailure(
      preparedProject = preparedProject,
      expectedErrorNodeNameVerifier = {
        Truth.assertThat(it).isEqualTo("Could not set unknown property 'abdd' for root project 'project' of type org.gradle.api.Project")
      }
    )
  }

  @Test
  fun testPropertyNotFoundForReading() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val buildFile = preparedProject.root.resolve(SdkConstants.FN_BUILD_GRADLE)
    buildFile.appendText("\nabdd")

    runSyncAndCheckFailure(
      preparedProject = preparedProject,
      expectedErrorNodeNameVerifier = {
        Truth.assertThat(it).isEqualTo("Could not get unknown property 'abdd' for root project 'project' of type org.gradle.api.Project")
      }
    )
  }

  @Test
  fun testMethodNotFoundInBuildFileAndroidSection() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val buildFile = preparedProject.root.resolve("app/" + SdkConstants.FN_BUILD_GRADLE)
    buildFile.appendText("\nandroid { abdd { } }")

    runSyncAndCheckFailure(
      preparedProject = preparedProject,
      expectedErrorNodeNameVerifier = {
        // Contains '[build_amf2rqavq1o9tpj2lvcymfp27$_run_closure3$_closure9@6263dc2d]' in the middle so verify before and after that.
        Truth.assertThat(it).startsWith("Could not find method abdd() for arguments [")
        Truth.assertThat(it).endsWith("] on extension 'android' of type com.android.build.gradle.internal.dsl.BaseAppModuleExtension")
      }
    )
  }

  @Test
  fun testPropertyNotFoundInBuildFileAndroidSection() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val buildFile = preparedProject.root.resolve("app/" + SdkConstants.FN_BUILD_GRADLE)
    buildFile.appendText("\nandroid { abdd = \"abc\" }")

    runSyncAndCheckFailure(
      preparedProject = preparedProject,
      expectedErrorNodeNameVerifier = {
        Truth.assertThat(it).isEqualTo(
          "Could not set unknown property 'abdd' for extension 'android' of type com.android.build.gradle.internal.dsl.BaseAppModuleExtension")
      }
    )
  }
}