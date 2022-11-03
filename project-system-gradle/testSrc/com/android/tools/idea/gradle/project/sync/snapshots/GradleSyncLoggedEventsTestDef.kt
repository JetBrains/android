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
package com.android.tools.idea.gradle.project.sync.snapshots

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.LoggedUsage
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_70
import com.android.tools.idea.testing.ModelVersion
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.TextFormat
import com.intellij.openapi.project.Project
import java.io.File
import java.util.Locale

data class GradleSyncLoggedEventsTestDef(
  val namePrefix: String,
  override val testProject: TestProject,
  override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
  val verify: GradleSyncLoggedEventsTestDef.(events: List<LoggedUsage>) -> Unit = {}
) : SyncedProjectTestDef {

  override val name: String
    get() = "$namePrefix - ${testProject.projectName}"

  override fun toString(): String = name

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): SyncedProjectTestDef {
    return copy(agpVersion = agpVersion)
  }

  private val testUsageTracker = TestUsageTracker(VirtualTimeScheduler())

  override fun setup() {
    UsageTracker.setWriterForTest(testUsageTracker)
    AnalyticsSettings.optedIn = true
  }

  override fun runTest(root: File, project: Project) = Unit

  override fun verifyAfterClosing(root: File) {
    val usages = testUsageTracker.usages
    AnalyticsSettings.optedIn = false
    UsageTracker.cleanAfterTesting()
    if (System.getenv("SYNC_BASED_TESTS_DEBUG_OUTPUT")?.lowercase(Locale.getDefault()) == "y") {
      val events = usages
        .joinToString("\n") { buildString { TextFormat.printer().print(it.studioEvent, this) } }
      println(events)
    }
    verify(usages)
  }

  companion object {
    val tests = listOf(
      GradleSyncLoggedEventsTestDef(
        namePrefix = "logged_events",
        testProject = TestProject.SIMPLE_APPLICATION
      ) { events ->
        assertThat(events.dumpSyncEvents()).isEqualTo(
          buildString {
            val expectedMode = if (shouldSupportParallelSync()) "PARALLEL" else "SEQUENTIAL"
            appendLine(
              """
              |GRADLE_SYNC_STARTED
              |  USER_REQUESTED_PARALLEL
              |GRADLE_SYNC_SETUP_STARTED
              |  USER_REQUESTED_PARALLEL""".trim()
            )
            if (agpVersion == AGP_70) {
              appendLine(
                """
              |GRADLE_SYNC_ISSUES
              |  USER_REQUESTED_PARALLEL
              |  STUDIO_REQUESTD_$expectedMode
              |  TYPE_COMPILE_SDK_VERSION_TOO_HIGH
              |    OPEN_FILE_HYPERLINK""".trim()
              )
            }
            appendLine(
              """
              |GRADLE_SYNC_ENDED
              |  USER_REQUESTED_PARALLEL
              |  STUDIO_REQUESTD_$expectedMode
              |GRADLE_BUILD_DETAILS
              |INTELLIJ_PROJECT_SIZE_STATS
              |  JAVA : 3
              |  XML : 16
              |  DOT_CLASS : 0
              |  KOTLIN : 0
              |  NATIVE : 0""".trim()
            )
          }.trimMargin()
        )
      },
      GradleSyncLoggedEventsTestDef(
        namePrefix = "logged_events",
        testProject = TestProject.SIMPLE_APPLICATION_NO_PARALLEL_SYNC
      ) { events ->
        assertThat(events.dumpSyncEvents()).isEqualTo(
          buildString {
            appendLine(
              """
              |GRADLE_SYNC_STARTED
              |  USER_REQUESTED_SEQUENTIAL
              |GRADLE_SYNC_SETUP_STARTED
              |  USER_REQUESTED_SEQUENTIAL""".trim()
            )
            if (agpVersion == AGP_70) {
              appendLine(
                """
              |GRADLE_SYNC_ISSUES
              |  USER_REQUESTED_SEQUENTIAL
              |  STUDIO_REQUESTD_SEQUENTIAL
              |  TYPE_COMPILE_SDK_VERSION_TOO_HIGH
              |    OPEN_FILE_HYPERLINK""".trim()
              )
            }
            appendLine(
              """
              |GRADLE_SYNC_ENDED
              |  USER_REQUESTED_SEQUENTIAL
              |  STUDIO_REQUESTD_SEQUENTIAL
              |GRADLE_BUILD_DETAILS
              |INTELLIJ_PROJECT_SIZE_STATS
              |  JAVA : 3
              |  XML : 16
              |  DOT_CLASS : 0
              |  KOTLIN : 0
              |  NATIVE : 0""".trim()
            )
          }.trimMargin()
        )
      },
      GradleSyncLoggedEventsTestDef(
        namePrefix = "module_counts",
        testProject = TestProject.PSD_SAMPLE_GROOVY
      ) { events ->
        assertThat(events.dumpModuleCounts()).isEqualTo(
          """
            |total_module_count: 11
            |app_module_count: 1
            |lib_module_count: 6
          """.trimMargin()
        )
      },
      GradleSyncLoggedEventsTestDef(
        namePrefix = "module_counts",
        testProject = TestProject.COMPOSITE_BUILD
      ) { events ->
        assertThat(events.dumpModuleCounts()).isEqualTo(
          """
            |total_module_count: 12
            |app_module_count: 3
            |lib_module_count: 3
          """.trimMargin()
        )
      },
    )

    private fun List<LoggedUsage>.dumpSyncEvents(): String {
      return map { it.studioEvent }
        .filter { it.hasGradleSyncStats() || it.hasGradleBuildDetails() || it.intellijProjectSizeStatsCount > 0 }
        .joinToString("") {
          buildString {
            appendLine(it.kind.toString())
            if (it.gradleSyncStats.hasUserRequestedSyncType()) {
              if (it.gradleSyncStats.hasUserRequestedSyncType()) appendLine("  ${it.gradleSyncStats.userRequestedSyncType}")
              if (it.gradleSyncStats.hasStudioRequestedSyncType()) appendLine("  ${it.gradleSyncStats.studioRequestedSyncType}")
              it.gradleSyncIssuesList.forEach { issue ->
                appendLine("  ${issue.type}")
                issue.offeredQuickFixesList.forEach { fix ->
                  appendLine("    $fix")
                }
              }
            }
            it.intellijProjectSizeStatsList.forEach {
              entry -> appendLine("  ${entry.type} : ${entry.count}")
            }
          }
        }
        .trim()
    }

    private fun List<LoggedUsage>.dumpModuleCounts(): String {
      return map { it.studioEvent }
        .filter { it.hasGradleBuildDetails() }
        .flatMap { it.gradleBuildDetails.modulesList }
        .joinToString("\n") { buildString { TextFormat.printer().print(it, this) } }
        .trim()
    }
  }
}

private fun GradleSyncLoggedEventsTestDef.shouldSupportParallelSync() =
  agpVersion > AgpVersionSoftwareEnvironmentDescriptor.AGP_73 && agpVersion.modelVersion == ModelVersion.V2
