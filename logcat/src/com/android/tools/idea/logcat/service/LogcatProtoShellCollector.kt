/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.adblib.ShellV2Collector
import com.android.logcat.proto.LogcatEntryProto
import com.android.logcat.proto.LogcatPriorityProto
import com.android.processmonitor.monitor.ProcessNameMonitor
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.message.LogcatHeader
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.util.LOGGER
import com.google.protobuf.ByteString
import kotlinx.coroutines.flow.FlowCollector
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.nio.charset.Charset
import java.time.Instant

/**
 * A [ShellV2Collector] implementation that collects `stdout` as a sequence of lists of
 * [LogcatMessage]'s
 *
 * Since a message can be split by buffer boundaries, we need to save a state of leftover bytes from
 * the previous invocation.
 *
 * The trivial way of handling the leftover bytes is to always create a new buffer by concatenating
 * the leftover bytes with the new buffer. However, this results in unnecessary memory thrashing.
 * Rather, we only copy the remaining bytes when processing the first log message in the buffer.
 * After the first message is handled, we proceed by reading directly from the provided buffer.
 */
class LogcatProtoShellCollector(
  private val serialNumber: String,
  private val processNameMonitor: ProcessNameMonitor,
) : ShellV2Collector<List<LogcatMessage>> {
  // Bytes left over from the last call. These bytes are the start of the next message to be read.
  private var leftoverBytes: ByteArray = ByteArray(0)

  override suspend fun start(collector: FlowCollector<List<LogcatMessage>>) {}

  override suspend fun collectStdout(
    collector: FlowCollector<List<LogcatMessage>>,
    stdout: ByteBuffer
  ) {
    stdout.order(LITTLE_ENDIAN)

    val messages = mutableListOf<LogcatMessage>()
    messages.add(getFirstLogMessage(stdout) ?: return)

    while (true) {
      messages.add(getLogcatMessage(stdout) ?: break)
    }
    leftoverBytes = stdout.getBytes(stdout.remaining())
    collector.emit(messages)
  }

  override suspend fun collectStderr(
    collector: FlowCollector<List<LogcatMessage>>,
    stderr: ByteBuffer
  ) {}

  override suspend fun end(collector: FlowCollector<List<LogcatMessage>>, exitCode: Int) {}

  /**
   * Tries to read the first [LogcatMessage] in a buffer.
   *
   * If [leftoverBytes] is not empty, then it will contain the first bytes fo the message while the
   * buffer contains the rest.
   *
   * @return The first LogcatMessage in `leftoverBytes + buffer` or null if there are not enough
   *   bytes.
   */
  private fun getFirstLogMessage(buffer: ByteBuffer): LogcatMessage? {
    val remaining = buffer.remaining()

    // Get the size, so we can check if we have enough bytes to read the message.
    val size =
      when {
        // If there are no leftover bytes, read the sizes from the buffer directly
        leftoverBytes.isEmpty() -> {
          if (remaining < Long.SIZE_BYTES) {
            // But if there are not enough bytes, we have to wait for another buffer.
            leftoverBytes = buffer.getBytes(remaining)
            return null
          }
          val size = buffer.getSize()
          buffer.position(0)
          size
        }

        // If leftoverBytes already has the bytes needed to get the sizes, just get them from there.
        leftoverBytes.size >= Long.SIZE_BYTES -> leftoverBytes.getSize()

        // This means that leftoverBytes doesn't have enough bytes to get the sizes, so we need to
        // read the missing bytes from the buffer
        else -> {
          val needed = Long.SIZE_BYTES - leftoverBytes.size
          if (remaining < needed) {
            // Not enough bytes in the buffer, add them to the leftover bytes we already have.
            // Note that this is very unlikely, probably impossible under normal circumstances. It
            // means the buffer being handled contains
            // <7 bytes.
            leftoverBytes += buffer.getBytes(remaining)
            return null
          }
          val bytes = leftoverBytes + buffer.getBytes(needed)
          // Reset the buffer because we will read the remainder again below
          buffer.position(0)
          bytes.getSize()
        }
      }

    val needed = Long.SIZE_BYTES + size - leftoverBytes.size
    if (remaining < needed) {
      // Not enough bytes in the buffer, add them to the leftover bytes we already have.
      // Note: Also unlikely but might happen if the logcat message is very large.
      leftoverBytes += buffer.getBytes(remaining)
      return null
    }

    // By this point, we have enough bytes to read a proto. If leftoverBytes is empty, read directly
    // from the provided buffer. If not,
    // create a new buffer to read from.
    val messageBuffer =
      when {
        leftoverBytes.isEmpty() -> buffer
        else -> ByteBuffer.wrap(leftoverBytes + buffer.getBytes(needed)).order(LITTLE_ENDIAN)
      }
    return getLogcatMessage(messageBuffer)
  }

  /**
   * Tries to read a [LogcatMessage] from a buffer.
   *
   * Returns `null` if there are not enough bytes to read a complete entry.
   */
  private fun getLogcatMessage(buffer: ByteBuffer): LogcatMessage? {
    if (buffer.remaining() < Long.SIZE_BYTES) {
      return null
    }
    val start = buffer.position()
    val size = buffer.getSize()
    if (buffer.remaining() < size) {
      // Not enough bytes to read the message. Reset the buffer, so the caller can save the bytes
      // for next invocation.
      buffer.position(start)
      return null
    }
    val position = buffer.position()
    val entry = LogcatEntryProto.parseFrom(buffer.slice(position, size))
    buffer.position(position + size)
    return entry.toLogcatMessage()
  }

  /** Reads the payload & header sizes from a [ByteArray] */
  private fun ByteArray.getSize() = ByteBuffer.wrap(this).order(LITTLE_ENDIAN).getLong().toInt()

  private fun LogcatEntryProto.toLogcatMessage() =
    LogcatMessage(
      LogcatHeader(
        priority.toLogLevel(),
        pid.toInt(),
        tid.toInt(),
        processNameMonitor.getProcessNames(serialNumber, pid.toInt())?.applicationId ?: processName,
        processName,
        tag.toTag(),
        Instant.ofEpochSecond(timeSec, timeNsec)
      ),
      message.toMessage()
    )
}

