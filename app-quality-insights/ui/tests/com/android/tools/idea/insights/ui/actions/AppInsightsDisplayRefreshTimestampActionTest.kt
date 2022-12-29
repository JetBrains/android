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
package com.google.services.firebase.insights.ui

import com.android.tools.idea.concurrency.SupervisorJob
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.google.services.firebase.insights.FakeClock
import com.intellij.testFramework.DisposableRule
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppInsightsDisplayRefreshTimestampActionTest {
  @get:Rule val disposableRule = DisposableRule()
  private lateinit var scope: CoroutineScope
  private lateinit var timestamp: MutableSharedFlow<Timestamp>
  private lateinit var action: AppInsightsDisplayRefreshTimestampAction
  private lateinit var clock: FakeClock

  @Before
  fun setUp() {
    clock = FakeClock()
    scope =
      CoroutineScope(
        MoreExecutors.directExecutor().asCoroutineDispatcher() +
          SupervisorJob(disposableRule.disposable)
      )
    timestamp = MutableSharedFlow()
    action = AppInsightsDisplayRefreshTimestampAction(timestamp, clock, scope)
  }

  private suspend fun waitUntil(condition: () -> Boolean) {
    withTimeout(1000) {
      while (!condition()) {
        yield()
      }
    }
  }

  @Test
  fun `display refreshing in progress`() {
    runBlocking {
      assertThat(action.displayText).isEqualTo("Last refreshed: never")

      timestamp.emit(Timestamp(null, TimestampState.UNAVAILABLE))
      waitUntil { action.displayText == "Refreshing..." }
    }
  }

  @Test
  fun `display last refreshed`() {
    runBlocking {
      assertThat(action.displayText).isEqualTo("Last refreshed: never")

      timestamp.emit(Timestamp(clock.instant(), TimestampState.ONLINE))
      waitUntil { action.displayText == "Last refreshed: right now" }

      // Check "text being updated" when time advances.
      clock.advanceTimeBy(Duration.ofSeconds(1).toMillis())
      assertThat(action.displayText).isEqualTo("Last refreshed: moments ago")

      // Check "text being updated" when time advances.
      clock.advanceTimeBy(Duration.ofMinutes(2).toMillis())
      assertThat(action.displayText).isEqualTo("Last refreshed: 2 minutes ago")
    }
  }

  @Test
  fun `display offline`() {
    runBlocking {
      assertThat(action.displayText).isEqualTo("Last refreshed: never")

      timestamp.emit(Timestamp(clock.instant(), TimestampState.ONLINE))
      waitUntil { action.displayText == "Last refreshed: right now" }

      clock.advanceTimeBy(Duration.ofMinutes(2).toMillis())
      assertThat(action.displayText).isEqualTo("Last refreshed: 2 minutes ago")

      timestamp.emit(Timestamp(null, TimestampState.UNAVAILABLE))
      waitUntil { action.displayText == "Refreshing..." }

      timestamp.emit(Timestamp(null, TimestampState.OFFLINE))
      waitUntil { action.displayText == "Currently offline. Last refreshed: 2 minutes ago" }
    }
  }
}
