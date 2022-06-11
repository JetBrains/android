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
package com.android.tools.idea.diagnostics.jfr

import com.android.tools.idea.diagnostics.TruncatingStringBuilder
import com.android.tools.idea.diagnostics.jfr.analysis.CallTree
import com.android.tools.idea.diagnostics.jfr.analysis.IdleStacks
import jdk.jfr.consumer.RecordedEvent
import java.time.Instant

private data class Sample(val thread: String, val time: Instant, var duration: Long, val stackTrace: List<String>)

class CallTreeAggregator(val threadFilter: (String) -> Boolean) {
  private val samples = mutableMapOf<Long, MutableList<Sample>>() // thread id -> samples for that thread
  private val threadIdToName = mutableMapOf<Long, String>()
  private val callTrees = mutableMapOf<Long, CallTree>(); // thread id -> call tree
  private var edtId = 0L

  fun accept(e: RecordedEvent) {
    if (e.eventType.name !in EventFilter.SAMPLING_EVENT_NAMES) return
    val thread = e.getThread("sampledThread")
    val tid = thread.javaThreadId
    val threadName = thread.javaName
    if (!threadFilter(threadName)) return
    threadIdToName[tid] = threadName
    if (threadName == EDT) edtId = tid

    if (IdleStacks.isIgnoredThread(threadName)) return
    var stacktrace = e.getStacktrace()
    if (IdleStacks.isIdle(threadName, stacktrace)) {
      stacktrace = listOf("IDLE.IDLE(Unknown Source)")
    }
    val sample = Sample(threadName, e.startTime, 0, stacktrace)
    samples.getOrPut(tid) { mutableListOf() }.add(sample)
  }

  fun processBatch(endTime: Instant) {
    sortSamplesAndComputeDurations(endTime)
    aggregate()
    samples.clear()
  }

  private fun getReportForThread(tid: Long, tree: CallTree): String {
    return "${threadIdToName[tid]}, TID: $tid [${tree.time}ms] (${tree.sampleCount})\n$tree\n"
  }

  fun generateReport(maxLengthBytes: Int): String {
    val edtTree = callTrees.remove(edtId)
    val sb = TruncatingStringBuilder(maxLengthBytes, "\n...report truncated...")
    if (edtTree != null) sb.append(getReportForThread(edtId, edtTree))
    callTrees.entries.toList().sortedByDescending { it.value.numNodesAboveCutoff() }.forEach { (tid, tree) ->
      sb.append(getReportForThread(tid, tree))
    }
    return sb.toString()
  }

  private fun sortSamplesAndComputeDurations(end: Instant) {
    samples.forEach { (_, sampleList) ->
      sampleList.sortBy { it.time }
      for (i in 0 until sampleList.size - 1) {
        sampleList[i].duration = sampleList[i+1].time.toEpochMilli() - sampleList[i].time.toEpochMilli()
      }
      sampleList.last().duration = end.toEpochMilli() - sampleList.last().time.toEpochMilli()
    }
  }

  private fun aggregate() {
    samples.forEach { (tid, sampleList) ->
      val root = callTrees[tid] ?: CallTree("")
      sampleList.forEach { sample ->
        root.addStacktrace(sample.stackTrace, sample.duration)
      }
      root.sort()
      callTrees[tid] = root
    }
  }

  private fun RecordedEvent.getStacktrace() = stackTrace.frames.map { frame ->
    buildString {
      append(frame.method.type.name)
      append(".")
      append(frame.method.name)
      if (frame.lineNumber == -1) {
        append("(Unknown Source)")
      } else {
        append("(?:${frame.lineNumber})")
      }
    }
  }

  companion object {
    private const val EDT = "AWT-EventQueue-0"
    val THREAD_FILTER_ALL: (String) -> Boolean = { true }
    val THREAD_FILTER_EDT_ONLY: (String) -> Boolean = { name -> name == EDT }
  }
}