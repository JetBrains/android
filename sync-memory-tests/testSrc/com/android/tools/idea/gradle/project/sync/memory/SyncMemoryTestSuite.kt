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

import com.android.tools.idea.gradle.project.sync.BenchmarkTestRule
import org.junit.Rule
import org.junit.Test

import com.android.tools.idea.gradle.project.sync.SUBSET_1000_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_100_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_2000_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_200_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_4200_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_500_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_50_NAME
import com.android.tools.idea.gradle.project.sync.createBenchmarkTestRule

class Benchmark50MemoryTest {
  @get:Rule val benchmarkTestRule = createBenchmarkTestRule(SUBSET_50_NAME)
  @get:Rule val measureSyncMemoryUsageRule = MeasureSyncMemoryUsageRule()
  @Test fun testMemory() = runTest(benchmarkTestRule, measureSyncMemoryUsageRule)
}
class Benchmark100MemoryTest {
  @get:Rule val benchmarkTestRule = createBenchmarkTestRule(SUBSET_100_NAME)
  @get:Rule val measureSyncMemoryUsageRule = MeasureSyncMemoryUsageRule()
  @Test fun testMemory() = runTest(benchmarkTestRule, measureSyncMemoryUsageRule)
}

class Benchmark200MemoryTest {
  @get:Rule val benchmarkTestRule = createBenchmarkTestRule(SUBSET_200_NAME)
  @get:Rule val measureSyncMemoryUsageRule = MeasureSyncMemoryUsageRule()
  @Test fun testMemory() = runTest(benchmarkTestRule, measureSyncMemoryUsageRule)
}

class Benchmark500MemoryTest {
  @get:Rule val benchmarkTestRule = createBenchmarkTestRule(SUBSET_500_NAME)
  @get:Rule val measureSyncMemoryUsageRule = MeasureSyncMemoryUsageRule()
  @Test fun testMemory() = runTest(benchmarkTestRule, measureSyncMemoryUsageRule)
}

class Benchmark1000MemoryTest {
  @get:Rule val benchmarkTestRule = createBenchmarkTestRule(SUBSET_1000_NAME)
  @get:Rule val measureSyncMemoryUsageRule = MeasureSyncMemoryUsageRule()

  @Test fun testMemory() = runTest(benchmarkTestRule, measureSyncMemoryUsageRule)
}

class Benchmark2000MemoryTest {
  @get:Rule val benchmarkTestRule = createBenchmarkTestRule(SUBSET_2000_NAME)
  @get:Rule val captureFromHistogramRule = CaptureSyncMemoryFromHistogramRule(benchmarkTestRule.projectName)
  @Test fun testMemory() = benchmarkTestRule.openProject()
}

class Benchmark4200MemoryTest {
  @get:Rule val benchmarkTestRule = createBenchmarkTestRule(SUBSET_4200_NAME)
  @get:Rule val captureFromHistogramRule = CaptureSyncMemoryFromHistogramRule(benchmarkTestRule.projectName)
  @Test fun testMemory() = benchmarkTestRule.openProject()
}

class Benchmark200Repeated20TimesMemoryTest  {
  @get:Rule val benchmarkTestRule = createBenchmarkTestRule(SUBSET_200_NAME)
  @get:Rule val measureSyncMemoryUsageRule = MeasureSyncMemoryUsageRule(
    // Turn off measurements in repeated syncs before the measured one
    disableInitialMeasurements = true
  )

  @Test
  fun testSyncMemoryPost20Repeats() {
    benchmarkTestRule.openProject {
      measureSyncMemoryUsageRule.repeatSyncAndMeasure(it, benchmarkTestRule.projectName, repeatCount = 20)
    }
  }
}

private fun runTest(benchmarkTestRule: BenchmarkTestRule,
                    measureSyncMemoryUsageRule: MeasureSyncMemoryUsageRule) {
  benchmarkTestRule.openProject {
    measureSyncMemoryUsageRule.recordMeasurements(benchmarkTestRule.projectName)
  }
}

