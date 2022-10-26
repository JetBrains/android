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
package com.android.tools.idea

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidStudio
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import io.ktor.util.date.getTimeMillis
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class BuildAndRunWithMemoryTest {
  @JvmField
  @Rule
  val system: AndroidSystem = AndroidSystem.standard()

  private val benchmark = Benchmark.Builder("BuildAndRunWithMemoryTest Memory Usage")
    .setProject("Android Studio Memory Usage")
    .setDescription("Memory usage by Android Studio components after executing BuildAndRunWithMemoryTest.")
    .build()

  /**
   * Version of [BuildAndRunTest.deploymentTest] with tracking of components memory usage. Memory usage statistics is compared with the
   * threshold values and sent to perfgate.
   */
  @Test
  fun deploymentTestWithMemoryTracking() {
    val project = AndroidProject("tools/adt/idea/android/integration/testData/minapp")
    project.setDistribution("tools/external/gradle/gradle-7.2-bin.zip")
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/buildproject_deps.manifest"))

    system.installation.addVmOption("-Dstudio.run.under.integration.test=true")
    // Enabling this flag is required for connecting all the Java Instrumentation agents needed for memory usage statistics.
    system.installation.addVmOption("-Djdk.attach.allowAttachSelf=true")

    system.runAdb { adb ->
      system.runEmulator { emulator ->
        system.runStudio(project) { studio ->
          studio.waitForSync()
          studio.waitForIndex()
          studio.executeAction("MakeGradleProject")
          studio.waitForBuild()

          studio.executeAction("Run")
          system.installation.ideaLog.waitForMatchingLine(
            ".*AndroidProcessHandler - Adding device emulator-${emulator.portString} to monitor for launched app: " +
            "com\\.example\\.minapp",
            60, TimeUnit.SECONDS)
          adb.runCommand("logcat") {
            waitForLog(".*Hello Minimal World!.*", 30.seconds)
          }

          collectMemoryUsageStatistics(studio)
        }
      }
    }
  }


  private fun collectMemoryUsageStatistics(studio: AndroidStudio) {
    studio.executeAction("IntegrationTestCollectMemoryUsageStatisticsAction")
    var m = system.installation.metricsFile.waitForMatchingLine("Total used memory: (\\d+) bytes/(\\d+) objects",
                                                                "Memory usage report collection failed: .*", 60,
                                                                TimeUnit.SECONDS)
    val timeStamp = getTimeMillis()
    val totalObjectsSize = m.group(1).toLong()
    assert(totalObjectsSize > 1024 * 1024 * 10) { "Total size of objects should be over 10mb, problem on the memory reporting side." }
    var metric = Metric("total_used_memory")
    metric.addSamples(benchmark, Metric.MetricSample(timeStamp, totalObjectsSize))
    metric.commit()

    m = system.installation.metricsFile.waitForMatchingLine("(\\d+) Categories:", 60, TimeUnit.SECONDS)
    val numberOfCategories = m.group(1).toInt()
    repeat(numberOfCategories) {
      m = system.installation.metricsFile.waitForMatchingLine("  Category ([\\w:]+):", 60,
                                                              TimeUnit.SECONDS)
      val categoryLabel = m.group(1).replace(':', '_')
      m = system.installation.metricsFile.waitForMatchingLine("    Owned: (\\d+) bytes/(\\d+) objects", 60,
                                                              TimeUnit.SECONDS)
      val categoryOwnedSize = m.group(1).toLong()
      metric = Metric(categoryLabel + "_category_owned_objects_size")
      metric.addSamples(benchmark, Metric.MetricSample(timeStamp, categoryOwnedSize))
      metric.commit()
    }
    m = system.installation.metricsFile.waitForMatchingLine("(\\d+) Components:", 60, TimeUnit.SECONDS)
    val numberOfComponents = m.group(1).toInt()
    repeat(numberOfComponents) {
      m = system.installation.metricsFile.waitForMatchingLine("  Component ([\\w:]+):", 60,
                                                              TimeUnit.SECONDS)
      val componentLabel = m.group(1)
      m = system.installation.metricsFile.waitForMatchingLine("    Owned: (\\d+) bytes/(\\d+) objects", 60,
                                                              TimeUnit.SECONDS)
      val componentOwnedSize = m.group(1).toLong()
      metric = Metric(componentLabel + "_component_owned_objects_size")
      metric.addSamples(benchmark, Metric.MetricSample(timeStamp, componentOwnedSize))
      metric.commit()
    }
  }
}