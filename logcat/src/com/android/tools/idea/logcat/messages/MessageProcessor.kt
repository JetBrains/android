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

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.filters.LogcatFilter
import com.android.tools.idea.logcat.filters.LogcatMasterFilter
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.util.LOGGER
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.Document
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.time.Clock
import kotlin.system.measureTimeMillis

const val CHANNEL_CAPACITY = 10
const val MAX_TIME_PER_BATCH_MS = 100

/**
 * Prints formatted [LogcatMessage]s to a [Document] with coloring provided by a [LogcatColors].
 */
internal class MessageProcessor @TestOnly constructor(
  private val logcatPresenter: LogcatPresenter,
  private val formatMessagesInto: (TextAccumulator, List<LogcatMessage>) -> Unit,
  var logcatFilter: LogcatFilter?,
  private val clock: Clock,
  private val maxTimePerBatchMs: Int,
  private val maxMessagesPerBatch: Int,
  autoStart: Boolean,
) {
  constructor(
    logcatPresenter: LogcatPresenter,
    formatMessagesInto: (TextAccumulator, List<LogcatMessage>) -> Unit,
    logcatFilter: LogcatFilter?,
  ) : this(
    logcatPresenter,
    formatMessagesInto,
    logcatFilter,
    Clock.systemDefaultZone(),
    MAX_TIME_PER_BATCH_MS,
    StudioFlags.LOGCAT_MAX_MESSAGES_PER_BATCH.get(),
    autoStart = true)

  private val messageChannel = Channel<List<LogcatMessage>>(CHANNEL_CAPACITY)

  init {
    if (autoStart) {
      start()
    }
  }

  internal suspend fun appendMessages(messages: List<LogcatMessage>): List<LogcatMessage> {
    val filteredMessages = LogcatMasterFilter(logcatFilter).filter(messages)
    if (filteredMessages.isNotEmpty()) {
      LOGGER.debug { "Sending ${filteredMessages.size} messages to messageChannel" }
      messageChannel.send(filteredMessages)
    }
    return filteredMessages
  }

  // TODO(b/200212377): @ExperimentalCoroutinesApi ReceiveChannel#isEmpty is required. See bug for details.
  @Suppress("OPT_IN_USAGE")
  @TestOnly
  internal fun isChannelEmpty() = messageChannel.isEmpty

  @TestOnly
  internal fun start() {
    val exceptionHandler = CoroutineExceptionHandler { _, e ->
      LOGGER.error("Error processing logcat message", e)
    }
    AndroidCoroutineScope(logcatPresenter, workerThread).launch(exceptionHandler) {
      // TODO(b/200322275): Manage the life cycle of textAccumulator in a more GC friendly way.
      var textAccumulator = TextAccumulator()
      var totalMessages = 0 // Number of messages in current batch
      var numMessages = 0 // Number of messages in current batch
      var lastFlushTime = 0L // The last time we flushed a batch
      var startTime = 0L // Time of arrival of the first message - used in debug log

      while (true) {
        val messages = messageChannel.receive()
        LOGGER.debug { "messageChannel received ${messages.size} messages" }
        if (startTime == 0L) {
          startTime = clock.millis()
          lastFlushTime = startTime
        }
        numMessages += messages.size
        totalMessages += messages.size
        formatMessagesInto(textAccumulator, messages)

        // TODO(b/200212377): @ExperimentalCoroutinesApi ReceiveChannel#isEmpty is required. See bug for details.
        val now = clock.millis()
        @Suppress("OPT_IN_USAGE")
        if (messageChannel.isEmpty || now - lastFlushTime > maxTimePerBatchMs || numMessages > maxMessagesPerBatch) {
          val timeInAppendMessages = measureTimeMillis { logcatPresenter.appendMessages(textAccumulator) }
          LOGGER.debug {
            val timeSinceStart = now - startTime
            val timeSinceLastFlush = now - lastFlushTime
            "timeSinceStart: $timeSinceStart " +
            "timeSinceLastFlush (ms): $timeSinceLastFlush " +
            "numMessages: $numMessages " +
            "totalMessages=$totalMessages " +
            "timeInAppendMessages=$timeInAppendMessages"
          }
          textAccumulator = TextAccumulator()
          lastFlushTime = now
          numMessages = 0
        }
      }
    }
  }
}
