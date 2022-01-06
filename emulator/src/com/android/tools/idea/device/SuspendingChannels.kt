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
package com.android.tools.idea.device

import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Creates a new output stream backed by the given [SuspendingSocketChannel].
 *
 * Please keep in mind that all [OutputStream] operations are synchronous and therefore potentially
 * blocking.
 */
fun newOutputStream(channel: SuspendingSocketChannel, bufferSize: Int): OutputStream {
  return SuspendingChannelOutputStream(channel, bufferSize)
}

/**
 * Creates a new input stream backed by the given [SuspendingSocketChannel].
 *
 * Please keep in mind that all [InputStream] operations are synchronous and therefore potentially
 * blocking. To avoid blocking the calling thread while waiting for data to arrive, use
 * the [SuspendingInputStream.waitForData] method before attempting to read from the stream.
 */
fun newInputStream(channel: SuspendingSocketChannel, bufferSize: Int): SuspendingInputStream {
  return SuspendingChannelInputStream(channel, bufferSize)
}

abstract class SuspendingInputStream : InputStream() {
  /**
   * Reads from the channel until the internal buffer of the stream contains at least [numBytes]
   * not yet consumed bytes.
   */
  abstract suspend fun waitForData(numBytes: Int)
}

private class SuspendingChannelOutputStream(val channel: SuspendingSocketChannel, bufferSize: Int) : OutputStream() {
  private val buffer = ByteBuffer.allocate(bufferSize)

  override fun write(b: Int) {
    buffer.put(b.toByte())
    if (!buffer.hasRemaining()) {
      blockingWriteAndClearBuffer()
    }
  }

  override fun close() {
    runBlocking {
      channel.use {
        if (buffer.position() != 0) {
          writeAndClearBuffer()
        }
      }
    }
  }

  override fun flush() {
    if (buffer.position() != 0) {
      blockingWriteAndClearBuffer()
    }
  }

  private fun blockingWriteAndClearBuffer() {
    runBlocking {
      writeAndClearBuffer()
    }
  }

  private suspend fun writeAndClearBuffer() {
    buffer.flip()
    channel.writeFully(buffer)
    buffer.clear()
  }
}

private class SuspendingChannelInputStream(val channel: SuspendingSocketChannel, bufferSize: Int) : SuspendingInputStream() {
  private val buffer = ByteBuffer.allocate(bufferSize).flip()

  override fun read(): Int {
    if (!buffer.hasRemaining()) {
      runBlocking {
        buffer.clear()
        channel.read(buffer)
        buffer.flip()
      }
      if (!buffer.hasRemaining()) {
        return -1
      }
    }
    return buffer.get().toInt() and 0xFF
  }

  override fun close() {
    runBlocking {
      channel.close()
    }
  }

  override fun available(): Int {
    return buffer.remaining()
  }

  override suspend fun waitForData(numBytes: Int) {
    require(numBytes <= buffer.capacity())
    while (buffer.remaining() < numBytes) {
      buffer.compact()
      channel.read(buffer)
      buffer.flip()
    }
  }
}