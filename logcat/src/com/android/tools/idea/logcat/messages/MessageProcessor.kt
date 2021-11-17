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
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.PackageNamesProvider
import com.android.tools.idea.logcat.filters.AndLogcatFilter
import com.android.tools.idea.logcat.filters.LogcatFilter
import com.android.tools.idea.logcat.filters.LogcatMasterFilter
import com.android.tools.idea.logcat.filters.ProjectAppFilter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.time.Clock

const val CHANNEL_CAPACITY = 10
const val MAX_TIME_PER_BATCH_MS = 100
const val MAX_MESSAGES_PER_BATCH = 5000

private val logger by lazy { Logger.getInstance(MessageProcessor::class.java) }

/**
 * Prints formatted [LogCatMessage]s to a [Document] with coloring provided by a [LogcatColors].
 */
internal class MessageProcessor(
  private val logcatPresenter: LogcatPresenter,
  private val formatMessagesInto: (TextAccumulator, List<LogCatMessage>) -> Unit,
  packageNamesProvider: PackageNamesProvider,
  var logcatFilter: LogcatFilter?,
  var showOnlyProjectApps: Boolean,
  private val clock: Clock = Clock.systemDefaultZone(),
  private val maxTimePerBatchMs: Int = MAX_TIME_PER_BATCH_MS,
  private val maxMessagesPerBatch: Int = MAX_MESSAGES_PER_BATCH,
) {
  private val messageChannel = Channel<List<LogCatMessage>>(CHANNEL_CAPACITY)
  private val projectAppFilter = ProjectAppFilter(packageNamesProvider)

  init {
    processMessageChannel()
  }

  internal suspend fun appendMessages(messages: List<LogCatMessage>) {
    val filter = when {
      showOnlyProjectApps && logcatFilter != null -> AndLogcatFilter(logcatFilter!!, projectAppFilter)
      showOnlyProjectApps -> projectAppFilter
      else -> logcatFilter
    }
    messageChannel.send(LogcatMasterFilter(filter).filter(messages))
  }

  // TODO(b/200212377): @ExperimentalCoroutinesApi ReceiveChannel#isEmpty is required. See bug for details.
  @Suppress("EXPERIMENTAL_API_USAGE")
  @TestOnly
  internal fun isChannelEmpty() = messageChannel.isEmpty

  private fun processMessageChannel() {
    val exceptionHandler = CoroutineExceptionHandler { _, e ->
      thisLogger().error("Error processing logcat message", e)
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
        if (startTime == 0L) {
          startTime = clock.millis()
          lastFlushTime = startTime
        }
        numMessages += messages.size
        totalMessages += messages.size
        formatMessagesInto(textAccumulator, messages)

        // TODO(b/200212377): @ExperimentalCoroutinesApi ReceiveChannel#isEmpty is required. See bug for details.
        @Suppress("EXPERIMENTAL_API_USAGE")
        if (messageChannel.isEmpty || clock.millis() - lastFlushTime > maxTimePerBatchMs || numMessages > maxMessagesPerBatch) {
          logcatPresenter.appendMessages(textAccumulator)
          val now = clock.millis()
          logger.debug {
            val timeSinceStart = now - startTime
            val timeSinceLastFlush = now - lastFlushTime
            "timeSinceStart: $timeSinceStart timeSinceLastFlush (ms): $timeSinceLastFlush  numMessages: $numMessages totalMessages=$totalMessages"
          }
          textAccumulator = TextAccumulator()
          lastFlushTime = now
          numMessages = 0
        }
      }
    }
  }
}
