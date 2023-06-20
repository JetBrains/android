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
package com.android.tools.idea.diagnostics.jfr.analysis

import com.android.tools.idea.diagnostics.TruncatingStringBuilder
import com.android.tools.idea.diagnostics.jfr.analysis.IdleStacks.Companion.isIdle
import com.android.tools.idea.diagnostics.jfr.analysis.IdleStacks.Companion.isIgnoredThread
import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.consumer.RecordingFile
import java.nio.file.Path
import java.time.Instant

private data class Sample(val thread: String, val time: Instant, val duration: Long, val stackTrace: List<String>, val truncated: Boolean)

private class Freeze(val startTime: Long, val endTime: Long) {
  val samples = mutableMapOf<Long, MutableList<Sample>>() // thread id -> samples for that thread

  fun containsInstant(i: Instant) = i.toEpochMilli() in startTime until endTime

  fun aggregateCallTrees(): MutableMap<Long, CallTree> {
    val trees = mutableMapOf<Long, CallTree>()
    samples.forEach { (tid, sampleList) ->
      val root = CallTree("")
      sampleList.forEach { sample ->
        if (sample.truncated) {
          root.truncatedSampleCount++
        } else {
          root.addStacktrace(sample.stackTrace, sample.duration)
        }
      }
      root.sort()
      trees[tid] = root
    }
    return trees
  }
}

class JfrAnalyzer {
  fun analyze(path: Path): String {
    val recordingFile = RecordingFile(path)
    val events = mutableListOf<RecordedEvent>()
    val threadIdToName = mutableMapOf<Long, String>()
    var edtId = 0L
    var freezeEvent: RecordedEvent? = null // there should only ever be one per recording
    while (recordingFile.hasMoreEvents()) {
      val event = recordingFile.readEvent()
      events.add(event)
      if (event.eventType.name == FREEZE_EVENT_NAME) {
        freezeEvent = event
      } else if (event.eventType.name in SAMPLING_EVENT_NAMES) {
        val thread = event.getThread("sampledThread")
        threadIdToName[thread.javaThreadId] = thread.javaName
        if (thread.javaName == EDT) edtId = thread.javaThreadId
      }
    }
    if (freezeEvent == null) return "No freeze event."
    val freeze = Freeze(freezeEvent.startTime.toEpochMilli() - freezeEvent.getInt("startOffsetMs"), freezeEvent.endTime.toEpochMilli())
    events.filter { it.eventType.name in SAMPLING_EVENT_NAMES }
      .groupBy { it.getThread("sampledThread").javaThreadId }
      .forEach { (tid, sampleEvents) ->
        val threadName = threadIdToName[tid]!!
        if (isIgnoredThread(threadName)) return@forEach
        val samples = mutableListOf<Sample>()
        val sortedEvents = sampleEvents.filter { freeze.containsInstant(it.startTime) }.sortedBy { it.startTime }
        sortedEvents.forEachIndexed { i, e ->
          val nextEventTime = if (i+1 == sortedEvents.size) freeze.endTime else sortedEvents[i+1].startTime.toEpochMilli()
          var stacktrace = e.getStacktrace()
          if (isIdle(threadName, stacktrace)) {
            stacktrace = listOf("IDLE.IDLE(Unknown Source)")
          }
          samples.add(Sample(threadName, e.startTime, nextEventTime - e.startTime.toEpochMilli(), stacktrace, e.stackTrace.isTruncated))
        }
        if (samples.isNotEmpty()) freeze.samples[tid] = samples
      }

    val callTrees = freeze.aggregateCallTrees()
    val edtTree = callTrees.remove(edtId)

    fun getThreadReport(tid: Long, tree: CallTree) = buildString {
      append("${threadIdToName[tid]}, TID: $tid [${tree.time}ms] (${tree.sampleCount} samples")
      append(if (tree.truncatedSampleCount > 0) "; omitted ${tree.truncatedSampleCount} truncated stacks)" else ")")
      append("\n$tree\n")
    }

    val sb = TruncatingStringBuilder(MAX_REPORT_LENGTH_BYTES, "\n...report truncated...")
    if (edtTree != null) sb.append(getThreadReport(edtId, edtTree))
    callTrees.entries.toList().sortedByDescending { it.value.numNodesAboveCutoff() }.forEach { (tid, tree) ->
      sb.append(getThreadReport(tid, tree))
    }
    return sb.toString()
  }

  private fun RecordedEvent.getStacktrace() = stackTrace.frames.map { frame ->
    "${frame.method.type.name}.${frame.method.name}" + if (frame.lineNumber == -1) "(Unknown Source)" else "(?:${frame.lineNumber})"
  }

  companion object {
    private val SAMPLING_EVENT_NAMES = listOf("jdk.ExecutionSample", "jdk.NativeMethodSample")
    private const val FREEZE_EVENT_NAME = "com.android.tools.idea.FreezeEvent"
    private const val EDT = "AWT-EventQueue-0"
    private const val MAX_REPORT_LENGTH_BYTES = 200_000
  }
}