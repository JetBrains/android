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

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.diagnostics.jfr.reports.JfrTypingLatencyReports.MyLatencyListener
import com.android.tools.idea.serverflags.protos.JfrTypingLatencyConfig
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.Editor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class JfrTypingLatencyReportsTest {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  private val mockEditor: Editor = mock()

  private val scheduler = TestCoroutineScheduler()
  private val dispatcher = StandardTestDispatcher(scheduler)
  private val testScope = TestScope(dispatcher)

  private val testLatencyConfig = JfrTypingLatencyConfig.newBuilder()
    .setMaxReportLengthBytes(200_000)
    .setTypingTimeoutMillis(2_000L) // 2 seconds
    .setSessionTimeoutMillis(10_000L) // 10 seconds
    .setCooldownTimeoutMillis(1_000L * 60L * 10L) // 10 minutes
    .setLatencyReportingThresholdMillis(1_000L)  // 1 second
    .build()

  private var startCaptureCalls = 0
  private val stopCaptureArgs: MutableList<Int> = mutableListOf()

  private val latencyListener = MyLatencyListener(testLatencyConfig, { startCaptureCalls++ }, stopCaptureArgs::add, testScope)

  @Test
  fun latencyListener_keystrokesAtThreshold() {
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis)
    scheduler.advanceTimeBy(100.milliseconds)
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis)
    scheduler.advanceTimeBy(100.milliseconds)
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis)
    scheduler.advanceTimeBy(100.milliseconds)
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis)
    scheduler.advanceTimeBy(100.milliseconds)

    assertThat(startCaptureCalls).isEqualTo(0)
    assertThat(stopCaptureArgs).isEmpty()
  }

  @Test
  fun latencyListener_recordingStoppedByKeystrokeUnderThreshold() {
    // First keystroke, above threshold.
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis + 1)

    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureArgs).isEmpty()

    // Second keystroke, above threshold.
    scheduler.advanceTimeBy(100.milliseconds)
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis + 1)

    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureArgs).isEmpty()

    // Third keystroke, at threshold
    scheduler.advanceTimeBy(100.milliseconds)
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis)

    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureArgs).containsExactly(2)
  }

  @Test
  fun latencyListener_recordingStoppedByTypingTimeout() {
    // First keystroke
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis + 1)

    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureArgs).isEmpty()

    // Second keystroke
    scheduler.advanceTimeBy(100.milliseconds)
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis + 1)

    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureArgs).isEmpty()

    // Third keystroke
    scheduler.advanceTimeBy(100.milliseconds)
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis + 1)

    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureArgs).isEmpty()

    // Advance just up to but not beyond the typing timeout. Nothing should be happening yet.
    scheduler.advanceTimeBy(testLatencyConfig.typingTimeoutMillis)

    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureArgs).isEmpty()

    // Advance to typing timeout.
    scheduler.advanceTimeBy(1.milliseconds)
    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureArgs).containsExactly(3)
  }

  @Test
  fun latencyListener_recordingStoppedBySessionTimeout() {
    // Choose a length between keystrokes just under the typing timeout to ensure it doesn't fire.
    // Simulate keystrokes until we're just under the session timeout threshold.
    val millisBetweenKeystrokes = testLatencyConfig.typingTimeoutMillis - 10
    val keystrokesBeforeSessionTimeout = (testLatencyConfig.sessionTimeoutMillis / millisBetweenKeystrokes).toInt()

    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis + 1)
    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureArgs).isEmpty()

    repeat(keystrokesBeforeSessionTimeout) {
      scheduler.advanceTimeBy(millisBetweenKeystrokes)
      latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis + 1)

      assertThat(startCaptureCalls).isEqualTo(1)
      assertThat(stopCaptureArgs).isEmpty()
    }

    // Wait until just before the session timeout.
    scheduler.advanceTimeBy(testLatencyConfig.sessionTimeoutMillis - scheduler.currentTime)

    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureArgs).isEmpty()

    // Advance to session timeout.
    scheduler.advanceTimeBy(1.milliseconds)
    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureArgs).containsExactly(keystrokesBeforeSessionTimeout + 1)
  }

  @Test
  fun latencyListener_cooldownPreventsAnotherReport() {
    // Send a keystroke above and then at the threshold, to trigger a report session followed by a cooldown.
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis + 1)
    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureArgs).isEmpty()

    scheduler.advanceTimeBy(100.milliseconds)
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis)
    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureArgs).containsExactly(1)

    val cooldownStartTime = scheduler.currentTime
    val cooldownEndTime = cooldownStartTime + testLatencyConfig.cooldownTimeoutMillis

    // Send a bunch of keystrokes above and at the threshold. None should cause a report to be started.
    // Include a keystroke 1 ms before the cooldown expires.
    scheduler.advanceTimeBy(100.milliseconds)
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis + 1)
    scheduler.advanceTimeBy(100.milliseconds)
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis + 1)
    scheduler.advanceTimeBy(100.milliseconds)
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis)
    scheduler.advanceTimeBy(100.milliseconds)
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis)
    scheduler.advanceTimeBy(100.milliseconds)
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis + 1)
    scheduler.advanceTimeBy(testLatencyConfig.sessionTimeoutMillis + 100)
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis + 1)
    scheduler.advanceTimeBy(testLatencyConfig.sessionTimeoutMillis + 100)
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis)

    scheduler.advanceTimeBy(cooldownEndTime - scheduler.currentTime)
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis + 1)

    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureArgs).containsExactly(1)

    // Advance to cooldown end.
    scheduler.advanceTimeBy(1.milliseconds)
    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureArgs).containsExactly(1)

    // Now that cooldown is over, a high-latency keystroke should start a new session (even without any additional time passing).
    latencyListener.recordTypingLatency(mockEditor, null, testLatencyConfig.latencyReportingThresholdMillis + 1)

    assertThat(startCaptureCalls).isEqualTo(2)
    assertThat(stopCaptureArgs).containsExactly(1)
  }

  private fun TestCoroutineScheduler.advanceTimeBy(duration: Duration) {
    advanceTimeBy(duration.inWholeMilliseconds)
  }
}
