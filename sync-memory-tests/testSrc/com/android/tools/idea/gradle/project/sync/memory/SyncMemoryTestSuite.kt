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
package com.android.tools.idea.gradle.project.sync.memory

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.sync.BenchmarkProject
import com.android.tools.idea.gradle.project.sync.BenchmarkProject.STANDARD_1000
import com.android.tools.idea.gradle.project.sync.BenchmarkProject.STANDARD_200
import com.android.tools.idea.gradle.project.sync.BenchmarkProject.STANDARD_2000
import com.android.tools.idea.gradle.project.sync.BenchmarkProject.STANDARD_4200
import com.android.tools.idea.gradle.project.sync.FEATURE_RUNTIME_CLASSPATH_1000
import com.android.tools.idea.gradle.project.sync.MULTI_APP_100_NAME
import com.android.tools.idea.gradle.project.sync.MULTI_APP_190_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_1000_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_2000_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_200_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_4200_NAME
import com.android.tools.idea.gradle.project.sync.createBenchmarkTestRule
import com.android.tools.idea.testing.requestSyncAndWait
import com.intellij.util.io.createDirectories
import org.junit.Rule
import org.junit.Test
import java.io.File

class Benchmark1000MemoryTest {
  @get:Rule val benchmarkTestRule = createBenchmarkTestRule(SUBSET_1000_NAME, STANDARD_1000)
  @get:Rule val captureFromHistogramRule = CaptureSyncMemoryFromHistogramRule(benchmarkTestRule.projectName, disableAnalyzers = true)

  @Test fun testMemory() = benchmarkTestRule.openProject()
}

class Benchmark2000MemoryTest {
  @get:Rule val benchmarkTestRule = createBenchmarkTestRule(SUBSET_2000_NAME, STANDARD_2000)
  @get:Rule val captureFromHistogramRule = CaptureSyncMemoryFromHistogramRule(benchmarkTestRule.projectName)
  @Test fun testMemory() = benchmarkTestRule.openProject()
}

class Benchmark4200MemoryTest {
  @get:Rule val benchmarkTestRule = createBenchmarkTestRule(SUBSET_4200_NAME, STANDARD_4200)
  @get:Rule val captureFromHistogramRule = CaptureSyncMemoryFromHistogramRule(benchmarkTestRule.projectName)
  @Test fun testMemory() = benchmarkTestRule.openProject()
}

class BenchmarkMultiApp100MemoryTest {
  @get:Rule val benchmarkTestRule = createBenchmarkTestRule(MULTI_APP_100_NAME, BenchmarkProject.MULTI_APP_100)
  @get:Rule val captureFromHistogramRule = CaptureSyncMemoryFromHistogramRule(benchmarkTestRule.projectName, disableAnalyzers = true)
  @Test fun testMemory() = benchmarkTestRule.openProject()
}

class BenchmarkMultiApp190MemoryTest {
  @get:Rule
  val benchmarkTestRule = createBenchmarkTestRule(MULTI_APP_190_NAME, BenchmarkProject.MULTI_APP_190)
  @get:Rule val captureFromHistogramRule = CaptureSyncMemoryFromHistogramRule(benchmarkTestRule.projectName, disableAnalyzers = true)
  @Test fun testMemory() = benchmarkTestRule.openProject()
}

class Benchmark200Repeated20TimesMemoryTest  {
  private val repeatCount = 20

  @get:Rule val benchmarkTestRule = createBenchmarkTestRule(SUBSET_200_NAME, STANDARD_200)
  @get:Rule val captureFromHistogramRule = CaptureSyncMemoryFromHistogramRule(
    "${benchmarkTestRule.projectName}_Post_${repeatCount}_Repeats", disableAnalyzers = true)

  @Test
  fun testSyncMemoryPost20Repeats() {
    benchmarkTestRule.openProject { project ->
      repeat (repeatCount - 2 ) {
        project.requestSyncAndWait()
      }
      // Delete all the measurements before the final measurement.
      clearOutputDirectory()

      project.requestSyncAndWait()
    }
  }

  private fun clearOutputDirectory() {
    val directory = File(OUTPUT_DIRECTORY)
    directory.deleteRecursively()
    directory.toPath().createDirectories()
  }
}

class Benchmark1000MemoryRuntimeClasspathTest {
  @get:Rule val benchmarkTestRule = createBenchmarkTestRule(FEATURE_RUNTIME_CLASSPATH_1000, STANDARD_1000)
  @get:Rule val captureFromHistogramRule = CaptureSyncMemoryFromHistogramRule(benchmarkTestRule.projectName, disableAnalyzers = true)

  @Test fun testMemory() {
    StudioFlags.GRADLE_SKIP_RUNTIME_CLASSPATH_FOR_LIBRARIES.override(true)
    GradleExperimentalSettings.getInstance().DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES = true
    benchmarkTestRule.openProject()
  }
}
