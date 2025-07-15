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
package com.android.tools.idea

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import com.android.tools.platform.performance.testing.PlatformPerformanceBenchmark
import com.intellij.openapi.util.SystemInfo
import java.util.Map
import org.junit.Rule
import org.junit.Test

class StartupPerformanceTest {
  @JvmField
  @Rule
  val system: AndroidSystem = AndroidSystem.standard()

  @JvmField
  @Rule
  var watcher = MemoryDashboardNameProviderWatcher()

  @Test
  fun testStartupPerformance() {
    // Create a new android project, and set a fixed distribution
    val project = AndroidProject("tools/adt/idea/android/integration/testData/architecture-samples")
    // Don't show Decompiler legal notice in case of resolving in .class files.
    system.installation.acceptLegalDecompilerNotice()

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/editor_performance_test_deps.manifest"))
    project.setDistribution("tools/external/gradle/gradle-8.6-bin.zip")

    system.runStudio(project) { studio ->
      studio.waitForSync()
      studio.waitForIndex()

      studio.openFile(null, "app/src/androidTest/java/com/example/android/architecture/blueprints/todoapp/tasks/TasksScreenTest.kt", false,
                      true)
    }

    system.runStudio(project, watcher.dashboardName) { studio ->
      studio.waitForSync()
      studio.waitForIndex()

      studio.waitForFinishedCodeAnalysis(null)
    }

    val benchmark = PlatformPerformanceBenchmark(watcher.dashboardName!!)
    val stats = system.installation.studioEvents

    stats.get("STARTUP_EVENT").findFirst().get().let { benchmark.log("startup_event", it.startupEvent.durationMs.toLong()) }
    stats.get("STARTUP_PERFORMANCE_CODE_LOADED_AND_VISIBLE_IN_EDITOR").findFirst().get().let {
      benchmark.log("startup_performance_code_loaded_and_visible_in_editor",
                    it.startupPerformanceCodeLoadedAndVisibleInEditor.durationMs.toLong())
    }
    stats.get("STARTUP_PERFORMANCE_FIRST_UI_SHOWN").findFirst().get().let {
      benchmark.log("startup_performance_first_ui_shown", it.startupPerformanceFirstUiShownEvent.durationMs.toLong())
    }
    stats.get("STARTUP_PERFORMANCE_FRAME_BECAME_INTERACTIVE").findFirst().get().let {
      benchmark.log("startup_performance_frame_became_interactive", it.startupPerformanceFrameBecameInteractiveEvent.durationMs.toLong())
    }
    stats.get("STARTUP_PERFORMANCE_FRAME_BECAME_VISIBLE").findFirst().get().let {
      benchmark.log("startup_performance_frame_became_visible", it.startupPerformanceFrameBecameVisibleEvent.durationMs.toLong())
    }

    // [Linux test const term, Windows test const term]
    val metricConstTerms = Map.of("pausedTimeInIndexingOrScanning", listOf(6, 6),
                                   "indexingTimeWithoutPauses", listOf(31, 225),
                                   "scanningTimeWithoutPauses", listOf(88, 760),
                                   "startup_performance_code_loaded_and_visible_in_editor", listOf(200, 200),
                                   "startup_performance_frame_became_visible", listOf(120, 384),
                                   "startup_performance_frame_became_interactive", listOf(145, 664),
                                   "startup_event", listOf(5, 300),
                                   "dumbModeTimeWithPauses", listOf(0, 330),
                                   "startup_performance_first_ui_shown", listOf(40, 10))

    system.installation.indexingMetrics.get(project).forEach {
      // Per-filetype metrics are too volatile and fine-granular. Let's report them without analyzer.
      if (it.metricLabel.startsWith("processingSpeedAvg_") ||
          it.metricLabel.startsWith("processingSpeedOfBaseLanguageAvg_") ||
          it.metricLabel.startsWith("processingSpeedWorst_") ||
          it.metricLabel.startsWith("processingSpeedOfBaseLanguageWorst_") ||
          it.metricLabel.startsWith("processingTime_") ||
          // Indexing is asynchronously scheduled, and there can be natural variations (e.g. two nearby update requests being conflated into
          // one, or races with other background processes that modify files or request an index refresh).
          it.metricLabel.startsWith("numberOfRunsOfIndexing") ||
          it.metricLabel.startsWith("numberOfIndexedFilesWithNothingToWrite")
        ) {
        benchmark.logWithoutAnalyzer(it.metricLabel, it.metricValue)
        return
      }
      metricConstTerms[it.metricLabel]?.get(if (SystemInfo.isWindows) 0 else 1)?.toLong()?.let { constTerm ->
        benchmark.log(it.metricLabel, it.metricValue, constTerm)
        return
      }

      benchmark.log(it.metricLabel, it.metricValue)
    }
  }
}