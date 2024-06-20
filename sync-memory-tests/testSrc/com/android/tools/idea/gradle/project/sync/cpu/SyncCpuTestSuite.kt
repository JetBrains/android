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
package com.android.tools.idea.gradle.project.sync.cpu

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.sync.BenchmarkProject.MULTI_APP_100
import com.android.tools.idea.gradle.project.sync.BenchmarkProject.MULTI_APP_190
import com.android.tools.idea.gradle.project.sync.BenchmarkProject.STANDARD_1000
import com.android.tools.idea.gradle.project.sync.BenchmarkProject.STANDARD_200
import com.android.tools.idea.gradle.project.sync.BenchmarkProject.STANDARD_2000
import com.android.tools.idea.gradle.project.sync.BenchmarkProject.STANDARD_4200
import com.android.tools.idea.gradle.project.sync.BenchmarkProject.STANDARD_500
import com.android.tools.idea.gradle.project.sync.BenchmarkTestRule
import com.android.tools.idea.gradle.project.sync.DaemonIdleTimeoutRule
import com.android.tools.idea.gradle.project.sync.FEATURE_RUNTIME_CLASSPATH_1000
import com.android.tools.idea.gradle.project.sync.MULTI_APP_100_NAME
import com.android.tools.idea.gradle.project.sync.MULTI_APP_190_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_1000_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_1000_GRADLE_SNAPSHOT_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_1000_KOTLIN_LATEST_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_2000_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_200_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_4200_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_500_NAME
import com.android.tools.idea.gradle.project.sync.createBenchmarkTestRule
import com.android.tools.idea.testing.requestSyncAndWait
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class Benchmark200CpuTest  {
  @get:Rule val benchmarkProjectSetupRule = createBenchmarkTestRule(SUBSET_200_NAME, STANDARD_200)
  @get:Rule val measureSyncExecutionTimeRule = MeasureSyncExecutionTimeRule(syncCount = 0, enableAnalyzers = false)
  @get:Rule val daemonIdleTimeoutRule = DaemonIdleTimeoutRule(3.minutes)
  @Test fun testCpu() = runTest(benchmarkProjectSetupRule, measureSyncExecutionTimeRule)
}

class Benchmark500CpuTest {
  @get:Rule val benchmarkProjectSetupRule = createBenchmarkTestRule(SUBSET_500_NAME, STANDARD_500)
  @get:Rule val measureSyncExecutionTimeRule = MeasureSyncExecutionTimeRule(syncCount = 25, enableAnalyzers = false)
  @get:Rule val daemonIdleTimeoutRule = DaemonIdleTimeoutRule(5.minutes)
  @Test fun testCpu() = runTest(benchmarkProjectSetupRule, measureSyncExecutionTimeRule)
}

class Benchmark1000CpuTest {
  @get:Rule val benchmarkProjectSetupRule = createBenchmarkTestRule(SUBSET_1000_NAME, STANDARD_1000)
  @get:Rule val measureSyncExecutionTimeRule = MeasureSyncExecutionTimeRule(syncCount = 15)
  @get:Rule val daemonIdleTimeoutRule = DaemonIdleTimeoutRule(6.minutes)
  @Test fun testCpu() = runTest(benchmarkProjectSetupRule, measureSyncExecutionTimeRule)
}

class Benchmark2000CpuTest {
  @get:Rule val benchmarkProjectSetupRule = createBenchmarkTestRule(SUBSET_2000_NAME, STANDARD_2000)
  @get:Rule val measureSyncExecutionTimeRule = MeasureSyncExecutionTimeRule(syncCount = 5)
  @get:Rule val daemonIdleTimeoutRule = DaemonIdleTimeoutRule(7.minutes)
  @Test fun testCpu() = runTest(benchmarkProjectSetupRule, measureSyncExecutionTimeRule)
}

class Benchmark4200CpuTest {
  @get:Rule val benchmarkProjectSetupRule = createBenchmarkTestRule(SUBSET_4200_NAME, STANDARD_4200)
  @get:Rule val measureSyncExecutionTimeRule = MeasureSyncExecutionTimeRule(syncCount = 2)
  @get:Rule val daemonIdleTimeoutRule = DaemonIdleTimeoutRule(10.minutes)
  @Test fun testCpu() = runTest(benchmarkProjectSetupRule, measureSyncExecutionTimeRule)
}

