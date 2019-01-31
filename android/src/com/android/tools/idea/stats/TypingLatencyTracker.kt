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
package com.android.tools.idea.stats

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.TypingLatencyTracker.reportTypingLatency
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.TypingLatencyStats
import com.intellij.ide.toProto
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.LatencyListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.HdrHistogram.SingleWriterRecorder
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks typing latency across all file types.
 * To log an [AndroidStudioEvent] with the collected data, call [reportTypingLatency].
 */
object TypingLatencyTracker : LatencyListener {

  /**
   * Maps file types to latency recorders.
   * We use [SingleWriterRecorder] to allow thread-safe read access from background threads.
   */
  private val latencyRecorders = ConcurrentHashMap<String, SingleWriterRecorder>()

  override fun recordTypingLatency(editor: Editor, action: String, latencyMs: Long) {
    // This runs on the EDT, but is thread-safe with respect to a background thread running reportTypingLatency().
    val fileType = FileDocumentManager.getInstance().getFile(editor.document)?.fileType?.name ?: return
    val recorder = latencyRecorders.computeIfAbsent(fileType) { SingleWriterRecorder(1) }
    recorder.recordValue(latencyMs)
  }

  /**
   * Logs an [AndroidStudioEvent] with typing latency information.
   * Resets statistics so that latencies are not double-counted in the next report.
   */
  fun reportTypingLatency() {
    val allStats = TypingLatencyStats.newBuilder()
    for ((fileType, recorder) in latencyRecorders) {
      val histogram = recorder.intervalHistogram // Automatically resets statistics for this recorder.
      if (histogram.totalCount == 0L) {
        continue
      }
      val convertedFileType = convertFileType(fileType)
      val record = TypingLatencyStats.LatencyRecord.newBuilder().also {
        it.fileType = convertedFileType
        it.totalKeysTyped = histogram.totalCount
        it.totalLatencyMs = (histogram.totalCount * histogram.mean).toLong()
        it.maxLatencyMs = histogram.maxValue
        it.histogram = histogram.toProto()
      }
      allStats.addLatencyRecords(record.build())
    }

    if (allStats.latencyRecordsCount == 0) {
      return
    }

    UsageTracker.log(
      AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.TYPING_LATENCY_STATS
        typingLatencyStats = allStats.build()
      }
    )
  }

  /** Converts from file type name to proto enum value. */
  private fun convertFileType(fileType: String): TypingLatencyStats.FileType = when (fileType) {
    // We use string literals here (rather than, e.g., JsonFileType.INSTANCE.name) to avoid unnecessary
    // dependencies on other plugins. Fortunately, these values are extremely unlikely to change.
    "JAVA" -> TypingLatencyStats.FileType.JAVA
    "Kotlin" -> TypingLatencyStats.FileType.KOTLIN
    "XML" -> TypingLatencyStats.FileType.XML
    "Groovy" -> TypingLatencyStats.FileType.GROOVY
    "Properties" -> TypingLatencyStats.FileType.PROPERTIES
    "JSON" -> TypingLatencyStats.FileType.JSON
    else -> TypingLatencyStats.FileType.UNKNOWN
  }
}
