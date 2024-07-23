/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.benchmarks

import com.android.testutils.JarTestSuiteRunner
import com.android.tools.perflogger.PerfData
import com.android.tools.tests.GradleDaemonsRule
import com.android.tools.tests.IdeaTestSuiteBase
import org.junit.ClassRule
import org.junit.runner.RunWith

@RunWith(JarTestSuiteRunner::class)
class IdeBenchmarkTestSuite : IdeaTestSuiteBase() {
  companion object {
    @get:ClassRule
    val gradleDaemonCleanup = GradleDaemonsRule()

    init {
      try {
        // SantaTracker.
        setUpSourceZip(
          "prebuilts/studio/buildbenchmarks/SantaTracker.181be75/src.zip",
          "tools/adt/idea/ide-perf-tests/testData/SantaTracker",
          DiffSpec("prebuilts/studio/buildbenchmarks/SantaTracker.181be75/setupForIdeTest.diff", 2))
        unzipIntoOfflineMavenRepo("prebuilts/studio/buildbenchmarks/SantaTracker.181be75/repo.zip")

        // Updated SantaTracker project with Kotlin sources
        setUpSourceZip(
          "prebuilts/studio/buildbenchmarks/SantaTrackerKotlin/src.zip",
          "tools/adt/idea/ide-perf-tests/testData/SantaTrackerKotlin",
          DiffSpec("prebuilts/studio/buildbenchmarks/SantaTrackerKotlin/setupForIdeTest.diff", 0))
        unzipIntoOfflineMavenRepo("prebuilts/studio/buildbenchmarks/SantaTrackerKotlin/repo.zip")

        unzipIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin.zip")
        linkIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin_runtime_dependencies.manifest")
        linkIntoOfflineMavenRepo("tools/adt/idea/ide-perf-tests/test_deps.manifest")
        linkIntoOfflineMavenRepo("tools/base/build-system/integration-test/kotlin_gradle_plugin_prebuilts.manifest")

        // Write Perfgate metadata (e.g. benchmark descriptions).
        val perfData = PerfData()
        perfData.addBenchmark(SimpleHighlightingBenchmark.benchmark)
        perfData.addBenchmark(MlModelBindingBenchmark.benchmark)
        perfData.addBenchmark(FullProjectBenchmark.highlightingBenchmark)
        perfData.addBenchmark(FullProjectBenchmark.layoutCompletionBenchmark)
        perfData.addBenchmark(FullProjectBenchmark.completionBenchmark)
        perfData.addBenchmark(FullProjectBenchmark.lintInspectionBenchmark)
        perfData.commit()
      }
      catch (e: Throwable) {
        System.err.println("ERROR: Failed to initialize test suite, tests will likely fail following this error")
        e.printStackTrace()
      }
    }
  }
}
