/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.diagnostics.report.DiagnosticReport
import com.android.tools.idea.diagnostics.report.DiagnosticReportProperties
import com.android.tools.idea.diagnostics.report.JfrBasedReport
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import jdk.jfr.consumer.RecordedEvent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertFailsWith

private const val REPORT_TYPE = "Test Report"

@RunWith(JUnit4::class)
class JfrReportGeneratorTest {
  @get:Rule
  val projectRule = ProjectRule()

  private val fakeEventFilter = FakeEventFilter()
  private val now = Instant.EPOCH
  private val clock = Clock.fixed(now, ZoneOffset.UTC)

  private val testJfrReportGenerator = TestJfrReportGenerator(clock, REPORT_TYPE, fakeEventFilter)
  private val capture = testJfrReportGenerator.Capture()
  private val event: RecordedEvent = mock()
  private val recordingManager: RecordingManager = mock()

  private var callbackInvocations = 0
  private var callbackReport: DiagnosticReport? = null
  private var startedCapture: JfrReportGenerator.Capture? = null

  @Before
  fun setUp() {
    ApplicationManager.getApplication().replaceService(
      RecordingManager::class.java, recordingManager, projectRule.project.earlyDisposable)
    whenever(recordingManager.startCapture(any())).thenAnswer {
      startedCapture = (it.arguments[0] as JfrReportGenerator.Capture)
      null
    }
    whenever(event.startTime).thenReturn(now)
  }

  @Test
  fun constructor_startOffsetMsThresholdEnforced() {
    // Should not throw
    TestJfrReportGenerator(clock, REPORT_TYPE, fakeEventFilter, -JFR_RECORDING_DURATION.inWholeMilliseconds)
    assertFailsWith<IllegalArgumentException> {
      TestJfrReportGenerator(clock, REPORT_TYPE, fakeEventFilter, -JFR_RECORDING_DURATION.inWholeMilliseconds - 1)
    }
  }

  @Test
  fun capture_maybeAccept_success_nullEnd() {
    capture.maybeAccept(event)

    assertThat(testJfrReportGenerator.acceptedEvents).containsExactly(event to capture)
  }

  @Test
  fun capture_maybeAccept_success_nonNullEnd() {
    capture.end = now.plusMillis(1L)

    capture.maybeAccept(event)

    assertThat(testJfrReportGenerator.acceptedEvents).containsExactly(event to capture)
  }

  @Test
  fun capture_maybeAccept_failure_tooEarly() {
    val earlyGenerator = TestJfrReportGenerator(clock, REPORT_TYPE, fakeEventFilter, startOffsetMs = 1L)
    val c = earlyGenerator.Capture()

    c.maybeAccept(event)

    assertThat(earlyGenerator.acceptedEvents).isEmpty()
  }

  @Test
  fun capture_maybeAccept_failure_tooLate() {
    capture.end = now

    capture.maybeAccept(event)

    assertThat(testJfrReportGenerator.acceptedEvents).isEmpty()
  }

  @Test
  fun capture_maybeAccept_failure_filterRejected() {
    fakeEventFilter.accepting = false

    capture.maybeAccept(event)

    assertThat(testJfrReportGenerator.acceptedEvents).isEmpty()
  }

  @Test
  fun capture_completeAndGenerateReport_failure_nullEnd() {
    val success = capture.completeAndGenerateReport(Instant.MAX) {
      ++callbackInvocations
      true
    }

    assertThat(success).isFalse()
    assertThat(callbackInvocations).isEqualTo(0)
    assertThat(testJfrReportGenerator.completedCaptures).isEmpty()
  }

  @Test
  fun capture_completeAndGenerateReport_failure_nonNullEnd() {
    capture.end = now

    val success = capture.completeAndGenerateReport(now) {
      ++callbackInvocations
      true
    }

    assertThat(success).isFalse()
    assertThat(callbackInvocations).isEqualTo(0)
    assertThat(testJfrReportGenerator.completedCaptures).isEmpty()
  }

  @Test
  fun capture_completeAndGenerateReport_success_unfinished() {
    capture.end = now

    val success = capture.completeAndGenerateReport(now.plusMillis(1L)) {
      ++callbackInvocations
      true
    }

    assertThat(success).isTrue()
    assertThat(callbackInvocations).isEqualTo(0)
    assertThat(testJfrReportGenerator.completedCaptures).containsExactly(capture)
  }

  @Test
  fun capture_completeAndGenerateReport_success_emptyReport() {
    capture.end = now
    testJfrReportGenerator.finish()
    testJfrReportGenerator.reportToReturn = mapOf()
    var callbackInvocations = 0

    val success = capture.completeAndGenerateReport(now.plusMillis(1L)) {
      ++callbackInvocations
      true
    }

    assertThat(success).isTrue()
    assertThat(callbackInvocations).isEqualTo(0)
    assertThat(testJfrReportGenerator.completedCaptures).containsExactly(capture)
  }

