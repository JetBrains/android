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
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.diagnostics.jfr.CallTreeAggregator
import com.android.tools.idea.diagnostics.jfr.EventFilter
import com.android.tools.idea.diagnostics.jfr.JfrReportGenerator
import com.android.tools.idea.diagnostics.jfr.JfrReportManager
import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.JfrTypingLatencyConfig
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.LatencyListener
import jdk.jfr.consumer.RecordedEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object JfrTypingLatencyReports {
  private const val SERVER_FLAG_NAME = "diagnostics/jfr_typing_latency"
  private const val DEFAULT_MAX_REPORTING_LENGTH_BYTES = 200_000
  private const val CALL_TREES_FIELD = "callTrees"
  private const val KEYSTROKE_COUNT_FIELD = "keystrokes"
  private val DEFAULT_TYPING_TIMEOUT = 2.seconds
  private val DEFAULT_SESSION_TIMEOUT = 10.seconds
  private val DEFAULT_COOLDOWN_TIMEOUT = 10.minutes
  private val DEFAULT_LATENCY_THRESHOLD = 1.seconds

  const val REPORT_TYPE = "JFR-TypingLatency"
  val FIELDS = listOf(CALL_TREES_FIELD, KEYSTROKE_COUNT_FIELD)

  @JvmStatic
  fun createReportManager(parentDisposable: Disposable, serverFlagService: ServerFlagService): JfrReportManager<*> {
    val config: JfrTypingLatencyConfig? = serverFlagService.getProtoOrNull(SERVER_FLAG_NAME, JfrTypingLatencyConfig.getDefaultInstance())
    val maxReportLengthBytes =
      if (config?.hasMaxReportLengthBytes() == true) config.maxReportLengthBytes else DEFAULT_MAX_REPORTING_LENGTH_BYTES

    return JfrReportManager.create({ MyReportGenerator(maxReportLengthBytes) }) {
      val stopCapture = fun(keystrokes: Int) {
        this.currentReportGenerator?.keystrokeCount = keystrokes
        this.stopCapture()
      }
      val coroutineScope = AndroidCoroutineScope(parentDisposable, uiThread)
      val latencyListener = MyLatencyListener(config, ::startCapture, stopCapture, coroutineScope)
      ApplicationManager.getApplication().messageBus.connect(parentDisposable).subscribe(LatencyListener.TOPIC, latencyListener)
    }
  }

private class MyReportGenerator(private val maxReportLengthBytes: Int) : JfrReportGenerator(REPORT_TYPE, EventFilter.CPU_SAMPLES) {
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
  internal class MyLatencyListener(
    config: JfrTypingLatencyConfig?,
    private val startCapture: () -> Unit,
    private val stopCapture: (keystrokeCount: Int) -> Unit,
    private val coroutineScope: CoroutineScope,
  ) : LatencyListener {
    private val typingTimeout =
      if (config?.hasTypingTimeoutMillis() == true) config.typingTimeoutMillis.milliseconds else DEFAULT_TYPING_TIMEOUT
    private val sessionTimeout =
      if (config?.hasSessionTimeoutMillis() == true) config.sessionTimeoutMillis.milliseconds else DEFAULT_SESSION_TIMEOUT
    private val cooldownTimeout =
      if (config?.hasCooldownTimeoutMillis() == true) config.cooldownTimeoutMillis.milliseconds else DEFAULT_COOLDOWN_TIMEOUT

    private val latencyReportingThreshold =
      if (config?.hasLatencyReportingThresholdMillis() == true) config.latencyReportingThresholdMillis.milliseconds
      else DEFAULT_LATENCY_THRESHOLD

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

    private fun TimeoutType.timeout() = when (this) {
      TimeoutType.TYPING -> typingTimeout
      TimeoutType.SESSION -> sessionTimeout
      TimeoutType.COOLDOWN -> cooldownTimeout
    }

    // State variables are only accessed on the UI Thread, so do not need any locking.
    private var state: State = State.WAITING_TO_RECORD
    private var keystrokeCount: Int = 0
    private val timeouts: MutableMap<TimeoutType, Job> = mutableMapOf()

    @UiThread
    override fun recordTypingLatency(editor: Editor, action: String?, latencyMs: Long) {
      when (state) {
        State.WAITING_TO_RECORD -> if (latencyMs.milliseconds > latencyReportingThreshold) {
          // Start recording when there's a latency above the threshold.
          keystrokeCount = 1
          cancelAllTimeouts()
          startTimeout(TimeoutType.TYPING)
          startTimeout(TimeoutType.SESSION)
          startCapture()

          state = State.RECORDING
        }

        State.RECORDING -> if (latencyMs.milliseconds > latencyReportingThreshold) {
          keystrokeCount++
          startTimeout(TimeoutType.TYPING)
        }
        else {
          doStopRecording()
        }

        State.COOLDOWN -> {} // Do nothing.
      }
    }

    @UiThread
    private fun startTimeout(timeoutType: TimeoutType) {
      // First cancel and remove any previous instance of this timeout type. There should only ever be one of each timeout type running.
      timeouts.remove(timeoutType)?.cancel()

      timeouts[timeoutType] = coroutineScope.launch {
        delay(timeoutType.timeout())
        handleTimeout(timeoutType)
      }
    }

    @UiThread
    private fun handleTimeout(timeoutType: TimeoutType) {
      when (timeoutType) {
        TimeoutType.TYPING, TimeoutType.SESSION -> {
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
      timeouts.values.forEach { it.cancel() }
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
}
