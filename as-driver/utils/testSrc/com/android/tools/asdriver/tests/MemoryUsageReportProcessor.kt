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

import com.android.tools.perflogger.Analyzer
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.android.tools.perflogger.WindowDeviationAnalyzer
import io.ktor.util.date.getTimeMillis
import java.lang.Boolean.getBoolean
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

// This is the name of the flag that is used for local E2e integration test runs and, when set, will be resent to the test Studio instance
// to enable collection of extended memory usage reports
const val COLLECT_AND_LOG_EXTENDED_MEMORY_REPORTS: String = "studio.collect.extended.memory.reports"

// The name of the flag that is used for enabling gathering hprof snapshots
const val DUMP_HPROF_SNAPSHOT: String = "studio.dump.hprof.snapshot"

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

    private val analyzer = WindowDeviationAnalyzer.Builder()
      .setMetricAggregate(Analyzer.MetricAggregate.MEDIAN)
      .setRunInfoQueryLimit(50)
      .addMedianTolerance(WindowDeviationAnalyzer.MedianToleranceParams.Builder()
                            .setConstTerm(1525000.0).build()) // All the current false-positives fall into the 1.525mb threshold
      .build();

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
      val dateFormat = SimpleDateFormat("HH:mm:ss z")
      println("Collecting memory statistics. Started at ${dateFormat.format(Date())}. This could take 15-30 seconds")
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
      metric.setAnalyzers(benchmark, setOf(analyzer))
      metric.addSamples(benchmark, Metric.MetricSample(timeStamp, totalObjectsSize))
      metric.commit()

      val objectStatisticsRegex = "(\\d+) bytes/(\\d+) objects\\[(\\d+) bytes/(\\d+) objects\\]"

      parseAndCommitMetrics("Total platform objects memory: $objectStatisticsRegex", "total_platform_objects_self_size",
                            "total_platform_objects_retained_size", installation, benchmark, timeStamp)

      m = installation.memoryReportFile.waitForMatchingLine("Total shared memory: (\\d+) bytes/(\\d+) objects", 60,
                                                            TimeUnit.SECONDS)
      val sharedObjectsSize = m.group(1).toLong()
      metric = Metric("total_shared_objects_size")
      metric.setAnalyzers(benchmark, setOf(analyzer))
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
        metric.setAnalyzers(benchmark, setOf(analyzer))
        metric.addSamples(benchmark, Metric.MetricSample(timeStamp, categoryOwnedSize))
        metric.commit()
        parseAndCommitMetrics("    Platform object: $objectStatisticsRegex", categoryLabel + "_platform_objects_self_size",
                              categoryLabel + "_platform_objects_retained_size", installation, benchmark, timeStamp)
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
        metric.setAnalyzers(benchmark, setOf(analyzer))
        metric.addSamples(benchmark, Metric.MetricSample(timeStamp, componentOwnedSize))
        metric.commit()
        parseAndCommitMetrics("    Platform object: $objectStatisticsRegex", componentLabel + "_platform_objects_self_size",
                              componentLabel + "_platform_objects_retained_size", installation, benchmark, timeStamp)
      }
      println("Memory statistics collection finished successfully. Took ${TimeUnit.MILLISECONDS.toSeconds(reportCollectionTimeMs)}seconds.")
      if (getBoolean(COLLECT_AND_LOG_EXTENDED_MEMORY_REPORTS)) {
        installation.memoryReportFile.printContents()
      }
    }

    private fun parseAndCommitMetrics(
      searchString: String,
      selfSizeMetricName: String,
      retainedSizeMetricName: String,
      installation: AndroidStudioInstallation,
      benchmark: Benchmark,
      timeStamp: Long
    ) {
      val m1 = installation.memoryReportFile.waitForMatchingLine(searchString, 60,
                                                                 TimeUnit.SECONDS)
      val totalPlatformObjectsSelfSize = m1.group(1).toLong()
      val totalPlatformObjectsRetainedSize = m1.group(3).toLong()
      var metric = Metric(selfSizeMetricName)
      metric.setAnalyzers(benchmark, setOf(analyzer))
      metric.addSamples(benchmark, Metric.MetricSample(timeStamp, totalPlatformObjectsSelfSize))
      metric.commit()
      metric = Metric(retainedSizeMetricName)
      metric.setAnalyzers(benchmark, setOf(analyzer))
      metric.addSamples(benchmark, Metric.MetricSample(timeStamp, totalPlatformObjectsRetainedSize))
      metric.commit()
    }

    fun collectMemoryUsageStatistics(studio: AndroidStudio,
                                     installation: AndroidStudioInstallation,
                                     watcher: MemoryDashboardNameProviderWatcher,
                                     testLabel: String) {
      collectMemoryUsageStatistics(studio, installation, "${watcher.dashboardName}_$testLabel");
    }
  }
}
