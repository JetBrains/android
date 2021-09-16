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

import com.android.ddmlib.Log.LogLevel.WARN
import com.android.ddmlib.logcat.LogCatHeader
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.logcat.onIdle
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.concurrent.Executors

private val timestamp = Instant.ofEpochMilli(1000)

/**
 * Tests for [MessageProcessor]
 */
class MessageProcessorTest {
  @get:Rule
  val rule = RuleChain(ApplicationRule(), AndroidExecutorsRule(Executors.newCachedThreadPool()))

  private val disposable: Disposable = Disposable { }
  private val messageBatches = mutableListOf<String>()
  private val messageProcessor by lazy { MessageProcessor(disposable, this::formatMessages, this::appendMessages) }

  @After
  fun tearDown() {
    disposable.dispose()
  }

  @Test
  fun appendMessages_multipleBatches() = runBlocking {
    val batch1 = listOf(
      LogCatMessage(LogCatHeader(WARN, 1, 2, "app1", "tag1", timestamp), "message1"),
      LogCatMessage(LogCatHeader(WARN, 1, 2, "app1", "tag2", timestamp), "message2"),
    )
    val batch2 = listOf(
      LogCatMessage(LogCatHeader(WARN, 1, 2, "app2", "tag1", timestamp), "message3"),
      LogCatMessage(LogCatHeader(WARN, 1, 2, "app2", "tag2", timestamp), "message4"),
    )

    messageProcessor.appendMessages(batch1)
    messageProcessor.appendMessages(batch2)

    messageProcessor.onIdle {
      @Suppress("ConvertLambdaToReference") // The inOrder() call confuses IDEA
      assertThat(messageBatches).containsExactly(
        batch1.joinToString(", ", transform = LogCatMessage::message),
        batch2.joinToString(", ", transform = LogCatMessage::message),
      ).inOrder()
    }
  }

  private fun formatMessages(textAccumulator: TextAccumulator, messages: List<LogCatMessage>) {
    textAccumulator.accumulate(messages.joinToString(", ", transform = LogCatMessage::message))
  }

  private fun appendMessages(textAccumulator: TextAccumulator) {
    messageBatches.add(textAccumulator.text)
  }
}
