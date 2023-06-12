/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.messages

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.FakeLogcatPresenter
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.filters.LogcatFilterField.LINE
import com.android.tools.idea.logcat.filters.StringFilter
import com.android.tools.idea.logcat.message.LogLevel.WARN
import com.android.tools.idea.logcat.message.LogcatHeader
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.util.onIdle
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange.EMPTY_RANGE
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.util.concurrent.Executors

private val timestamp = Instant.ofEpochMilli(1000)

/**
 * Tests for [MessageProcessor]
 */
class MessageProcessorTest {
  @get:Rule
  val rule = RuleChain(ApplicationRule(), AndroidExecutorsRule(Executors.newCachedThreadPool()))

  private val fakeLogcatPresenter = FakeLogcatPresenter()
  private val messageFormatter = ::formatMessages

  @After
  fun tearDown() {
    Disposer.dispose(fakeLogcatPresenter)
  }

  @Test
  fun appendMessages_batchesJoined(): Unit = runBlocking {
    val mockClock = mock<Clock>()
    // First call initializes lastFlushTime, then each call represents a batch.
    whenever(mockClock.millis()).thenReturn(1000, 1000, 1010)
    val messageProcessor = messageProcessor(fakeLogcatPresenter, maxTimePerBatchMs = 100, clock = mockClock, autoStart = false)
    val batch1 = listOf(
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", timestamp), "message1"),
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag2", timestamp), "message2"),
    )
    val batch2 = listOf(
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app2", "", "tag1", timestamp), "message3"),
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app2", "", "tag2", timestamp), "message4"),
    )

    messageProcessor.appendMessages(batch1)
    messageProcessor.appendMessages(batch2)
    messageProcessor.start()

    messageProcessor.onIdle {
      assertThat(fakeLogcatPresenter.lineBatches).containsExactly((batch1 + batch2).mapMessages())
    }
  }

  @Test
  fun appendMessages_batchesSplitOnMaxMessagesPerBatch() = runBlocking {
    val messageProcessor = messageProcessor(fakeLogcatPresenter, maxMessagesPerBatch = 1, autoStart = false)
    val batch1 = listOf(
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", timestamp), "message1"),
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag2", timestamp), "message2"),
    )
    val batch2 = listOf(
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app2", "", "tag1", timestamp), "message3"),
      LogcatMessage(LogcatHeader(WARN, 1, 2, "app2", "", "tag2", timestamp), "message4"),
    )

    messageProcessor.appendMessages(batch1)
    messageProcessor.appendMessages(batch2)
    messageProcessor.start()

    messageProcessor.onIdle {
      @Suppress("ConvertLambdaToReference") // Calling inOrder() confuses IDEA.
      assertThat(fakeLogcatPresenter.lineBatches).containsExactly(
        batch1.mapMessages(),
        batch2.mapMessages(),
      ).inOrder()
    }
  }

  // We have to mock the clock ahead of time because of the asynchronous nature of the tested code. If we set it up before each call to
  // appendMessages, they will all be executed immediately and the tested code will only see the final value.
  @Test
  fun appendMessages_batchesSplitOnMaxTimePerBatchMs() = runBlocking {
    val mockClock = mock<Clock>()
    // First call initializes lastFlushTime, then each call represents a batch.
    whenever(mockClock.millis()).thenReturn(1000, 1000, 2000, 3000)
    val messageProcessor = messageProcessor(fakeLogcatPresenter, maxTimePerBatchMs = 500, clock = mockClock, autoStart = false)
    // We need 3 batches here because the first batch will never be flushed on its own unless the channel is empty. We can fake it by
    // setting up the mock with (1000, 2000, 3000) but that's not an accurate representation of what actually happens where the first 2
    // calls to clock.millis() happen almost at the same time.
    val batch1 = listOf(LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", timestamp), "message1"))
    val batch2 = listOf(LogcatMessage(LogcatHeader(WARN, 1, 2, "app2", "", "tag2", timestamp), "message2"))
    val batch3 = listOf(LogcatMessage(LogcatHeader(WARN, 1, 2, "app2", "", "tag2", timestamp), "message3"))

    messageProcessor.appendMessages(batch1)
    messageProcessor.appendMessages(batch2)
    messageProcessor.appendMessages(batch3)
    messageProcessor.start()

    messageProcessor.onIdle {
      @Suppress("ConvertLambdaToReference") // Calling inOrder() confuses IDEA.
      assertThat(fakeLogcatPresenter.lineBatches).containsExactly(
        (batch1 + batch2).mapMessages(),
        batch3.mapMessages()
      ).inOrder()
    }
  }

  @Test
  fun appendMessages_batchesSplitOnEmptyChannel() = runBlocking {
    val messageProcessor = messageProcessor(fakeLogcatPresenter)
    val batch1 = listOf(LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", timestamp), "message1"))
    val batch2 = listOf(LogcatMessage(LogcatHeader(WARN, 1, 2, "app2", "", "tag2", timestamp), "message2"))

    messageProcessor.appendMessages(batch1)
    messageProcessor.onIdle { }
    messageProcessor.appendMessages(batch2)

    messageProcessor.onIdle {
      @Suppress("ConvertLambdaToReference") // Calling inOrder() confuses IDEA.
      assertThat(fakeLogcatPresenter.lineBatches).containsExactly(
        batch1.mapMessages(),
        batch2.mapMessages(),
      ).inOrder()
    }
  }

  @Test
  fun appendMessages_filters() = runBlocking {
    val message1 = LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", timestamp), "message1")
    val message2 = LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag2", timestamp), "message2")
    val messageProcessor = messageProcessor(fakeLogcatPresenter)
    val batch = listOf(message1, message2)
    messageProcessor.logcatFilter = StringFilter("tag2", LINE, EMPTY_RANGE)
    messageProcessor.appendMessages(batch)

    messageProcessor.onIdle {
      assertThat(fakeLogcatPresenter.lineBatches).containsExactly(listOf(message2).mapMessages())
    }
  }

  @Test
  fun appendMessages_emptyMessages() = runBlocking {
    val message1 = LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag1", timestamp), "message1")
    val message2 = LogcatMessage(LogcatHeader(WARN, 1, 2, "app1", "", "tag2", timestamp), "message2")
    val messageProcessor = messageProcessor(fakeLogcatPresenter)
    val batch = listOf(message1, message2)
    messageProcessor.logcatFilter = StringFilter("no-such-line", LINE, EMPTY_RANGE)
    messageProcessor.appendMessages(batch)

    messageProcessor.onIdle {
      assertThat(fakeLogcatPresenter.lineBatches).isEmpty()
    }
  }

  private fun messageProcessor(
    logcatPresenter: LogcatPresenter = fakeLogcatPresenter,
    formatMessagesInto: (TextAccumulator, List<LogcatMessage>) -> Unit = messageFormatter,
    clock: Clock = Clock.systemDefaultZone(),
    maxTimePerBatchMs: Int = MAX_TIME_PER_BATCH_MS,
    maxMessagesPerBatch: Int = StudioFlags.LOGCAT_MAX_MESSAGES_PER_BATCH.get(),
    autoStart: Boolean = true,
  ) = MessageProcessor(
    logcatPresenter,
    formatMessagesInto,
    logcatFilter = null,
    clock,
    maxTimePerBatchMs,
    maxMessagesPerBatch,
    autoStart)
}

private fun formatMessages(textAccumulator: TextAccumulator, messages: List<LogcatMessage>) {
  textAccumulator.accumulate("${messages.joinToString("\n", transform = { it.message })}\n")
}

private fun List<LogcatMessage>.mapMessages() = map { it.message }
