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

import com.android.ddmlib.logcat.LogCatMessage

/**
 * Manages a cyclic collection of [LogCatMessage]s that is limited by the size in bytes of the payload.
 *
 * The purpose of this backlog is to allow for filtering and re-rendering of the Logcat when needed, for example, when formatting changes
 * are made.
 *
 * The goal is to have this backlog contain at least as many messages as there are displayed in the Logcat window which is also governed by
 * a cyclic buffer size. In order to achieve this, the size of a LogCatMessage is 'rounded down' to [LogCatMessage.message] which is less
 * than the minimal size required to render a message. Therefore, the backlog will contain more messages than the actual displayed window,
 * even if no filters are applied and the formatting options are at their minimum.
 *
 * TODO(aalbert): Maybe pass in the current formatting options setting and calculate the size more accurately.
 */
internal class MessageBacklog(private val maxSize: Int) {

  val messages = ArrayDeque<LogCatMessage>()
  private var size = 0

  init {
    assert(maxSize > 0)
  }

  fun addAll(collection: Collection<LogCatMessage>) {
    val addedSize = collection.sumOf { it.message.length }
    if (addedSize >= maxSize) {
      messages.clear()
      size = 0
    }
    size += addedSize
    messages.addAll(collection)
    while (size > maxSize) {
      val first = messages.removeFirst()
      size -= first.message.length
    }
  }
}