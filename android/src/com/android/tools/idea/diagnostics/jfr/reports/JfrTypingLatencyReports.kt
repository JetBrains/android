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

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.diagnostics.jfr.CallTreeAggregator
import com.android.tools.idea.diagnostics.jfr.EventFilter
import com.android.tools.idea.diagnostics.jfr.JfrReportGenerator
import com.android.tools.idea.diagnostics.jfr.JfrReportManager
import com.android.tools.idea.diagnostics.jfr.reports.JfrTypingLatencyReports.Companion.CALL_TREES_FIELD
import com.android.tools.idea.diagnostics.jfr.reports.JfrTypingLatencyReports.Companion.DEFAULT_COOLDOWN_TIMEOUT_MILLIS
import com.android.tools.idea.diagnostics.jfr.reports.JfrTypingLatencyReports.Companion.DEFAULT_LATENCY_THRESHOLD_MILLIS
import com.android.tools.idea.diagnostics.jfr.reports.JfrTypingLatencyReports.Companion.DEFAULT_SESSION_TIMEOUT_MILLIS
import com.android.tools.idea.diagnostics.jfr.reports.JfrTypingLatencyReports.Companion.DEFAULT_TYPING_TIMEOUT_MILLIS
import com.android.tools.idea.diagnostics.jfr.reports.JfrTypingLatencyReports.Companion.KEYSTROKE_COUNT_FIELD
import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.JfrTypingLatencyConfig
import com.google.common.annotations.VisibleForTesting
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.LatencyListener
import jdk.jfr.consumer.RecordedEvent
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class JfrTypingLatencyReports {
  companion object {
    const val REPORT_TYPE = "JFR-TypingLatency"

    const val SERVER_FLAG_NAME = "diagnostics/jfr_typing_latency"

    const val DEFAULT_MAX_REPORTING_LENGTH_BYTES = 200_000
    const val DEFAULT_TYPING_TIMEOUT_MILLIS = 2_000L // 2 seconds
    const val DEFAULT_SESSION_TIMEOUT_MILLIS = 10_000L // 10 seconds
    const val DEFAULT_COOLDOWN_TIMEOUT_MILLIS = 1_000L * 60L * 10L // 10 minutes

    const val DEFAULT_LATENCY_THRESHOLD_MILLIS = 1_000L // 1 second

    const val CALL_TREES_FIELD = "callTrees"
    const val KEYSTROKE_COUNT_FIELD = "keystrokes"
    val FIELDS = listOf(CALL_TREES_FIELD, KEYSTROKE_COUNT_FIELD)

    fun createReportManager(serverFlagService: ServerFlagService): JfrReportManager<*> {
      val config: JfrTypingLatencyConfig? = serverFlagService.getProtoOrNull(SERVER_FLAG_NAME, JfrTypingLatencyConfig.getDefaultInstance())
      val maxReportLengthBytes =
        if (config?.hasMaxReportLengthBytes() == true) config.maxReportLengthBytes else DEFAULT_MAX_REPORTING_LENGTH_BYTES

      return JfrReportManager.create({ MyReportGenerator(maxReportLengthBytes) }) {
        val stopCapture = fun(keystrokes: Int) {
          this.currentReportGenerator?.keystrokeCount = keystrokes
          this.stopCapture()
        }
        val latencyListener = MyLatencyListener(config, ::startCapture, stopCapture)
        ApplicationManager.getApplication().messageBus.connect().subscribe(LatencyListener.TOPIC, latencyListener)
      }
    }
  }
}

private class MyReportGenerator(private val maxReportLengthBytes: Int) : JfrReportGenerator(JfrTypingLatencyReports.REPORT_TYPE,
                                                                                            EventFilter.CPU_SAMPLES, startOffsetMs = 0,
                                                                                            endOffsetMs = 0) {
  var keystrokeCount: Int = -1

  private val callTreeAggregator = CallTreeAggregator(CallTreeAggregator.THREAD_FILTER_ALL)

  override fun accept(e: RecordedEvent, c: Capture) {
    callTreeAggregator.accept(e)
  }

  override fun captureCompleted(c: Capture) {
    callTreeAggregator.processBatch(c.end!!)
  }

  override fun generateReport(): Map<String, String> {
    return mapOf(
      CALL_TREES_FIELD to callTreeAggregator.generateReport(maxReportLengthBytes),
      KEYSTROKE_COUNT_FIELD to keystrokeCount.toString())
  }
}

