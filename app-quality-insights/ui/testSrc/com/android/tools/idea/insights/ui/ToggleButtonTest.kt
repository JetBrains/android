/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.IssueState
import com.android.tools.idea.insights.Permission
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.runInEdtAndWait
import java.awt.Component
import java.awt.Dimension
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

class StateHolder<T>(value: T) : Closeable {
  val value = AtomicReference(value)
  private val channel =
    Channel<T>(capacity = Channel.BUFFERED, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    channel.trySend(this.value.get())
  }

  suspend fun updateState(updater: (oldValue: T) -> T) {
    channel.send(value.updateAndGet(updater))
  }

  suspend fun listen(block: suspend (T) -> Unit) = coroutineScope {
    for (e in channel) {
      block(e)
    }
  }

  override fun close() {
    channel.close()
  }
}

@RunWith(JUnit4::class)
class ToggleButtonTest {

  private val issue =
    AppInsightsIssue(
      IssueDetails(
        IssueId("1234"),
        "Issue1",
        "com.google.crash.Crash",
        FailureType.FATAL,
        "Sample Event",
        "1.2.3",
        "1.2.3",
        8L,
        14L,
        5L,
        50L,
        emptySet(),
        "https://url.for-crash.com",
        0,
        emptyList(),
      ),
      Event(),
    )

  private fun createDefaultStateHolder() =
    StateHolder(
      ToggleButtonState(issue, ToggleButtonEnabledState(Permission.FULL, ConnectionMode.ONLINE))
    )

  private fun CoroutineScope.createButton(stateHolder: StateHolder<ToggleButtonState>): JButton =
    ToggleButton(
        withIssue = { block -> launch { stateHolder.listen(block) } },
        onOpen = { issue ->
          launch {
            delay(200)
            stateHolder.updateState { it.copy(issue = issue.copy(state = IssueState.OPENING)) }
          }
        },
        onClose = { issue ->
          launch {
            delay(200)
            stateHolder.updateState { it.copy(issue = issue.copy(state = IssueState.CLOSING)) }
          }
        },
      )
      .also { it.size = Dimension(100, 100) } as JButton

  @Test
  fun `closing issue disables button and then changes label to 'Open issue'`() = runTest {
    createDefaultStateHolder().use { stateHolder ->
      val btn = createButton(stateHolder)
      val ui = FakeUi(btn)
      ui.dispatchInEdt()
      advanceUntilIdle()

      assertThat(btn.text).isEqualTo("Close issue")

      ui.click(btn)
      advanceUntilIdle()
      assertThat(btn.isEnabled).isFalse()
      assertThat(btn.text).isEqualTo("Closing...")

      stateHolder.updateState { it.copy(issue = it.issue.copy(state = IssueState.CLOSED)) }
      advanceUntilIdle()
      assertThat(btn.isEnabled).isTrue()
      assertThat(btn.text).isEqualTo("Undo close")
    }
  }

  @Test
  fun `opening issue disables button and then changes label to 'Close issue'`() = runTest {
    createDefaultStateHolder().use { stateHolder ->
      stateHolder.updateState { it.copy(issue = it.issue.copy(state = IssueState.CLOSED)) }
      val btn = createButton(stateHolder)
      val ui = FakeUi(btn)
      ui.dispatchInEdt()
      advanceUntilIdle()

      assertThat(btn.text).isEqualTo("Undo close")

      ui.click(btn)
      advanceUntilIdle()
      assertThat(btn.isEnabled).isFalse()
      assertThat(btn.text).isEqualTo("Opening...")

      stateHolder.updateState { it.copy(issue = it.issue.copy(state = IssueState.OPEN)) }
      advanceUntilIdle()
      assertThat(btn.isEnabled).isTrue()
      assertThat(btn.text).isEqualTo("Close issue")
    }
  }

  @Test
  fun `insufficient permission disables button`() = runTest {
    StateHolder(
        ToggleButtonState(
          issue,
          ToggleButtonEnabledState(Permission.READ_ONLY, ConnectionMode.ONLINE),
        )
      )
      .use { stateHolder ->
        val btn = createButton(stateHolder)
        val ui = FakeUi(btn)
        ui.dispatchInEdt()
        advanceUntilIdle()

        assertThat(btn.text).isEqualTo("Close issue")
        assertThat(btn.isEnabled).isFalse()

        ui.click(btn)
        advanceUntilIdle()
        assertThat(btn.isEnabled).isFalse()
        assertThat(btn.text).isEqualTo("Close issue")
      }
  }

  @Test
  fun `offline mode disables button`() = runTest {
    StateHolder(
        ToggleButtonState(issue, ToggleButtonEnabledState(Permission.FULL, ConnectionMode.OFFLINE))
      )
      .use { stateHolder ->
        val btn = createButton(stateHolder)
        val ui = FakeUi(btn)
        ui.dispatchInEdt()
        advanceUntilIdle()

        assertThat(btn.text).isEqualTo("Close issue")
        assertThat(btn.isEnabled).isFalse()

        ui.click(btn)
        advanceUntilIdle()
        assertThat(btn.isEnabled).isFalse()
        assertThat(btn.text).isEqualTo("Close issue")
      }
  }
}

fun FakeUi.dispatchInEdt() = runInEdtAndWait { layoutAndDispatchEvents() }

fun FakeUi.click(component: Component) = runInEdtAndWait { clickOn(component) }
