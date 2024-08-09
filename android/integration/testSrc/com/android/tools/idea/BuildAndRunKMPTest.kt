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
package com.android.tools.idea

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.Emulator
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import com.android.tools.asdriver.tests.MemoryUsageReportProcessor.Companion.collectMemoryUsageStatistics
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.android.tools.perflogger.Metric.MetricSample
import com.android.tools.perflogger.PerfData
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

class BuildAndRunKMPTest {
  @JvmField
  @Rule
  val system = AndroidSystem.standard()

  @JvmField
  @Rule
  var watcher = MemoryDashboardNameProviderWatcher()

  val metric = Metric("Time-elapsed")

  @Test
  fun buildAndRunKmpTest() {
    val benchmark = createBenchmark()
    val startTime = System.currentTimeMillis()

    benchmark.log("test_start", System.currentTimeMillis())

    val project = AndroidProject("tools/adt/idea/android/integration/testData/kmpapp")
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/buildkmpproject_deps.manifest"))

    benchmark.log("studio_start", System.currentTimeMillis())

    system.runAdb { adb ->
      system.runStudio(project,benchmark)  { studio ->
        system.runEmulator(Emulator.SystemImage.API_31) { emulator ->
          studio.waitForSync()
          collectMemoryUsageStatistics(studio, system.installation, watcher, "afterSync")
          studio.waitForIndex()
          println("Finished waiting for index")

          println("Waiting for boot")
          metric.addSamples(Benchmark.Builder("KMP-before-boot").setProject("Android Studio E2E").build(), MetricSample(Instant.now().toEpochMilli(), System.currentTimeMillis() - startTime ))
          benchmark.log("calling_waitForBoot", System.currentTimeMillis())
          emulator.waitForBoot()
          metric.addSamples(Benchmark.Builder("KMP-after-boot").setProject("Android Studio E2E").build(), MetricSample(Instant.now().toEpochMilli(), System.currentTimeMillis() - startTime ))
          benchmark.log("after_waitForBoot", System.currentTimeMillis())

          studio.executeAction("MakeGradleProject")
          studio.waitForBuild()

          println("Waiting for device")
          adb.waitForDevice(emulator)

          println("Running the app")
          studio.executeAction("Run")

          studio.waitForEmulatorStart(system.installation.ideaLog, emulator, "com\\.google\\.samples\\.apps\\.kmp", 60, TimeUnit.SECONDS)
          emulator.logCat.waitForMatchingLine(".*Hello World!.*", 30, TimeUnit.SECONDS)
          metric.addSamples(Benchmark.Builder("KMP-total-time").setProject("Android Studio E2E").build(), MetricSample(Instant.now().toEpochMilli(), System.currentTimeMillis() - startTime ))
          benchmark.log("test_end", System.currentTimeMillis())
          metric.commit()
        }
      }
    }
  }

  @Throws(Exception::class)
  private fun createBenchmark(): Benchmark {
    val benchmarkName = "BuildAndRunKMP"
    val perfData = PerfData()

    val benchmark =
      Benchmark.Builder(benchmarkName)
        .setProject("Android Studio E2E")
        .build()
    perfData.addBenchmark(benchmark)
    perfData.commit()

    return benchmark
  }
}