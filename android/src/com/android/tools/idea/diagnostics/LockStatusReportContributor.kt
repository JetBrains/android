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

import com.android.tools.idea.diagnostics.freeze.FreezeGraph
import java.lang.management.ManagementFactory
import java.lang.management.ThreadInfo
import java.util.function.BiConsumer

class LockStatusReportContributor : DiagnosticReportContributor {
  private var report = ""
  private val threadBean = ManagementFactory.getThreadMXBean()

  override fun setup(configuration: DiagnosticReportConfiguration) {}

  override fun startCollection(timeElapsedSoFarMs: Long) {
    val allThreads = threadBean.getThreadInfo(threadBean.allThreadIds, true, true)
    val freezeGraph = FreezeGraph.analyzeThreads(allThreads)
    val sb = StringBuilder()
    try {
      sb.appendFreezeGraphAnalysis(freezeGraph)
      sb.appendLine()
      sb.appendJvmAnalysis()
      report = sb.toString()
    }
    catch (t: Throwable) {
      report = "ERROR: ${t.stackTraceToString()}\n$sb"
    }
  }

  override fun stopCollection(totalDurationMs: Long) {
  }

  override fun getReport(): String {
    return report
  }

  override fun generateReport(saveReportCallback: BiConsumer<String, String>) {
    saveReportCallback.accept("lockDiagnostics", report)
  }

  private fun StringBuilder.appendJvmAnalysis() {
    val deadlockedThreads = threadBean.findDeadlockedThreads()?.toSet() ?: emptySet()
    val allThreads = threadBean.dumpAllThreads(true, true)
    val awtThread = allThreads.firstOrNull(DiagnosticUtils.Companion::isAwtThread)
    appendLine()
    appendLine("ANALYSIS - JVM")
    appendLine("==============")
    appendLine("AWT thread ID: ${awtThread?.threadId}")
    if (deadlockedThreads.isEmpty()) {
      appendLine("JVM did not detect any deadlocks")
    }
    else {
      val awtDeadlockedString = if (awtThread != null && deadlockedThreads.contains(awtThread.threadId)) "AWT#${awtThread.threadId}"
      else "background"
      appendLine("JVM detected a deadlock ($awtDeadlockedString)")
      appendLine("Deadlocked threads: [${deadlockedThreads.joinToString(", ")}]")
    }
    for (threadInfo in allThreads) {
      val lockInfo = threadInfo.lockInfo
      val lockedMonitors = threadInfo.lockedMonitors
      val lockedSynchronizers = threadInfo.lockedSynchronizers
      if (lockInfo != null && threadInfo.lockOwnerId != -1L) {
        append("Thread #${threadInfo.threadId} blocked on ")
        append("${lockInfo.className}@${lockInfo.identityHashCode} owned by thread #${threadInfo.lockOwnerId}")
        append(": ${threadInfo.threadState}")
        if (deadlockedThreads.contains(threadInfo.threadId)) {
          append(" - DEADLOCKED")
        }
        appendLine()
      }
      else if (lockedMonitors.isNotEmpty() || lockedSynchronizers.isNotEmpty()) {
        appendLine("Thread #${threadInfo.threadId}: ${threadInfo.threadState}")
      }
      else {
        continue
      }
      if (lockedMonitors.isNotEmpty()) {
        appendLine("  Locked monitors:")
        for (lockedMonitor in lockedMonitors) {
          val stackDepth = lockedMonitor.lockedStackDepth
          append("  * ${lockedMonitor.className}@${lockedMonitor.identityHashCode} at ${lockedMonitor.lockedStackFrame}")
          if (stackDepth >= 0) {
            append("@$stackDepth")
          }
          appendLine()
        }
      }
      if (lockedSynchronizers.isNotEmpty()) {
        appendLine("  Locked synchronizers:")
        for (lockedSynchronizer in lockedSynchronizers) {
          appendLine("  * ${lockedSynchronizer.className}@${lockedSynchronizer.identityHashCode}")
        }
      }
    }
  }

  private fun StringBuilder.appendFreezeGraphAnalysis(freezeGraph: FreezeGraph) {
    appendLine("ANALYSIS - FREEZE GRAPH")
    appendLine("=======================")
    appendLine("AWT thread deadlocked: " + freezeGraph.awtDeadlocked)
    appendLine("Summary:")
    append(freezeGraph.summary)
  }
}

