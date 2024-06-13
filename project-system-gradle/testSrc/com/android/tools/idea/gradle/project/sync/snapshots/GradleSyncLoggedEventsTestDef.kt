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
import com.android.tools.idea.gradle.project.sync.internal.KOTLIN_VERSION_FOR_TESTS
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.ModelVersion
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.TextFormat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
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
        assertThat(events.dumpGradleSyncEvents()).isEqualTo(
          buildString {
            val expectedMode = if (shouldSupportParallelSync()) "PARALLEL" else "SEQUENTIAL"
            appendLine(
              """
              |GRADLE_SYNC_STARTED
              |  USER_REQUESTED_PARALLEL
              |GRADLE_SYNC_SETUP_STARTED
              |  USER_REQUESTED_PARALLEL
              |GRADLE_SYNC_ENDED
              |  USER_REQUESTED_PARALLEL
              |  STUDIO_REQUESTD_$expectedMode""".trim()
            )
          }.trimMargin()
        )
        assertThat(events.dumpGradleDetailEvents()).isEqualTo(
          buildString {
            appendLine(
              """
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
        assertThat(events.dumpGradleSyncEvents()).isEqualTo(
          buildString {
            appendLine(
              """
              |GRADLE_SYNC_STARTED
              |  USER_REQUESTED_SEQUENTIAL
              |GRADLE_SYNC_SETUP_STARTED
              |  USER_REQUESTED_SEQUENTIAL
              |GRADLE_SYNC_ENDED
              |  USER_REQUESTED_SEQUENTIAL
              |  STUDIO_REQUESTD_SEQUENTIAL""".trim()
            )
          }.trimMargin()
        )
        assertThat(events.dumpGradleDetailEvents()).isEqualTo(
          buildString {
            appendLine(
              """
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
            |Module count: 11
            |Library count: 48
            |total_module_count: 11
            |app_module_count: 1
            |lib_module_count: 6
            |dynamic_feature_module_count: 1
            |test_module_count: 0
            |kotlin_multiplatform_module_count: 0
          """.trimMargin()
        )
      },
      GradleSyncLoggedEventsTestDef(
        namePrefix = "module_counts",
        testProject = TestProject.COMPOSITE_BUILD
      ) { events ->
        assertThat(events.dumpModuleCounts()).isEqualTo(
          """
            |Module count: 13
            |Library count: 35
            |total_module_count: 13
            |app_module_count: 3
            |lib_module_count: 3
            |dynamic_feature_module_count: 0
            |test_module_count: 0
            |kotlin_multiplatform_module_count: 0
          """.trimMargin()
        )
      },
      GradleSyncLoggedEventsTestDef(
        namePrefix = "kotlin_versions",
        testProject = TestProject.KOTLIN_KAPT
      ) { events ->
        assertThat(events.dumpKotlinVersions(agpVersion.kotlinVersion)).isEqualTo(
          """
            |kotlin version: KOTLIN_VERSION_FOR_TESTS
            |core-ktx version: 1.0.1
          """.trimMargin()
        )
      },
      GradleSyncLoggedEventsTestDef(
        namePrefix = "kotlin_versions",
        testProject = TestProject.SIMPLE_APPLICATION_VERSION_CATALOG
      ) { events ->
        assertThat(events.dumpKotlinVersions(agpVersion.kotlinVersion)).isEqualTo("")
      }
    )

    private fun List<LoggedUsage>.dumpGradleSyncEvents() = dumpEvents(
      filterBy = { hasGradleSyncStats() },
    )

    private fun List<LoggedUsage>.dumpGradleDetailEvents() = dumpEvents(
      filterBy = { hasGradleBuildDetails() || intellijProjectSizeStatsCount > 0 },
      sortedBy = { kind.name }
    )

    private fun List<LoggedUsage>.dumpEvents(
      filterBy: AndroidStudioEvent.() -> Boolean,
      sortedBy: AndroidStudioEvent.() -> String? = { null }
    ): String {
      return map { it.studioEvent }
        .filter { filterBy(it) }
        .sortedBy { sortedBy(it) }
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
        .map { it.gradleBuildDetails }
        .let {
          buildString {
            it
              .forEach { gradleBuildDetails ->
                appendLine("Module count: ${gradleBuildDetails.getModuleCount()}")
                appendLine("Library count: ${gradleBuildDetails.libCount}")
                gradleBuildDetails.modulesList.forEach { gradleModule ->
                  TextFormat.printer().print(gradleModule, this)
                }
              }
          }.trim()
        }
    }

    private fun List<LoggedUsage>.dumpKotlinVersions(expectedKotlinVersion: String?): String {
      val expectedKotlinVersion = expectedKotlinVersion ?: KOTLIN_VERSION_FOR_TESTS
      return map { it.studioEvent }
        .filter { it.hasGradleSyncStats() }
        .map { it.kotlinSupport }
        .last()
        .let {
          buildString {
            if (it.hasKotlinSupportVersion()) {
              val versionForPrint = it.kotlinSupportVersion.takeIf { it != expectedKotlinVersion } ?: "KOTLIN_VERSION_FOR_TESTS"
              appendLine("kotlin version: $versionForPrint")
            }
            if (it.hasAndroidKtxVersion()) {
              appendLine("core-ktx version: ${it.androidKtxVersion}")
            }
          }.trim()
        }
    }
  }
}

private fun GradleSyncLoggedEventsTestDef.shouldSupportParallelSync() =
  agpVersion > AgpVersionSoftwareEnvironmentDescriptor.AGP_73 && agpVersion.modelVersion == ModelVersion.V2