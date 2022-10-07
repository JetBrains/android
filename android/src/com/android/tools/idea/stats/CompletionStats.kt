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
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.stats.CompletionStats.reportCompletionStats
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.EditorCompletionStats
import com.google.wireless.android.sdk.stats.EditorFileType
import com.intellij.application.subscribe
import com.intellij.codeInsight.completion.CompletionPhaseListener
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.HdrHistogram.SingleWriterRecorder
import org.jetbrains.android.AndroidPluginDisposable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Collects performance metrics on code completion, such as popup latency and completion insertion latency.
 * To log an [AndroidStudioEvent] with the collected data, call [reportCompletionStats].
 */
object CompletionStats {
  private const val MAX_LATENCY_MS = 60 * 1000

  // The EDT is the only mutator of each SingleWriterRecorder, although they may be read from a background thread.
  private val popupLatencyHistograms = ConcurrentHashMap<EditorFileType, SingleWriterRecorder>()
  private val completionLatencyHistograms = ConcurrentHashMap<EditorFileType, SingleWriterRecorder>()
  private val insertionLatencyHistograms = ConcurrentHashMap<EditorFileType, SingleWriterRecorder>()

  // Only the EDT accesses the mutable fields below.

  /** The most recent time at which code completion started or restarted. */
  private var completionStartMs: Long = -1

  /** Set to true while the completion popup is visible and additional items are still being computed. */
  private var waitingForAdditionalCompletions = false

  /** Deferred result containing the UNKNOWN file type, when the current completion session's file type can't be determined. */
  private val unknownFileType = CompletableDeferred(EditorFileType.UNKNOWN)

  /** The file type involved in the current code completion session. */
  private var activeFileType: Deferred<EditorFileType> = unknownFileType

  private val coroutineScope = AndroidCoroutineScope(AndroidPluginDisposable.getApplicationInstance())

  /**
   * Logs an [AndroidStudioEvent] with editor completion latency information.
   * Resets statistics so that latencies are not double-counted in the next report.
   * May be called from any thread.
   */
  fun reportCompletionStats() {
    val allStats = EditorCompletionStats.newBuilder()

    val allFileTypes = mutableSetOf<EditorFileType>()
    allFileTypes.addAll(popupLatencyHistograms.keys)
    allFileTypes.addAll(completionLatencyHistograms.keys)
    allFileTypes.addAll(insertionLatencyHistograms.keys)

    // Note: calling getIntervalHistogram() on a recorder also resets its statistics.
    for (fileType in allFileTypes) {
      val popupLatencies = popupLatencyHistograms[fileType]?.intervalHistogram?.takeIf { it.totalCount > 0L }
      val completionLatencies = completionLatencyHistograms[fileType]?.intervalHistogram?.takeIf { it.totalCount > 0L }
      val insertionLatencies = insertionLatencyHistograms[fileType]?.intervalHistogram?.takeIf { it.totalCount > 0L }

      if (popupLatencies == null && completionLatencies == null && insertionLatencies == null) {
        continue
      }

      val stats = EditorCompletionStats.Stats.newBuilder()
      stats.fileType = fileType

      if (popupLatencies != null) stats.popupLatency = popupLatencies.toProto()
      if (completionLatencies != null) stats.fullCompletionLatency = completionLatencies.toProto()
      if (insertionLatencies != null) stats.insertionLatency = insertionLatencies.toProto()

      allStats.addByFileType(stats.build())
    }

    if (allStats.byFileTypeCount == 0) {
      return
    }

    UsageTracker.log(
      AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.EDITOR_COMPLETION_STATS
        editorCompletionStats = allStats.build()
      }
    )
  }

  /** Registers lookup listeners. */
  class MyStartupActivity : StartupActivity, DumbAware {

    override fun runActivity(project: Project) {
      CompletionPhaseListener.TOPIC.subscribe(project, MyCompletionPhaseListener())

      // For each new lookup window we get the current file type and register our LookupListener.
      LookupManager.getInstance(project).addPropertyChangeListener { e ->
        if (e.propertyName != LookupManager.PROP_ACTIVE_LOOKUP) {
          return@addPropertyChangeListener
        }
        val lookup = e.newValue as? Lookup ?: return@addPropertyChangeListener
        val file = FileDocumentManager.getInstance().getFile(lookup.editor.document)
        activeFileType = when (file) {
          null -> unknownFileType
          else -> {
            // Capture a local reference to the project in the current thread in case the reference changes by the time the below executes.
            val project = lookup.editor.project
            coroutineScope.async { getEditorFileTypeForAnalytics(file, project) }
          }
        }
        lookup.addLookupListener(MyLookupListener())
      }
    }
  }

  /** Listens for completion start and finish times. */
  class MyCompletionPhaseListener : CompletionPhaseListener {

    override fun completionPhaseChanged(isCompletionRunning: Boolean) {
      if (isCompletionRunning) {
        // Completion just started.
        completionStartMs = System.currentTimeMillis()
      }
      else {
        // Completion just finished.
        // Note: waitingForAdditionalCompletions will be false in the case where lookup was canceled or interrupted.
        if (waitingForAdditionalCompletions) {
          waitingForAdditionalCompletions = false
          recordLatency(completionLatencyHistograms, System.currentTimeMillis() - completionStartMs)
        }
      }
    }
  }

  /** Listens for completion popup events, such as completion item insertion. */
  class MyLookupListener : LookupListener {
    private var itemSelectionStartMs: Long = -1

    override fun lookupShown(event: LookupEvent) {
      recordLatency(popupLatencyHistograms, System.currentTimeMillis() - completionStartMs)
      waitingForAdditionalCompletions = true
    }

    override fun lookupCanceled(event: LookupEvent) {
      waitingForAdditionalCompletions = false
    }

    override fun beforeItemSelected(event: LookupEvent): Boolean {
      itemSelectionStartMs = System.currentTimeMillis()
      waitingForAdditionalCompletions = false
      return super.beforeItemSelected(event)
    }

    override fun itemSelected(event: LookupEvent) {
      recordLatency(insertionLatencyHistograms, System.currentTimeMillis() - itemSelectionStartMs)
    }
  }

  private fun recordLatency(histograms: ConcurrentMap<EditorFileType, SingleWriterRecorder>, latencyMs: Long) {
    if (latencyMs < 0 || latencyMs > MAX_LATENCY_MS) return

    // Capture a local reference to the file type in the current thread in case the reference changes by the time the below executes.
    val currentFileType = activeFileType
    coroutineScope.async(uiThread) {
      val fileType = currentFileType.await()
      val histogram = histograms.computeIfAbsent(fileType) { SingleWriterRecorder(1) }
      // This runs on the EDT to ensure a single writer, but is thread-safe with respect to a background thread reading the histogram.
      histogram.recordValue(latencyMs)
    }
  }
}
