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
package com.android.tools.idea.diagnostics.jfr.reports

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.diagnostics.jfr.CallTreeAggregator
import com.android.tools.idea.diagnostics.jfr.EventFilter
import com.android.tools.idea.diagnostics.jfr.JfrReportGenerator
import com.android.tools.idea.diagnostics.jfr.JfrReportManager
import com.android.tools.idea.model.MergedManifestSnapshotComputeListener
import com.android.tools.idea.stats.ManifestMergerStatsTracker
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import jdk.jfr.consumer.RecordedEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

object JfrManifestMergerReports {
  private const val MAX_REPORT_LENGTH_BYTES = 200_000
  private const val CALL_TREES_FIELD = "callTrees"
  @VisibleForTesting
  internal val REPORTING_THRESHOLD = 5.seconds

  const val REPORT_TYPE = "JFR-ManifestMerger"
  val FIELDS = listOf(CALL_TREES_FIELD)

  @JvmStatic
  fun createReportManager(parentDisposable: Disposable): JfrReportManager<*> = JfrReportManager.create(::MyReportGenerator) {
    val coroutineScope = AndroidCoroutineScope(parentDisposable,
                                               Executors.newSingleThreadScheduledExecutor().asCoroutineDispatcher())
    val listener = MyMergedManifestSnapshotComputeListener(::startCapture, ::stopCapture, coroutineScope)
    ApplicationManager.getApplication().messageBus.connect(parentDisposable)
      .subscribe(MergedManifestSnapshotComputeListener.TOPIC, listener)
  }

  private class MyReportGenerator : JfrReportGenerator(
    REPORT_TYPE, EventFilter.CPU_SAMPLES, startOffsetMs = -REPORTING_THRESHOLD.inWholeMilliseconds) {
    private val callTreeAggregator = CallTreeAggregator(CallTreeAggregator.THREAD_FILTER_ALL)

    override fun accept(e: RecordedEvent, c: Capture) {
      callTreeAggregator.accept(e)
    }

    override fun captureCompleted(c: Capture) {
      callTreeAggregator.processBatch(c.end!!)
    }

    override fun generateReport(): Map<String, String> {
      return mapOf(
        CALL_TREES_FIELD to callTreeAggregator.generateReport(MAX_REPORT_LENGTH_BYTES),
      )
    }
  }

  /**
   * Listens to manifest merge start and end events, and starts a JFR report if a merge takes longer than the defined threshold.
   */
  @VisibleForTesting
  internal class MyMergedManifestSnapshotComputeListener(
    private val startCapture: () -> Unit,
    private val stopCapture: () -> Unit,
    private val coroutineScope: CoroutineScope,
  ) : MergedManifestSnapshotComputeListener {

    @AnyThread
    override fun snapshotCreationStarted(token: Any, startTimestampMillis: Long) {
      coroutineScope.launch { handleSnapshotCreationStarted(token) }
    }

    @AnyThread
    override fun snapshotCreationEnded(token: Any,
                                       startTimestampMillis: Long,
                                       endTimestampMillis: Long,
                                       result: ManifestMergerStatsTracker.MergeResult) {
      coroutineScope.launch { handleSnapshotCreationEnded(token) }
    }

    // State variables are only accessed on a single worker thread, so do not need to be synchronized.
    // Each individual merge is represented by a token object, which will be the same for the calls to handleSnapshotCreationStarted and
    // handleSnapshotCreationEnded. This allows us to track multiple simultaneous merges. The token is used as the dictionary key for
    // storing timeout futures, and is used to identify which single merge controls the in-progress report.
    private val startReportTimeouts: MutableMap<Any, Job> = mutableMapOf()
    private var inProgressReportToken: Any? = null

    @WorkerThread
    private fun handleSnapshotCreationStarted(token: Any) {
      startReportTimeouts[token] = coroutineScope.launch {
        delay(REPORTING_THRESHOLD)
        handleSnapshotReportingTimeout(token)
      }
    }

    @WorkerThread
    private fun handleSnapshotCreationEnded(token: Any) {
      // Cancel any waiting timeout.
      startReportTimeouts.remove(token)?.cancel()

      // If there's a report in progress for this token, end it now.
      if (inProgressReportToken == token) {
        inProgressReportToken = null
        stopCapture()
      }
    }

    @WorkerThread
    private fun handleSnapshotReportingTimeout(token: Any) {
      // If the timeout was already removed by handleSnapshotCreationEnded, then don't start a report.
      startReportTimeouts.remove(token) ?: return

      // Since the snapshot is still running past the threshold, start a report if there's not already one in progress.
      if (inProgressReportToken == null) {
        inProgressReportToken = token
        startCapture()
      }
    }
  }
}