class BenchmarkMultiApp100CpuTest {
  @get:Rule val benchmarkProjectSetupRule = createBenchmarkTestRule(MULTI_APP_100_NAME, MULTI_APP_100)
  @get:Rule val measureSyncExecutionTimeRule = MeasureSyncExecutionTimeRule(syncCount = 5, enableAnalyzers = false)
  @get:Rule val daemonIdleTimeoutRule = DaemonIdleTimeoutRule(15.minutes)
  @Test fun testCpu() = runTest(benchmarkProjectSetupRule, measureSyncExecutionTimeRule)
}

class BenchmarkMultiApp190CpuTest {
  @get:Rule val benchmarkProjectSetupRule = createBenchmarkTestRule(MULTI_APP_190_NAME, MULTI_APP_190)
  @get:Rule val measureSyncExecutionTimeRule = MeasureSyncExecutionTimeRule(syncCount = 5, enableAnalyzers = false)
  @get:Rule val daemonIdleTimeoutRule = DaemonIdleTimeoutRule(15.minutes)
  @Test fun testCpu() = runTest(benchmarkProjectSetupRule, measureSyncExecutionTimeRule)
}

class Benchmark1000CpuRuntimeClasspathTest {
  @get:Rule val benchmarkProjectSetupRule = createBenchmarkTestRule(FEATURE_RUNTIME_CLASSPATH_1000, STANDARD_1000)
  @get:Rule val measureSyncExecutionTimeRule = MeasureSyncExecutionTimeRule(syncCount = 15, enableAnalyzers = false)
  @get:Rule val daemonIdleTimeoutRule = DaemonIdleTimeoutRule(6.minutes)

  @Test fun testCpu() {
    StudioFlags.GRADLE_SKIP_RUNTIME_CLASSPATH_FOR_LIBRARIES.override(true)
    GradleExperimentalSettings.getInstance().DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES = true
    runTest(benchmarkProjectSetupRule, measureSyncExecutionTimeRule)
  }
}

private fun runTest(benchmarkTestRule: BenchmarkTestRule,
                    measureSyncExecutionTimeRule: MeasureSyncExecutionTimeRule) {
  benchmarkTestRule.addListener(measureSyncExecutionTimeRule.listener)
  benchmarkTestRule.openProject { project ->
    repeat(measureSyncExecutionTimeRule.syncCount) {
      project.requestSyncAndWait()
    }
    measureSyncExecutionTimeRule.recordMeasurements(benchmarkTestRule.projectName)
  }
}

class Benchmark1000CpuLatestGradleTest {
  @get:Rule val benchmarkProjectSetupRule = createBenchmarkTestRule(SUBSET_1000_GRADLE_SNAPSHOT_NAME, STANDARD_1000, useLatestGradle = true)
  @get:Rule val measureSyncExecutionTimeRule = MeasureSyncExecutionTimeRule(syncCount = 15, projectToCompareAgainst = SUBSET_1000_NAME)
  @get:Rule val daemonIdleTimeoutRule = DaemonIdleTimeoutRule(6.minutes)
  @Test fun testCpu() = runTest(benchmarkProjectSetupRule, measureSyncExecutionTimeRule)
}

class Benchmark1000CpuLatestKotlinTest {
  @get:Rule val benchmarkProjectSetupRule = createBenchmarkTestRule(SUBSET_1000_KOTLIN_LATEST_NAME, STANDARD_1000, useLatestKotlin = true)
  @get:Rule val measureSyncExecutionTimeRule = MeasureSyncExecutionTimeRule(syncCount = 15, projectToCompareAgainst = SUBSET_1000_NAME)
  @get:Rule val daemonIdleTimeoutRule = DaemonIdleTimeoutRule(6.minutes)
  @Test fun testCpu() = runTest(benchmarkProjectSetupRule, measureSyncExecutionTimeRule)
}