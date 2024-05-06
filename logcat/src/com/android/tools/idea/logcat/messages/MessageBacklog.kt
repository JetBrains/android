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
import java.util.Collections

/**
 * Manages a cyclic collection of [LogcatMessage]s that is limited by the size in bytes of the
 * payload.
 *
 * The purpose of this backlog is to allow for filtering and re-rendering of the Logcat when needed,
 * for example, when formatting changes are made.
 *
 * The goal is to have this backlog contain at least as many messages as there are displayed in the
 * Logcat window which is also governed by a cyclic buffer size. In order to achieve this, the size
 * of a LogcatMessage is 'rounded down' to [LogcatMessage.message] which is less than the minimal
 * size required to render a message. Therefore, the backlog will contain more messages than the
 * actual displayed window, even if no filters are applied and the formatting options are at their
 * minimum.
 *
 * TODO(aalbert): Maybe pass in the current formatting options setting and calculate the size more
 *   accurately.
 */
internal class MessageBacklog(private var maxSize: Int) {

  // The internal messages collection is exposed as a read-only list
  private val _messages = ArrayDeque<LogcatMessage>()
  val messages: List<LogcatMessage>
    get() = Collections.unmodifiableList(_messages)

  private var size = 0

  init {
    assert(maxSize > 0)
  }

  fun addAll(collection: List<LogcatMessage>) {
    val addedSize = collection.sumOf { it.message.length }

    // We split into 2 flows.
    //  If the new messages are larger than maxSize already, we clear the backlog and only add the
    // messages that will fit using a sublist.
    //  Otherwise, we first remove the messages that would overflow and then add the new ones.
    // It would be simpler to just add the messages and then remove the overflowing ones but this
    // way is slightly more efficient in terms of
    // memory thrashing.
    if (addedSize >= maxSize) {
      _messages.clear()
      size = addedSize
      val i =
        collection.indexOfFirst {
          size -= it.message.length
          size <= maxSize
        }
      _messages.addAll(collection.subList(i + 1, collection.size))
    } else {
      size += addedSize
      while (size > maxSize) {
        size -= _messages.removeFirst().message.length
      }
      _messages.addAll(collection)
    }
  }

  fun setMaxSize(newSize: Int) {
    if (newSize < maxSize) {
      while (size > newSize) {
        size -= _messages.removeFirst().message.length
      }
    }
    maxSize = newSize
  }

  fun clear() {
    _messages.clear()
    size = 0
  }
}