private fun ByteBuffer.getSize() = Math.toIntExact(getLong())

/** Reads a given number of bytes from a [ByteBuffer] into a new [ByteArray] */
private fun ByteBuffer.getBytes(size: Int): ByteArray {
  return ByteArray(size).apply { get(this) }
}

private fun LogcatPriorityProto.toLogLevel(): LogLevel =
  when (this) {
    LogcatPriorityProto.VERBOSE -> LogLevel.VERBOSE
    LogcatPriorityProto.DEBUG -> LogLevel.DEBUG
    LogcatPriorityProto.INFO -> LogLevel.INFO
    LogcatPriorityProto.WARN -> LogLevel.WARN
    LogcatPriorityProto.ERROR -> LogLevel.ERROR
    LogcatPriorityProto.FATAL -> LogLevel.ASSERT
    else -> {
      LOGGER.debug("Unexpected priority: $this. Using 'ERROR' instead")
      LogLevel.ERROR
    }
  }

/**
 * Extracts a log tag from a [ByteString]
 *
 * The ByteString contains a null terminator which is removed before converting to a [String].
 */
private fun ByteString.toTag() = substring(0, size() - 1).toString(Charset.defaultCharset())

private const val NEWLINE = '\n'.code.toByte()

/**
 * Extracts a log message from a [ByteString]
 *
 * The ByteString contains a null terminator which is removed before converting to a [String].
 *
 * For some reason, some log messages end with a trailing newline. This newline is not included in
 * the text output of logcat. This method also removed a single trailing newline if it exists.
 */
private fun ByteString.toMessage(): String {
  val size = size()
  val end =
    when (endsWithNewline()) {
      true -> size - 1
      false -> size
    }
  return substring(0, end).toString(Charset.defaultCharset())
}

private fun ByteString.endsWithNewline() = size() > 0 && byteAt(size() - 1) == NEWLINE
