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

import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.util.logcatMessage
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.UsefulTestCase.assertThrows
import org.junit.Test

/**
 * Tests for [MessageBacklog]
 */
class MessageBacklogTest {
  private val message1 = logcatMessage(message = "Message 1")
  private val message2 = logcatMessage(message = "Message 2")
  private val message3 = logcatMessage(message = "Message 3")

  @Test
  fun create_smallSize() {
    assertThrows(AssertionError::class.java) { MessageBacklog(0) }
  }

  @Test
  fun addAll_oneBatch() {
    val messageBacklog = MessageBacklog(20)

    messageBacklog.addAll(listOf(message1, message2, message3))

    assertThat(messageBacklog.messages).containsExactly(message2, message3)
    assertThat(messageBacklog.messages).containsExactly(message2, message3)
  }

  @Test
  fun addAll_multipleBatches() {
    val messageBacklog = MessageBacklog(20)

    messageBacklog.addAll(listOf(message1))
    messageBacklog.addAll(listOf(message2))
    messageBacklog.addAll(listOf(message3))

    assertThat(messageBacklog.messages).containsExactly(message2, message3)
  }

  /**
   * This test indirectly verifies that [MessageBacklog.clear] is implemented properly.
   */
  @Test
  fun clear_resetsSize() {
    // We use a MessageBacklog that can hold 2 of our messages.
    val messageBacklog = MessageBacklog(20)
    // And fill it up so it's at capacity
    messageBacklog.addAll(listOf(message1, message2))

    // We clear it
    messageBacklog.clear()
    // And add 2 messages again
    messageBacklog.addAll(listOf(message1, message2))

    // If clear() did not also reset the internal size, we would have an empty backlog because as each message is added, the size would
    // exceed the max size and the message would be deleted.
    assertThat(messageBacklog.messages).containsExactly(message1, message2)
  }

  /**
   * This test indirectly verifies that [MessageBacklog.clear] is implemented properly.
   */
  @Test
  fun clear_resetsSize_largeBatch() {
    // We use a MessageBacklog that can hold 2 of our messages.
    val messageBacklog = MessageBacklog(20)
    // And fill it up so it's at capacity
    messageBacklog.addAll(listOf(message1, message2))

    // We clear it
    messageBacklog.clear()
    // And add 4 messages
    messageBacklog.addAll(listOf(message1, message1, message2, message3))

    // If clear() did not also reset the internal size, we would have an empty backlog because as each message is added, the size would
    // exceed the max size and the message would be deleted.
    assertThat(messageBacklog.messages).containsExactly(message2, message3)
  }

  @Test
  fun messages_isUnmodifiable() {
    val mutableList = MessageBacklog(20).messages as MutableList<LogcatMessage>

    assertThrows(UnsupportedOperationException::class.java, mutableList::clear)
  }
}