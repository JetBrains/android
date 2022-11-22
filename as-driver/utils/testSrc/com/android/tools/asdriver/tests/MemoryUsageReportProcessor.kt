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
package com.android.tools.asdriver.tests

import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import io.ktor.util.date.getTimeMillis
import java.util.concurrent.TimeUnit

/**
 * Util class that requests memory usage report collection by calling internal action `IntegrationTestCollectMemoryUsageStatisticsAction`,
 * parses the output and sends the results to perfgate.
 */
class MemoryUsageReportProcessor {
  companion object {
    private val reportCollectionTimeBenchmark = Benchmark.Builder("Memory Report Collection Time")
      .setProject("Android Studio Memory Usage")
      .setDescription("How long it took to collect memory report for different tests.")
      .build()

    /**
     * @param memoryDashboardName a string that uniquely specifies the integration test. Will be used for perfgate reporting.
     * Memory usage data will be written to a dashboard name as [memoryDashboardName].
     */
    fun collectMemoryUsageStatistics(
      studio: AndroidStudio,
      installation: AndroidStudioInstallation,
      memoryDashboardName: String?
    ) {
      val testDisplayNameNoWhitespaces = memoryDashboardName!!.replace(' ', '_')
      println("Collecting memory statistics. This could take 15-30 seconds")
      studio.executeAction("IntegrationTestCollectMemoryUsageStatisticsAction")
      var m = installation.memoryReportFile.waitForMatchingLine("Total used memory: (\\d+) bytes/(\\d+) objects",
                                                                "Memory usage report collection failed: .*", 60,
                                                                TimeUnit.SECONDS)
      val timeStamp = getTimeMillis()
      val totalObjectsSize = m.group(1).toLong()
      assert(totalObjectsSize > 1024 * 1024 * 10) { "Total size of objects should be over 10mb, problem on the memory reporting side." }
      val benchmark = Benchmark.Builder(testDisplayNameNoWhitespaces)
        .setProject("Android Studio Memory Usage")
        .setDescription("Memory usage by Android Studio components during the `$memoryDashboardName` test execution.")
        .build()
      var metric = Metric("total_used_memory")
      metric.addSamples(benchmark, Metric.MetricSample(timeStamp, totalObjectsSize))
      metric.commit()
      m = installation.memoryReportFile.waitForMatchingLine("Total shared memory: (\\d+) bytes/(\\d+) objects", 60,
                                                            TimeUnit.SECONDS)
      val sharedObjectsSize = m.group(1).toLong()
      metric = Metric("total_shared_objects_size")
      metric.addSamples(benchmark, Metric.MetricSample(timeStamp, sharedObjectsSize))
      metric.commit()

      m = installation.memoryReportFile.waitForMatchingLine("Report collection time: (\\d+) ms", 60,
                                                            TimeUnit.SECONDS)
      val reportCollectionTimeMs = m.group(1).toLong()
      metric = Metric(testDisplayNameNoWhitespaces)
      metric.addSamples(reportCollectionTimeBenchmark, Metric.MetricSample(timeStamp, reportCollectionTimeMs))
      metric.commit()

      m = installation.memoryReportFile.waitForMatchingLine("(\\d+) Categories:", 60, TimeUnit.SECONDS)
      val numberOfCategories = m.group(1).toInt()
      repeat(numberOfCategories) {
        m = installation.memoryReportFile.waitForMatchingLine("  Category ([\\w:]+):", 60,
                                                              TimeUnit.SECONDS)
        val categoryLabel = m.group(1).replace(':', '_')
        m = installation.memoryReportFile.waitForMatchingLine("    Owned: (\\d+) bytes/(\\d+) objects", 60,
                                                              TimeUnit.SECONDS)
        val categoryOwnedSize = m.group(1).toLong()
        metric = Metric(categoryLabel + "_category_owned_objects_size")
        metric.addSamples(benchmark, Metric.MetricSample(timeStamp, categoryOwnedSize))
        metric.commit()
      }
      m = installation.memoryReportFile.waitForMatchingLine("(\\d+) Components:", 60, TimeUnit.SECONDS)
      val numberOfComponents = m.group(1).toInt()
      repeat(numberOfComponents) {
        m = installation.memoryReportFile.waitForMatchingLine("  Component ([\\w:]+):", 60,
                                                              TimeUnit.SECONDS)
        val componentLabel = m.group(1)
        m = installation.memoryReportFile.waitForMatchingLine("    Owned: (\\d+) bytes/(\\d+) objects", 60,
                                                              TimeUnit.SECONDS)
        val componentOwnedSize = m.group(1).toLong()
        metric = Metric(componentLabel + "_component_owned_objects_size")
        metric.addSamples(benchmark, Metric.MetricSample(timeStamp, componentOwnedSize))
        metric.commit()
      }
      println("Memory statistics collection finished successfully. Took ${TimeUnit.MILLISECONDS.toSeconds(reportCollectionTimeMs)}seconds.")
    }
  }
}
