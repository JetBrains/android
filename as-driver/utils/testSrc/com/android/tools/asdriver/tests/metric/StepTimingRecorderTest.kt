/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.asdriver.tests.metric

import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

public class StepTimingRecorderTest {
  private var currentTime = 1000L
  private val committedMetrics = CopyOnWriteArrayList<Pair<String, Long>>()

  @Before
  fun setUp() {
    StepTimingRecorder.clear()
    currentTime = 1000L
    committedMetrics.clear()
    StepTimingRecorder.timeProvider = { currentTime }
    StepTimingRecorder.committer = StepTimingRecorder.MetricCommitter { name, duration -> committedMetrics.add(name to duration) }
  }

  @After
  fun tearDown() {
    StepTimingRecorder.clear()
  }

  @Test
  fun testSingleSpanRecording() {
    StepTimingRecorder.startSpan("runStudio")
    currentTime += 2500L
    StepTimingRecorder.endSpan("runStudio")

    assertEquals(1, committedMetrics.size)
    assertEquals("E2E_runStudio" to 2500L, committedMetrics[0])
  }

  @Test
  fun testDuplicateSpanExecutionNaming() {
    StepTimingRecorder.startSpan("runAdb")
    currentTime += 150L
    StepTimingRecorder.endSpan("runAdb")

    StepTimingRecorder.startSpan("runAdb")
    currentTime += 200L
    StepTimingRecorder.endSpan("runAdb")

    StepTimingRecorder.startSpan("runAdb")
    currentTime += 350L
    StepTimingRecorder.endSpan("runAdb")

    assertEquals(3, committedMetrics.size)
    assertEquals("E2E_runAdb" to 150L, committedMetrics[0])
    assertEquals("E2E_runAdb_2" to 200L, committedMetrics[1])
    assertEquals("E2E_runAdb_3" to 350L, committedMetrics[2])
  }

  @Test
  fun testNestedSpanExecutionInReverseOrder() {
    StepTimingRecorder.startSpan("Build")

    currentTime = 1200L
    StepTimingRecorder.startSpan("MakeGradleProject")
    currentTime = 1500L
    StepTimingRecorder.endSpan("MakeGradleProject")

    currentTime = 1600L
    StepTimingRecorder.startSpan("waitForBuild")
    currentTime = 2100L
    StepTimingRecorder.endSpan("waitForBuild")

    currentTime = 3000L
    StepTimingRecorder.endSpan("Build")

    assertEquals(3, committedMetrics.size)
    assertEquals("E2E_MakeGradleProject" to 300L, committedMetrics[0])
    assertEquals("E2E_waitForBuild" to 500L, committedMetrics[1])
    assertEquals("E2E_Build" to 2000L, committedMetrics[2])
  }

  @Test
  fun testRecordParsedDuration() {
    StepTimingRecorder.recordParsedDuration("GradleSync", "27 s 121 ms")
    StepTimingRecorder.recordParsedDuration("Indexing", "1111ms")
    StepTimingRecorder.recordParsedDuration("GradleBuild", "1 m 4 s")

    assertEquals(3, committedMetrics.size)
    assertEquals("E2E_GradleSync" to 27121L, committedMetrics[0])
    assertEquals("E2E_Indexing" to 1111L, committedMetrics[1])
    assertEquals("E2E_GradleBuild" to 64000L, committedMetrics[2])
  }
}
