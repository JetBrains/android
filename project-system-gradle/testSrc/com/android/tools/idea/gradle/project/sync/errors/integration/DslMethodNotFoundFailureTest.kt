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
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import org.junit.Test

class DslMethodNotFoundFailureTest: AbstractSyncFailureIntegrationTest() {

  private fun runSyncAndCheckFailure(
    preparedProject: PreparedTestProject,
    expectedErrorNodeNameVerifier: (String) -> Unit,
    expectedPhases: String
  ) = runSyncAndCheckGeneralFailure(
    preparedProject = preparedProject,
    verifySyncViewEvents = { buildEvents ->
      // Expect single MessageEvent on Sync Output
      buildEvents.filterIsInstance<MessageEvent>().let { events ->
        expect.that(events).hasSize(1)
        events.firstOrNull()?.let { expectedErrorNodeNameVerifier(it.message) }
      }
      // Make sure no additional error build issue events are generated
      expect.that(buildEvents.filterIsInstance<BuildIssueEvent>()).isEmpty()
      expect.that(buildEvents.finishEventFailures()).isEmpty()
    },
    verifyFailureReported = {
      expect.that(it.gradleSyncFailure).isEqualTo(AndroidStudioEvent.GradleSyncFailure.DSL_METHOD_NOT_FOUND)
      expect.that(it.buildOutputWindowStats.buildErrorMessagesList.map { it.errorShownType })
        .containsExactly(BuildErrorMessage.ErrorType.UNKNOWN_ERROR_TYPE)
      expect.that(it.gradleSyncStats.printPhases()).isEqualTo(expectedPhases)
    },
  )

  @Test
  fun testCheckIssueWithMethodNotFoundInSettingsFile() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val settingsFile = preparedProject.root.resolve(SdkConstants.FN_SETTINGS_GRADLE)
    settingsFile.writeText("incude ':app'")

    runSyncAndCheckFailure(
      preparedProject = preparedProject,
      expectedErrorNodeNameVerifier = {
        expect.that(it).isEqualTo(
          "Could not find method incude() for arguments [:app] on settings 'project' of type org.gradle.initialization.DefaultSettings")
      },
      // When settings.gradle file is broken GRADLE_CONFIGURE_ROOT_BUILD is not reported.
      expectedPhases = """
          FAILURE : SYNC_TOTAL
        """.trimIndent()
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
        expect.that(it).isEqualTo(
          "Could not find method abdd() for arguments [] on root project 'project' of type org.gradle.api.Project")
      },
      expectedPhases = """
          FAILURE : SYNC_TOTAL/GRADLE_CONFIGURE_ROOT_BUILD
          FAILURE : SYNC_TOTAL
        """.trimIndent()
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
        expect.that(it).isEqualTo("Could not set unknown property 'abdd' for root project 'project' of type org.gradle.api.Project")
      },
      expectedPhases = """
          FAILURE : SYNC_TOTAL/GRADLE_CONFIGURE_ROOT_BUILD
          FAILURE : SYNC_TOTAL
        """.trimIndent()
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
        expect.that(it).isEqualTo("Could not get unknown property 'abdd' for root project 'project' of type org.gradle.api.Project")
      },
      expectedPhases = """
          FAILURE : SYNC_TOTAL/GRADLE_CONFIGURE_ROOT_BUILD
          FAILURE : SYNC_TOTAL
        """.trimIndent()
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
        expect.that(it).startsWith("Could not find method abdd() for arguments [")
        expect.that(it).endsWith("] on extension 'android' of type com.android.build.gradle.internal.dsl.BaseAppModuleExtension")
      },
      expectedPhases = """
          FAILURE : SYNC_TOTAL/GRADLE_CONFIGURE_ROOT_BUILD
          FAILURE : SYNC_TOTAL
        """.trimIndent()
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
        expect.that(it).isEqualTo(
          "Could not set unknown property 'abdd' for extension 'android' of type com.android.build.gradle.internal.dsl.BaseAppModuleExtension")
      },
      expectedPhases = """
          FAILURE : SYNC_TOTAL/GRADLE_CONFIGURE_ROOT_BUILD
          FAILURE : SYNC_TOTAL
        """.trimIndent()
    )
  }
}