  @Test
  fun capture_completeAndGenerateReport_success_nonEmptyReport() {
    capture.end = now
    testJfrReportGenerator.finish()

    val success = capture.completeAndGenerateReport(now.plusMillis(1L)) {
      ++callbackInvocations
      callbackReport = it
      true
    }

    assertThat(success).isTrue()
    assertThat(callbackInvocations).isEqualTo(1)
    with(callbackReport as JfrBasedReport) {
      assertThat(type).isEqualTo(REPORT_TYPE)
      val defaultProperties = DiagnosticReportProperties()
      // properties.uptime and properties.reportTime can't be tested. Just get the other values.
      assertThat(properties.sessionId).isEqualTo(defaultProperties.sessionId)
      assertThat(properties.studioVersion).isEqualTo(defaultProperties.studioVersion)
      assertThat(properties.kotlinVersion).isEqualTo(defaultProperties.kotlinVersion)
      assertThat(fields).isEqualTo(testJfrReportGenerator.defaultReport)
    }
    assertThat(testJfrReportGenerator.completedCaptures).containsExactly(capture)
  }

  @Test
  fun capture_completeAndGenerateReport_success_exceptionThrown() {
    capture.end = now

    testJfrReportGenerator.finish()

    val success = capture.completeAndGenerateReport(now.plusMillis(1L)) {
      throw CloneNotSupportedException("No Cloning!")
    }

    assertThat(success).isTrue()
    assertThat(testJfrReportGenerator.completedCaptures).containsExactly(capture)
  }

  @Test
  fun startCapture_throwsIfCaptureInProgress() {
    testJfrReportGenerator.startCapture()
    assertFailsWith<IllegalStateException> {
      testJfrReportGenerator.startCapture()
    }
  }

  @Test
  fun startCapture() {
    testJfrReportGenerator.startCapture()

    assertThat(startedCapture).isNotNull()
    assertThat(startedCapture?.start).isEqualTo(now)
    assertThat(startedCapture?.end).isNull()
  }

  @Test
  fun startCapture_withStartOffset() {
    val offsetGenerator = TestJfrReportGenerator(clock, REPORT_TYPE, fakeEventFilter, startOffsetMs = 1L)

    offsetGenerator.startCapture()

    assertThat(startedCapture).isNotNull()
    assertThat(startedCapture?.start).isEqualTo(now.plusMillis(1L))
    assertThat(startedCapture?.end).isNull()
  }

  @Test
  fun stopCapture_throwsIfNoCaptureInProgress() {
    assertFailsWith<IllegalStateException> {
      testJfrReportGenerator.stopCapture()
    }
  }

  @Test
  fun stopCapture() {
    testJfrReportGenerator.startCapture()
    testJfrReportGenerator.stopCapture()

    assertThat(startedCapture).isNotNull()
    assertThat(startedCapture?.start).isEqualTo(now)
    assertThat(startedCapture?.end).isEqualTo(now)
  }

  @Test
  fun stopCapture_withEndOffset() {
    val offsetGenerator = TestJfrReportGenerator(clock, REPORT_TYPE, fakeEventFilter, endOffsetMs = 1L)

    offsetGenerator.startCapture()
    offsetGenerator.stopCapture()

    assertThat(startedCapture).isNotNull()
    assertThat(startedCapture?.start).isEqualTo(now)
    assertThat(startedCapture?.end).isEqualTo(now.plusMillis(1L))
  }

  private class FakeEventFilter : EventFilter {
    var accepting = true
    override fun accepts(event: RecordedEvent) = accepting
  }

  private class TestJfrReportGenerator(
    clock: Clock, reportType: String, eventFilter: EventFilter, startOffsetMs: Long = 0, endOffsetMs: Long = 0):
    JfrReportGenerator(reportType, eventFilter, startOffsetMs, endOffsetMs, clock) {
    val defaultReport = mapOf(
      "The Regrettes" to "Barely On My Mind",
      "Metric" to "All Comes Crashing",
      "COIN" to "Cutie",
      "Broken Bells" to "The Ghost Inside",
    )
    val acceptedEvents: MutableList<Pair<RecordedEvent, Capture>> = mutableListOf()
    val completedCaptures: MutableList<Capture> = mutableListOf()
    var reportToReturn = defaultReport

    override fun accept(e: RecordedEvent, c: Capture) {
      acceptedEvents += e to c
    }

    override fun captureCompleted(c: Capture) {
      completedCaptures += c
    }

    override fun generateReport(): Map<String, String> = reportToReturn
  }
}

