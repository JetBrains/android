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
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import java.util.concurrent.TimeUnit

@RunWith(JUnit4::class)
class JfrTypingLatencyReportsTest {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  private val mockEditor: Editor = mock()
  private val mockStartCapture: () -> Unit = mock()
  private val mockStopCapture: (Int) -> Unit = mock()

  private val fakeScheduler = VirtualTimeScheduler()

  @Test
  fun latencyListener_keystrokesBelowThreshold() {
    val latencyListener = MyLatencyListener(mockStartCapture, mockStopCapture, fakeScheduler)

    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis - 1)
    fakeScheduler.advanceBy(100, TimeUnit.MILLISECONDS)
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis - 1)
    fakeScheduler.advanceBy(100, TimeUnit.MILLISECONDS)
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis - 1)
    fakeScheduler.advanceBy(100, TimeUnit.MILLISECONDS)
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis - 1)
    fakeScheduler.advanceBy(100, TimeUnit.MILLISECONDS)

    assertThat(fakeScheduler.actionsExecuted).isEqualTo(0)
    assertThat(fakeScheduler.actionsQueued).isEqualTo(0)
    verifyNoInteractions(mockStartCapture)
    verifyNoInteractions(mockStopCapture)
  }

  @Test
  fun latencyListener_recordingStoppedByKeystrokeUnderThreshold() {
    val latencyListener = MyLatencyListener(mockStartCapture, mockStopCapture, fakeScheduler)

    // First keystroke, above threshold.
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis + 1)

    verify(mockStartCapture).invoke()
    verifyNoInteractions(mockStopCapture)

    // Second keystroke, above threshold.
    fakeScheduler.advanceBy(100, TimeUnit.MILLISECONDS)
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis + 1)

    verifyNoMoreInteractions(mockStartCapture)
    verifyNoInteractions(mockStopCapture)

    // Third keystroke, below threshold
    fakeScheduler.advanceBy(100, TimeUnit.MILLISECONDS)
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis - 1)

    verifyNoMoreInteractions(mockStartCapture)
    verify(mockStopCapture).invoke(/* keystrokes = */ 2)
  }

  @Test
  fun latencyListener_recordingStoppedByTypingTimeout() {
    val latencyListener = MyLatencyListener(mockStartCapture, mockStopCapture, fakeScheduler)

    // First keystroke
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis + 1)

    verify(mockStartCapture).invoke()
    verifyNoInteractions(mockStopCapture)

    // Second keystroke
    fakeScheduler.advanceBy(100, TimeUnit.MILLISECONDS)
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis + 1)

    verifyNoMoreInteractions(mockStartCapture)
    verifyNoInteractions(mockStopCapture)

    // Third keystroke
    fakeScheduler.advanceBy(100, TimeUnit.MILLISECONDS)
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis + 1)

    verifyNoMoreInteractions(mockStartCapture)
    verifyNoInteractions(mockStopCapture)

    // Advance just short of the typing timeout. Nothing should be happening yet.
    fakeScheduler.advanceBy(JfrTypingLatencyReports.typingTimeoutMillis - 1, TimeUnit.MILLISECONDS)

    verifyNoMoreInteractions(mockStartCapture)
    verifyNoInteractions(mockStopCapture)

    // Advance to typing timeout.
    fakeScheduler.advanceBy(1, TimeUnit.MILLISECONDS)
    flushEventThread()
    verifyNoMoreInteractions(mockStartCapture)
    verify(mockStopCapture).invoke(/* keystrokes = */ 3)
  }

  @Test
  fun latencyListener_recordingStoppedBySessionTimeout() {
    val latencyListener = MyLatencyListener(mockStartCapture, mockStopCapture, fakeScheduler)

    // Choose a length between keystrokes just under the typing timeout to ensure it doesn't fire.
    // Simulate keystrokes until we're just under the session timeout threshold.
    val millisBetweenKeystrokes = JfrTypingLatencyReports.typingTimeoutMillis - 10
    val keystrokesBeforeSessionTimeout = JfrTypingLatencyReports.sessionTimeoutMillis / millisBetweenKeystrokes

    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis + 1)
    verify(mockStartCapture).invoke()
    verifyNoInteractions(mockStopCapture)

    for (unused in 1..keystrokesBeforeSessionTimeout) {
      fakeScheduler.advanceBy(millisBetweenKeystrokes, TimeUnit.MILLISECONDS)
      latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis + 1)

      verifyNoMoreInteractions(mockStartCapture)
      verifyNoInteractions(mockStopCapture)
    }

    // Wait until just before the session timeout.
    fakeScheduler.advanceBy(JfrTypingLatencyReports.sessionTimeoutMillis - fakeScheduler.currentTimeMillis - 1, TimeUnit.MILLISECONDS)

    verifyNoMoreInteractions(mockStartCapture)
    verifyNoInteractions(mockStopCapture)

    // Advance to session timeout.
    fakeScheduler.advanceBy(1, TimeUnit.MILLISECONDS)
    flushEventThread()
    verifyNoMoreInteractions(mockStartCapture)
    verify(mockStopCapture).invoke(/* keystrokes = */ (keystrokesBeforeSessionTimeout + 1).toInt())
  }

  @Test
  fun latencyListener_cooldownPreventsAnotherReport() {
    val latencyListener = MyLatencyListener(mockStartCapture, mockStopCapture, fakeScheduler)

    // Send a keystroke above and then below the threshold, to trigger a report session followed by a cooldown.
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis + 1)
    verify(mockStartCapture).invoke()
    verifyNoInteractions(mockStopCapture)

    fakeScheduler.advanceBy(100, TimeUnit.MILLISECONDS)
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis - 1)
    verifyNoMoreInteractions(mockStartCapture)
    verify(mockStopCapture).invoke(/* keystrokes = */ 1)

    val cooldownStartTime = fakeScheduler.currentTimeMillis
    val cooldownEndTime = cooldownStartTime + JfrTypingLatencyReports.cooldownTimeoutMillis

    // Send a bunch of keystrokes above and below the threshold. None should cause a report to be started.
    // Include a keystroke 1 milli before the cooldown expires.
    fakeScheduler.advanceBy(100, TimeUnit.MILLISECONDS)
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis + 1)
    fakeScheduler.advanceBy(100, TimeUnit.MILLISECONDS)
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis + 1)
    fakeScheduler.advanceBy(100, TimeUnit.MILLISECONDS)
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis - 1)
    fakeScheduler.advanceBy(100, TimeUnit.MILLISECONDS)
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis - 1)
    fakeScheduler.advanceBy(100, TimeUnit.MILLISECONDS)
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis + 1)
    fakeScheduler.advanceBy(JfrTypingLatencyReports.sessionTimeoutMillis + 100, TimeUnit.MILLISECONDS)
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis + 1)
    fakeScheduler.advanceBy(JfrTypingLatencyReports.sessionTimeoutMillis + 100, TimeUnit.MILLISECONDS)
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis - 1)

    fakeScheduler.advanceBy(cooldownEndTime - fakeScheduler.currentTimeMillis - 1, TimeUnit.MILLISECONDS)
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis + 1)

    // Nothing is expected to have timed out, but flush any pending events just in case.
    flushEventThread()

    verifyNoMoreInteractions(mockStartCapture)
    verifyNoMoreInteractions(mockStopCapture)

    // Advance to cooldown end.
    fakeScheduler.advanceBy(1, TimeUnit.MILLISECONDS)
    flushEventThread()
    verifyNoMoreInteractions(mockStartCapture)
    verifyNoMoreInteractions(mockStopCapture)

    // Now that cooldown is over, a high-latency keystroke should start a new session (even without any additional time passing).
    latencyListener.recordTypingLatency(mockEditor, null, JfrTypingLatencyReports.latencyThresholdMillis + 1)

    verify(mockStartCapture, times(2)).invoke()
    verifyNoMoreInteractions(mockStopCapture)
  }

  private fun flushEventThread() {
    ApplicationManager.getApplication().invokeAndWait { }
  }
}
