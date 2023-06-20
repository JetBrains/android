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
package com.android.tools.idea.logcat.service

import com.android.processmonitor.monitor.ProcessNameMonitor
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.folding.StackTraceExpander
import com.android.tools.idea.logcat.message.LogcatHeader
import com.android.tools.idea.logcat.message.LogcatHeaderParser
import com.android.tools.idea.logcat.message.LogcatMessage
import com.intellij.openapi.Disposable
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

private const val SYSTEM_LINE_PREFIX = "--------- beginning of "

/**
 * Receives batches of lines from an `adb logcat -v long` process and assembles them into complete [LogcatMessage]'s.
 *
 * A logcat entry starts with a header:
 *
 * `[          1650901603.644  1505: 1539 W/BroadcastQueue ]`
 *
 * And is followed by one or more lines. The entry is terminated by an empty line but there is nothing preventing users from including an
 * empty line in a proper message so there is no reliable way to determine the end of a Logcat entry. The only reliable trigger that an
 * entry has ended is when a new header is parsed.
 *
 * This means that the last entry cannot be assembled until the next entry is emitted, which could cause an unwanted delay.
 *
 * To avoid this delay, after finishing precessing a batch of lines, we schedule a delayed task to flush the last message if nothing arrives
 * in time.
 *
 * Note:
 * This is flaky by definition but given the Logcat ambiguous format, it's the best we can do.
 *
 * This class is derived from [com.android.tools.idea.logcat.AndroidLogcatReceiver]
 */
internal class LogcatMessageAssembler(
  private val serialNumber: String,
  logcatFormat: LogcatHeaderParser.LogcatFormat,
  private val channel: SendChannel<List<LogcatMessage>>,
  processNameMonitor: ProcessNameMonitor,
  coroutineContext: CoroutineContext,
  private val lastMessageDelayMs: Long,
  private val cutoffTimeSeconds: Long? = null,
) : Disposable {
  private val coroutineScope = AndroidCoroutineScope(this, coroutineContext)

  private val previousState = AtomicPendingMessage()

  private val headerParser = LogcatHeaderParser(logcatFormat, processNameMonitor)

  /**
   * Parse a batch of new lines.
   *
   * Lines are expected to arrive in the following structure:
   * ```
   *   [ header 1 ]
   *   Line 1
   *   Line 2
   *   ...
   *
   *   [ header 2]
   *   Line 1
   *   Line 2
   *   ...
   * ```
   *
   * There seems to be no way to deterministically detect the end of a log entry because the EOM is indicated by an empty line but an empty
   * line can be also be a valid part of a log entry. A valid header is a good indication that the previous entry has ended, but we can't
   * just hold on to an entry until we get a new header because there could be a long pause between entries.
   *
   * We address this issue by posting a delayed job that will flush the last entry in a batch. If we get another batch before the delayed
   * job executes, we cancel it.
   */
  suspend fun processNewLines(newLines: List<String>) {
    // New batch has arrived so effectively cancel the pending job by resetting previousState
    val state = previousState.getAndReset()

    // Parse new lines and send log messages
    val batch: Batch = parseNewLines(state, newLines)
    if (batch.messages.isNotEmpty()) {
      // Use the timestamp of the last message in the batch. We might end up sending a few older messages but not by much. There's no
      // need to be super precise.
      if (cutoffTimeSeconds == null || batch.messages.last().header.timestamp.epochSecond >= cutoffTimeSeconds) {
        channel.send(batch.messages)
      }
    }

    // Save the last header/lines to handle in the next batch or in the delayed job
    val partialMessage = PartialMessage(batch.lastHeader, batch.lastLines)
    previousState.set(partialMessage)

    // If there is a valid last message in the batch, queue it for sending in case there is no imminent next batch coming
    if (batch.lastHeader != null && batch.lastLines.isNotEmpty()) {
      coroutineScope.launch {
        // This is flaky by definition but given the Logcat ambiguous format, it's the best we can do. See class KDoc
        delay(lastMessageDelayMs)
        val message = getAndResetPendingMessage(partialMessage)
        if (message != null) {
          if (cutoffTimeSeconds == null || message.header.timestamp.epochSecond >= cutoffTimeSeconds) {
            channel.send(listOf(message))
          }
        }
      }
    }
  }

  fun getAndResetLastMessage(): LogcatMessage? = previousState.getAndReset()?.toLogcatMessage()

  private fun getAndResetPendingMessage(expected: PartialMessage): LogcatMessage? = previousState.getAndResetIf(expected)?.toLogcatMessage()

  private fun parseNewLines(
    state: PartialMessage?, newLines: List<String>): Batch {

    var lastHeader = state?.header
    val lastLines = state?.lines?.toMutableList() ?: mutableListOf()
    val batchMessages = mutableListOf<LogcatMessage>()

    for (line in newLines.map { it.fixLine() }) {
      if (line.isSystemLine()) {
        batchMessages.add(LogcatMessage(SYSTEM_HEADER, line))
        continue
      }
      val header = headerParser.parseHeader(line, serialNumber)
      if (header != null) {
        // It's a header, flush active lines.
        if (lastHeader != null && lastLines.isNotEmpty()) {
          batchMessages.add(LogcatMessage(lastHeader, lastLines.toMessage()))
        }
        // previous lines without a previous header are discarded
        lastLines.clear()
        lastHeader = header
      }
      else {
        lastLines.add(line)
      }
    }
    return Batch(batchMessages, lastHeader, lastLines)
  }

  /**
   * A batch consists of the first n-1 log entries in a batch. The last entry can be incomplete and is stored as a header and a list of
   * lines.
   */
  private class Batch(val messages: List<LogcatMessage>, val lastHeader: LogcatHeader?, val lastLines: List<String>)

  /**
   * A header and lines of a possibly unfinished message.
   */
  private class PartialMessage(val header: LogcatHeader?, val lines: List<String>) {
    fun toLogcatMessage() = header?.let { LogcatMessage(header, lines.toMessage()) }
  }

  private class AtomicPendingMessage {
    private val data: AtomicReference<PartialMessage?> = AtomicReference<PartialMessage?>()

    fun getAndReset(): PartialMessage? = data.getAndSet(null)

    fun getAndResetIf(expected: PartialMessage): PartialMessage? {
      val actual = data.compareAndExchange(expected, null)
      return if (actual === expected) actual else null
    }

    fun set(value: PartialMessage?) {
      data.set(value)
    }
  }

  override fun dispose() {}
}

private fun String.isSystemLine(): Boolean {
  return startsWith(SYSTEM_LINE_PREFIX)

}

/**
 * Really, the user's log should never put any system characters in it ever - that will cause
 * it to get filtered by our strict regex patterns (see AndroidLogcatFormatter). The reason
 * this might happen in practice is due to a bug where either adb or logcat (not sure which)
 * is too aggressive about converting \n's to \r\n's, including those that are quoted. This
 * means that a user's log, if it uses \r\n itself, is converted to \r\r\n. Then, when
 * MultiLineReceiver, which expects valid input, strips out \r\n, it leaves behind an extra \r.
 *
 * Unfortunately this isn't a case where we can fix the root cause because adb and logcat are
 * both external to Android Studio. In fact, the latest adb/logcat versions have already fixed
 * this issue! But we still need to run properly with older versions. Also, putting this fix in
 * MultiLineReceiver isn't right either because it is used for more than just receiving logcat.
 */
private fun String.fixLine(): String {
  return replace("\r", "")
}

private fun List<String>.toMessage(): String = StackTraceExpander.process(this).joinToString("\n").trimEnd('\n')
