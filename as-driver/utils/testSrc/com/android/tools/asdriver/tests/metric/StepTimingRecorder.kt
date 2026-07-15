/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.asdriver.tests.metric

import com.android.tools.perflogger.Analyzer
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.android.tools.perflogger.WindowDeviationAnalyzer
import com.android.tools.testlib.TestLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe registry for recording live-timed step spans dynamically at runtime inside as-driver integration tests and committing
 * telemetry immediately on endSpan.
 */
object StepTimingRecorder {
  fun interface MetricCommitter {
    fun commit(metricName: String, durationMs: Long)
  }

  @JvmField @Volatile var timeProvider: () -> Long = { System.currentTimeMillis() }

  @JvmField
  @Volatile
  var committer: MetricCommitter = MetricCommitter { metricName, durationMs ->
    val (benchmark, analyzer, now) = createBenchmarkMetadata()
    logDynamicMetric(benchmark, analyzer, now, metricName, durationMs)
  }

  private val activeSpans = ConcurrentHashMap<String, CopyOnWriteArrayList<Long>>()
  private val metricCounts = ConcurrentHashMap<String, Int>()

  @JvmStatic
  fun startSpan(name: String) {
    activeSpans.computeIfAbsent(name) { CopyOnWriteArrayList() }.add(timeProvider())
  }

  @JvmStatic
  fun endSpan(name: String) {
    val stack = activeSpans[name]
    if (stack != null && stack.isNotEmpty()) {
      val start = stack.removeAt(stack.size - 1)
      if (start > 0) {
        val duration = timeProvider() - start
        if (duration >= 0) {
          commitSpanImmediately(name, duration)
        }
      }
    }
  }

  @JvmStatic
  fun clear() {
    activeSpans.clear()
    metricCounts.clear()
    timeProvider = { System.currentTimeMillis() }
    committer = MetricCommitter { metricName, durationMs ->
      val (benchmark, analyzer, now) = createBenchmarkMetadata()
      logDynamicMetric(benchmark, analyzer, now, metricName, durationMs)
    }
  }

  @JvmStatic
  fun recordParsedDuration(apiName: String, durationString: String) {
    val ms = parseDurationToMs(durationString)
    if (ms > 0) {
      commitSpanImmediately(apiName, ms)
    }
  }

  private fun parseDurationToMs(timeStr: String): Long {
    var totalMs = 0L
    try {
      val minMatch = Regex("(\\d+)\\s*(?:m|min|minutes?)\\b", RegexOption.IGNORE_CASE).find(timeStr)
      if (minMatch != null) {
        totalMs += (minMatch.groupValues[1].toLongOrNull() ?: 0L) * 60_000L
      }
      val secMatch = Regex("(\\d+)\\s*(?:s|sec|seconds?)\\b", RegexOption.IGNORE_CASE).find(timeStr)
      if (secMatch != null) {
        totalMs += (secMatch.groupValues[1].toLongOrNull() ?: 0L) * 1_000L
      }
      val msMatch = Regex("(\\d+)\\s*(?:ms|millis|milliseconds?)\\b", RegexOption.IGNORE_CASE).find(timeStr)
      if (msMatch != null) {
        totalMs += (msMatch.groupValues[1].toLongOrNull() ?: 0L)
      }
      if (totalMs == 0L) {
        val pureDigits = timeStr.trim().toLongOrNull()
        if (pureDigits != null) {
          totalMs = pureDigits
        }
      }
    } catch (t: Throwable) {
      TestLogger.log("Warning: failed to parse duration string '%s': %s", timeStr, t.message)
    }
    return totalMs
  }

  private fun commitSpanImmediately(apiName: String, duration: Long) {
    try {
      val count = metricCounts.compute(apiName) { _, old -> (old ?: 0) + 1 } ?: 1
      val metricName = if (count == 1) "E2E_$apiName" else "E2E_${apiName}_$count"

      committer.commit(metricName, duration)
    } catch (t: Throwable) {
      TestLogger.log("Warning: failed to commit immediate span for $apiName: ${t.message}")
    }
  }

  private fun createBenchmarkMetadata(): Triple<Benchmark, Analyzer, Long> {
    val testTarget = System.getenv("TEST_TARGET")
    val testName =
      if (testTarget != null && testTarget.contains(":")) {
        testTarget.substring(testTarget.lastIndexOf(':') + 1)
      } else {
        System.getenv("TEST_NAME") ?: "AsDriverIntegrationTest"
      }

    val benchmark =
      Benchmark.Builder(testName)
        .setProject("Android Studio Performance")
        .setDescription("Dynamic performance metrics collected for $testName")
        .build()

    val analyzer =
      WindowDeviationAnalyzer.Builder()
        .setMetricAggregate(Analyzer.MetricAggregate.MEDIAN)
        .setRunInfoQueryLimit(50)
        .addMedianTolerance(WindowDeviationAnalyzer.MedianToleranceParams.Builder().build())
        .build()

    return Triple(benchmark, analyzer, System.currentTimeMillis())
  }

  @JvmStatic
  fun commitDynamicPerfBenchmarks() {
    try {
      val unclosed = activeSpans.filterValues { it.isNotEmpty() }.keys
      if (unclosed.isNotEmpty()) {
        TestLogger.log("Warning: test ended with unclosed spans: $unclosed")
      }
      TestLogger.log("Dynamic perflogger benchmark metrics were committed in real-time")
    } catch (t: Throwable) {
      TestLogger.log("Warning: error during commitDynamicPerfBenchmarks teardown: ${t.message}")
    } finally {
      clear()
    }
  }

  private fun logDynamicMetric(benchmark: Benchmark, analyzer: Analyzer, timestampMs: Long, metricName: String, valueMs: Long) {
    if (valueMs >= 0) {
      try {
        val metric = Metric(metricName)
        metric.setAnalyzers(benchmark, setOf(analyzer))
        metric.addSamples(benchmark, Metric.MetricSample(timestampMs, valueMs))
        metric.commit()
      } catch (ignored: Exception) {}
    }
  }
}
