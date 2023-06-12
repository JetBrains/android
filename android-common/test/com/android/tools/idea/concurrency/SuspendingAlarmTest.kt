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
package com.android.tools.idea.concurrency

import com.android.tools.concurrency.AndroidIoManager
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.registerServiceInstance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class SuspendingAlarmTest {
  @get:Rule val projectRule = ProjectRule()

  private val scheduler = TestCoroutineScheduler()
  private val dispatcher = StandardTestDispatcher(scheduler)
  private val testScope = TestScope(dispatcher)
  private lateinit var alarm: SuspendingAlarm
  private var numRequestInvocations = 0

  @Before
  fun setUp() {
    ApplicationManager.getApplication().registerServiceInstance(AndroidIoManager::class.java, StudioIoManager())
    alarm = SuspendingAlarm(projectRule.project.earlyDisposable, testScope.coroutineContext)
  }

  @Test
  fun request_suspendingPayload() {
    alarm.request(Duration.ZERO, ::requestPayload)

    assertThat(numRequestInvocations).isEqualTo(0)

    scheduler.runCurrent()
    // Should still be zero, need to wait for the payload's own delay.
    assertThat(numRequestInvocations).isEqualTo(0)

    scheduler.advanceTimeBy(PAYLOAD_INTERNAL_DELAY)
    // Still shouldn't have happened because the scheduler doesn't run until we ask, or it passes the time.
    assertThat(numRequestInvocations).isEqualTo(0)

    scheduler.runCurrent()
    assertThat(numRequestInvocations).isEqualTo(1)
  }

  @Test
  fun request_nonSuspendingPayload() {
    alarm.request(Duration.ZERO, ::nonSuspendingRequestPayload)

    // Shouldn't have happened because the scheduler doesn't run until we ask, or it passes the time.
    assertThat(numRequestInvocations).isEqualTo(0)

    scheduler.runCurrent()
    assertThat(numRequestInvocations).isEqualTo(1)
  }

  @Test
  fun request_delay() {
    val delays = listOf(1.milliseconds, 1.seconds, 1.minutes, 1.hours, 1.days)
    delays.forEachIndexed { i, d ->
      assertThat(numRequestInvocations).isEqualTo(i)
      alarm.request(d, ::requestPayload)
      scheduler.advanceTimeBy(d + PAYLOAD_INTERNAL_DELAY)

      assertThat(numRequestInvocations).isEqualTo(i)
      scheduler.runCurrent()
      assertThat(numRequestInvocations).isEqualTo(i + 1)
    }
  }

  @Test
  fun request_obeysCancellation() {
    val job = alarm.request(INITIAL_DELAY, ::requestPayload)
    scheduler.advanceTimeBy(INITIAL_DELAY + PAYLOAD_INTERNAL_DELAY)
    assertThat(numRequestInvocations).isEqualTo(0)

    job.cancel()

    assertThat(job.isCancelled).isTrue()
    scheduler.runCurrent()
    assertThat(numRequestInvocations).isEqualTo(0)
    scheduler.advanceTimeBy(10000.days)
    assertThat(numRequestInvocations).isEqualTo(0)
  }

  @Test
  fun request_obeysCancellation_all() {
    val job = alarm.request(INITIAL_DELAY, ::requestPayload)
    scheduler.advanceTimeBy(INITIAL_DELAY + PAYLOAD_INTERNAL_DELAY)
    assertThat(numRequestInvocations).isEqualTo(0)

    alarm.cancelAll()

    assertThat(job.isCancelled).isTrue()
    scheduler.runCurrent()
    assertThat(numRequestInvocations).isEqualTo(0)
    scheduler.advanceTimeBy(10000.days)
    assertThat(numRequestInvocations).isEqualTo(0)
  }

  @Test
  fun requestRepeating_suspendingPayload() {
    alarm.requestRepeating(PERIOD, initialDelay = INITIAL_DELAY, request = ::requestPayload)
    assertThat(numRequestInvocations).isEqualTo(0)

    // Get us past the initial delay, shouldn't have run the request yet
    scheduler.advanceTimeBy(INITIAL_DELAY)

    repeat(10) {
      scheduler.advanceTimeBy(PAYLOAD_INTERNAL_DELAY)
      assertThat(numRequestInvocations).isEqualTo(it)

      scheduler.runCurrent()
      assertThat(numRequestInvocations).isEqualTo(it + 1)

      scheduler.advanceTimeBy(PERIOD)
    }
  }

  @Test
  fun requestRepeating_nonSuspendingPayload() {
    alarm.requestRepeating(PERIOD, initialDelay = INITIAL_DELAY, request = ::nonSuspendingRequestPayload)
    assertThat(numRequestInvocations).isEqualTo(0)

    // Get us past the initial delay, shouldn't have run the request yet
    scheduler.advanceTimeBy(INITIAL_DELAY)

    repeat(10) {
      assertThat(numRequestInvocations).isEqualTo(it)

      scheduler.runCurrent()
      assertThat(numRequestInvocations).isEqualTo(it + 1)

      scheduler.advanceTimeBy(PERIOD)
    }
  }

  @Test
  fun requestRepeating_quitsAfterIterations() {
    val iterations = 5
    alarm.requestRepeating(PERIOD, initialDelay = INITIAL_DELAY, iterations = iterations.toLong(), request = ::requestPayload)
    assertThat(numRequestInvocations).isEqualTo(0)

    // Get us past the initial delay, shouldn't have run the request yet
    scheduler.advanceTimeBy(INITIAL_DELAY)

    repeat(iterations) {
      scheduler.advanceTimeBy(PAYLOAD_INTERNAL_DELAY)
      assertThat(numRequestInvocations).isEqualTo(it)

      scheduler.runCurrent()
      assertThat(numRequestInvocations).isEqualTo(it + 1)

      scheduler.advanceTimeBy(PERIOD)
    }

    // Shouldn't continue incrementing
    repeat(10) {
      scheduler.advanceTimeBy(10000.days)
      assertThat(numRequestInvocations).isEqualTo(iterations)
    }
  }

  @Test
  fun requestRepeating_obeysCancellation() {
    val iterationsBeforeCancellation = 5
    val job = alarm.requestRepeating(PERIOD, initialDelay = INITIAL_DELAY, request = ::requestPayload)
    assertThat(numRequestInvocations).isEqualTo(0)

    // Get us past the initial delay, shouldn't have run the request yet
    scheduler.advanceTimeBy(INITIAL_DELAY)

    repeat(iterationsBeforeCancellation) {
      scheduler.advanceTimeBy(PAYLOAD_INTERNAL_DELAY)
      assertThat(numRequestInvocations).isEqualTo(it)

      scheduler.runCurrent()
      assertThat(numRequestInvocations).isEqualTo(it + 1)

      scheduler.advanceTimeBy(PERIOD)
    }

    job.cancel()

    // Shouldn't continue incrementing
    repeat(10) {
      scheduler.advanceTimeBy(10000.days)
      assertThat(numRequestInvocations).isEqualTo(iterationsBeforeCancellation)
    }
  }

  @Test
  fun requestRepeating_obeysCancellation_all() {
    val iterationsBeforeCancellation = 5
    val job = alarm.requestRepeating(PERIOD, initialDelay = INITIAL_DELAY, request = ::requestPayload)
    assertThat(numRequestInvocations).isEqualTo(0)

    // Get us past the initial delay, shouldn't have run the request yet
    scheduler.advanceTimeBy(INITIAL_DELAY)

    repeat(iterationsBeforeCancellation) {
      scheduler.advanceTimeBy(PAYLOAD_INTERNAL_DELAY)
      assertThat(numRequestInvocations).isEqualTo(it)

      scheduler.runCurrent()
      assertThat(numRequestInvocations).isEqualTo(it + 1)

      scheduler.advanceTimeBy(PERIOD)
    }

    alarm.cancelAll()
    assertThat(job.isCancelled).isTrue()

    // Shouldn't continue incrementing
    repeat(10) {
      scheduler.advanceTimeBy(10000.days)
      assertThat(numRequestInvocations).isEqualTo(iterationsBeforeCancellation)
    }
  }

  @Test
  fun cancellationDoesNotPrecludeFurtherRequests() {
    alarm.cancelAll()
    alarm.request(Duration.ZERO, ::nonSuspendingRequestPayload)

    // Shouldn't have happened because the scheduler doesn't run until we ask, or it passes the time.
    assertThat(numRequestInvocations).isEqualTo(0)

    scheduler.runCurrent()
    assertThat(numRequestInvocations).isEqualTo(1)
  }

  private suspend fun requestPayload() {
    delay(PAYLOAD_INTERNAL_DELAY)
    ++numRequestInvocations
  }

  private fun nonSuspendingRequestPayload() {
    ++numRequestInvocations
  }

  private fun TestCoroutineScheduler.advanceTimeBy(duration: Duration) {
    advanceTimeBy(duration.inWholeMilliseconds)
  }

  companion object {
    private val PAYLOAD_INTERNAL_DELAY = 1.milliseconds
    private val INITIAL_DELAY = 1.days
    private val PERIOD = 1.minutes
  }
}
