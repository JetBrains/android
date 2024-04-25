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
import com.android.tools.analytics.toProto
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.EditorFileType
import com.google.wireless.android.sdk.stats.TypingLatencyStats
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.LatencyListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.util.application
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.HdrHistogram.SingleWriterRecorder

/**
 * Tracks typing latency across all file types. To log an [AndroidStudioEvent] with the collected
 * data, call [reportTypingLatency].
 */
@Service(Service.Level.APP)
class TypingLatencyTracker(private val coroutineScope: CoroutineScope) : Disposable {

  init {
    // Report stats every hour.
    coroutineScope.launch(Dispatchers.Default) {
      while (true) {
        delay(1.hours)
        reportTypingLatency()
      }
    }

    // Listen for any typing latency data from the platform.
    application.messageBus.connect(this).subscribe(LatencyListener.TOPIC, Listener())
  }

  override fun dispose() {
    // Report final results on shutdown.
    reportTypingLatency()
  }

  /**
   * Maps file types to latency recorders. We use [SingleWriterRecorder] to allow thread-safe read
   * access from background threads.
   */
  private val latencyRecorders = ConcurrentHashMap<EditorFileType, SingleWriterRecorder>()

  private inner class Listener : LatencyListener {
    override fun recordTypingLatency(editor: Editor, action: String, latencyMs: Long) {
      if (latencyMs < 0) return // Can happen due to non-monotonic system time, for example.
      val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return

      coroutineScope.launch(Dispatchers.EDT) {
        val fileType = getEditorFileTypeForAnalytics(file, editor.project)
        val recorder = latencyRecorders.computeIfAbsent(fileType) { SingleWriterRecorder(1) }
        // This runs on the EDT to ensure a single writer, but is thread-safe with respect to a
        // background thread reading the histogram.
        recorder.recordValue(latencyMs)
      }
    }
  }

  /**
   * Logs an [AndroidStudioEvent] with typing latency information. Resets statistics so that
   * latencies are not double-counted in the next report.
   */
  private fun reportTypingLatency() {
    val allStats = TypingLatencyStats.newBuilder()
    for ((fileType, recorder) in latencyRecorders) {
      val histogram =
        recorder.intervalHistogram // Automatically resets statistics for this recorder.
      if (histogram.totalCount == 0L) {
        continue
      }
      val record =
        TypingLatencyStats.LatencyRecord.newBuilder().also {
          it.fileType = fileType
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

  companion object {
    fun ensureSubscribed() {
      // Instantiating the service causes it to subscribe to the events it needs, and set up
      // appropriate reporting. Since it's an application service, it will only ever be instantiated
      // once.
      service<TypingLatencyTracker>()
    }
  }
}
