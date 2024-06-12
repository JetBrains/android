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

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import org.jetbrains.kotlin.utils.filterIsInstanceAnd
import org.junit.Test

class TomlErrorTest : AbstractSyncFailureIntegrationTest() {

  private fun runSyncAndCheckFailure(
    preparedProject: PreparedTestProject
  ) = runSyncAndCheckGeneralFailure(
    preparedProject = preparedProject,
    verifySyncViewEvents = { buildEvents ->
      // Expect single BuildIssueEvent on Sync Output
      buildEvents.filterIsInstance<BuildIssueEvent>().let { events ->
        expect.that(events).hasSize(1)
        events.firstOrNull()?.let { expect.that(it.message).isEqualTo("Invalid TOML catalog definition.") }
      }
      // Make sure no additional error build issue events are generated
      expect.that(buildEvents.filterIsInstanceAnd<MessageEvent> { it !is BuildIssueEvent }).isEmpty()
      expect.that(buildEvents.finishEventFailures()).isEmpty()
    },
    verifyFailureReported = {
      expect.that(it.gradleSyncFailure).isEqualTo(AndroidStudioEvent.GradleSyncFailure.INVALID_TOML_DEFINITION)
      expect.that(it.buildOutputWindowStats.buildErrorMessagesList.map { it.errorShownType })
        .containsExactly(BuildErrorMessage.ErrorType.INVALID_TOML_DEFINITION)
      expect.that(it.gradleSyncStats.printPhases()).isEqualTo("""
          FAILURE : SYNC_TOTAL/GRADLE_CONFIGURE_ROOT_BUILD
          FAILURE : SYNC_TOTAL
        """.trimIndent())
    },
  )

  @Test
  fun testTomlError() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION_VERSION_CATALOG)

    preparedProject.root.resolve("gradle/libs.versions.toml").let {
      it.appendText("\n/")
    }

    runSyncAndCheckFailure(preparedProject)
  }

  @Test
  fun testTomlAliasError() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION_VERSION_CATALOG)

    preparedProject.root.resolve("gradle/libs.versions.toml").let {
      it.appendText("""
        
        [libraries]
        a = "group:name:1.0"
      """.trimIndent())
    }

    runSyncAndCheckFailure(preparedProject)
  }
}