/**
 * A [LatencyListener] responsible for starting and stopping JFR reports related to typing latency.
 *
 * When we see a latency above our reporting threshold, we want to start a recording session.
 * That session should continue until one of the following is true:
 *   1. We see a latency below the threshold (indicating the lag has resolved)
 *   2. There are no typing events for a set amount of time (indicating the user has stopped typing)
 *   3. The session reaches a maximum length of time
 * After a session completes, we want to wait a "cooldown" amount of time before starting any new sessions.
 */
@VisibleForTesting
class MyLatencyListener(config: JfrTypingLatencyConfig?,
                        private val startCapture: () -> Unit,
                        private val stopCapture: (keystrokeCount: Int) -> Unit,
                        private val scheduler: ScheduledExecutorService = JobScheduler.getScheduler()) : LatencyListener {

  private val typingTimeoutMillis =
    if (config?.hasTypingTimeoutMillis() == true) config.typingTimeoutMillis else DEFAULT_TYPING_TIMEOUT_MILLIS
  private val sessionTimeoutMillis =
    if (config?.hasSessionTimeoutMillis() == true) config.sessionTimeoutMillis else DEFAULT_SESSION_TIMEOUT_MILLIS
  private val cooldownTimeoutMillis =
    if (config?.hasCooldownTimeoutMillis() == true) config.cooldownTimeoutMillis else DEFAULT_COOLDOWN_TIMEOUT_MILLIS

  private val latencyReportingThresholdMillis =
    if (config?.hasLatencyReportingThresholdMillis() == true) config.latencyReportingThresholdMillis else DEFAULT_LATENCY_THRESHOLD_MILLIS

  private enum class State {
    WAITING_TO_RECORD,
    RECORDING,
    COOLDOWN,
  }

  private enum class TimeoutType {
    /** Tracks the user's typing behavior. It's reset when a new keystroke comes in, and if it first it indicates they stopped typing. */
    TYPING,

    /** Tracks overall length of the recording session. */
    SESSION,

    /** Tracks amount of time between recording sessions. */
    COOLDOWN,
  }

  private fun TimeoutType.timeoutLengthMillis() = when (this) {
    TimeoutType.TYPING -> typingTimeoutMillis
    TimeoutType.SESSION -> sessionTimeoutMillis
    TimeoutType.COOLDOWN -> cooldownTimeoutMillis
  }

  // State variables are only accessed on the UI Thread, so do not need any locking.
  private var state: State = State.WAITING_TO_RECORD
  private var keystrokeCount: Int = 0
  private val timeouts: MutableMap<TimeoutType, Future<*>> = mutableMapOf()

  @UiThread
  override fun recordTypingLatency(editor: Editor, action: String?, latencyMs: Long) {
    when (state) {
      State.WAITING_TO_RECORD -> {
        if (latencyMs > latencyReportingThresholdMillis) {
          // Start recording when there's a latency above the threshold.
          keystrokeCount = 1
          cancelAllTimeouts()
          startTimeout(TimeoutType.TYPING)
          startTimeout(TimeoutType.SESSION)
          startCapture()

          state = State.RECORDING
        }
      }

      State.RECORDING -> {
        if (latencyMs > latencyReportingThresholdMillis) {
          keystrokeCount++
          startTimeout(TimeoutType.TYPING)
        }
        else {
          doStopRecording()
        }
      }

      State.COOLDOWN -> {
        // Do nothing.
      }
    }
  }

  @UiThread
  private fun startTimeout(timeoutType: TimeoutType) {
    // First cancel and remove any previous instance of this timeout type. There should only ever be one of each timeout type running at a
    // given time.
    timeouts.remove(timeoutType)?.cancel(/* mayInterruptIfRunning = */ false)

    timeouts[timeoutType] = scheduler.schedule(
      { invokeLater { handleTimeout(timeoutType) } },
      timeoutType.timeoutLengthMillis(),
      TimeUnit.MILLISECONDS)
  }

  @UiThread
  private fun handleTimeout(timeoutType: TimeoutType) {
    when (timeoutType) {
      TimeoutType.TYPING,
      TimeoutType.SESSION -> {
        if (state == State.RECORDING) doStopRecording()
      }

      TimeoutType.COOLDOWN ->
        if (state == State.COOLDOWN) {
          cancelAllTimeouts()
          state = State.WAITING_TO_RECORD
        }
    }
  }

  @UiThread
  fun cancelAllTimeouts() {
    for (future in timeouts.values) {
      future.cancel(/* mayInterruptIfRunning = */ false)
    }

    timeouts.clear()
  }

  @UiThread
  private fun doStopRecording() {
    stopCapture(keystrokeCount)
    keystrokeCount = 0
    cancelAllTimeouts()
    startTimeout(TimeoutType.COOLDOWN)

    state = State.COOLDOWN
  }
}
