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
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

const val CHANNEL_CAPACITY = 10

/**
 * Prints formatted [LogCatMessage]s to a [Document] with coloring provided by a [LogcatColors].
 */
internal class MessageProcessor(
  parentDisposable: Disposable,
  private val formatMessagesInto: (TextAccumulator, List<LogCatMessage>) -> Unit,
  private val appendMessages: suspend (TextAccumulator) -> Unit,
) {
  private val channel = Channel<List<LogCatMessage>>(CHANNEL_CAPACITY)

  init {
    val exceptionHandler = CoroutineExceptionHandler { _, e ->
      thisLogger().error("Error processing logcat message", e)
    }
    AndroidCoroutineScope(parentDisposable, workerThread).launch(exceptionHandler) {
      val textAccumulator = TextAccumulator()
      while (true) {
        // This may seem like overkill, but it will become clear with next cl where multiple results from channel.receive() are added to a
        // single TextAccumulator before it's sent to the UI thread.
        formatMessagesInto(textAccumulator, channel.receive())
        appendMessages(textAccumulator)
        textAccumulator.clear()
      }
    }
  }

  internal suspend fun appendMessages(messages: List<LogCatMessage>) {
    channel.send(messages)
  }

  @Suppress("EXPERIMENTAL_API_USAGE")
  @TestOnly
  internal fun isChannelEmpty() = channel.isEmpty
}
