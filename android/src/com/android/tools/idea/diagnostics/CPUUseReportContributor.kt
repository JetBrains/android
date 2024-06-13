/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.diagnostics

import java.lang.management.ManagementFactory
import java.util.Arrays
import java.util.function.BiConsumer


class CPUUseReportContributor : DiagnosticReportContributor {
  private var report = "Disabled"
  private var enabled = false

  private lateinit var threadIds: LongArray
  private lateinit var initialCpuTimePerThreadId: LongArray

  private var initialUptimeMs: Long = 0L
  private var previousCpuTimeEnabledState: Boolean? = null;

  override fun setup(configuration: DiagnosticReportConfiguration) {}

  override fun startCollection(timeElapsedSoFarMs: Long) {
    if (!threadBean.isThreadCpuTimeSupported) return
    try {
      previousCpuTimeEnabledState = threadBean.isThreadCpuTimeEnabled
      threadBean.isThreadCpuTimeEnabled = true
      val allThreads: Set<Thread> = Thread.getAllStackTraces().keys
      threadIds = allThreads.stream().mapToLong { obj: Thread -> obj.id }.sorted().toArray()
      initialUptimeMs = runtimeBean.uptime
      initialCpuTimePerThreadId = getCpuTimeForThreadIds(threadIds)
      enabled = true
    }
    catch (t: Throwable) {
      report = "ERROR: $t"
      enabled = false
    }
  }

  override fun stopCollection(totalDurationMs: Long) {
    previousCpuTimeEnabledState?.let {
      threadBean.isThreadCpuTimeEnabled = it
    }
    if (!enabled) return

    val durationMs = runtimeBean.uptime - initialUptimeMs

    if (durationMs <= 0) {
      report = "ERROR: Duration = ${durationMs}ms"
      return
    }
    val cpuCount = osBean.availableProcessors
    val currentCpuTimePerThreadId = getCpuTimeForThreadIds(threadIds)
    val cpuUsagePerThreadId: DoubleArray = DoubleArray(threadIds.size)
    for (i in threadIds.indices) {
      // Ignore if thread is not alive anymore
      if (initialCpuTimePerThreadId[i] == -1L ||
        currentCpuTimePerThreadId[i] == -1L) {
        cpuUsagePerThreadId[i] = -1.0
        continue
      }
      val elapsedCpuNs: Long = currentCpuTimePerThreadId[i] - initialCpuTimePerThreadId[i]
      val elapsedCpuMs: Long = elapsedCpuNs / 1_000_000

      val threadCpuUse = (100.0 * elapsedCpuMs / durationMs).coerceIn(0.0, 100.0)
      cpuUsagePerThreadId[i] = threadCpuUse
    }
    val sb = StringBuilder()
    val threadsWithCpuUse =
      threadIds.zip(cpuUsagePerThreadId.toTypedArray())
      .sortedByDescending { it.first }
      .sortedByDescending { it.second }

    for ((id, cpuUse) in threadsWithCpuUse) {
      if (cpuUse < 0.01) continue
      val threadInfo = threadBean.getThreadInfo(id)
      val cpuUseString = String.format(null, "%.2f", cpuUse)
      if (threadInfo != null) {
        sb.appendLine("Thread #$id - ${threadInfo.threadName}: $cpuUseString%")
      } else {
        sb.appendLine("Thread #$id (dead): $cpuUseString%")
      }
    }
    val totalLoad = Arrays.stream(cpuUsagePerThreadId).filter { d -> d >= 0.0 }.sum()
    val totalLoadString = String.format(null, "%.2f", totalLoad)
    sb.appendLine()
    sb.appendLine("Duration by uptime: ${durationMs}ms")
    sb.appendLine("CPU count: $cpuCount")
    sb.appendLine("Total CPU load: $totalLoadString%")
    report = sb.toString()
  }

  override fun getReport(): String {
    return report
  }

  override fun generateReport(saveReportCallback: BiConsumer<String, String>) {
    saveReportCallback.accept("cpuUseDiagnostics", report)
  }

  companion object {
    private val threadBean = ManagementFactory.getThreadMXBean()
    private val runtimeBean = ManagementFactory.getRuntimeMXBean()
    private val osBean = ManagementFactory.getOperatingSystemMXBean()
    private fun getCpuTimeForThreadIds(ids: LongArray): LongArray {
      val longArray = LongArray(ids.size)
      for (i in ids.indices) {
        val id = ids[i]
        longArray[i] = threadBean.getThreadCpuTime(id)
      }
      return longArray
    }
  }
}
