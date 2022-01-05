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

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.nio.channels.InterruptedByTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Coroutine-friendly wrapper around an [AsynchronousSocketChannel] with suspending read and write
 * methods.
 */
class SuspendingSocketChannel(
  asynchronousChannel: AsynchronousSocketChannel
) : SuspendingNetworkChannel<AsynchronousSocketChannel>(asynchronousChannel) {

  private val readCompletionHandler = CompletionHandlerAdapter(Operation.READ)
  private val writeCompletionHandler = CompletionHandlerAdapter(Operation.WRITE)

  /**
   * Reads a sequence of bytes from this channel into the given buffer.
   *
   * [ByteBuffer.position] is advanced to the end of the read data. The read operation might not
   * fill the buffer, but it is guaranteed to read at least one byte unless there is no remaining
   * space in the buffer, or the end of channel is reached.
   *
   * @throws InterruptedByTimeoutException if the timeout elapses before the operation completes
   * @throws EOFException if the end of channel is reached before reading any bytes
   * @throws IOException if any I/O error occurs during the operation
   */
  suspend fun read(buffer: ByteBuffer, timeout: Long = 0, unit: TimeUnit = TimeUnit.MILLISECONDS) {
    return suspendCancellableCoroutine { continuation ->
      // Ensure that the asynchronous operation is stopped if the coroutine is cancelled.
      closeOnCancel(continuation)
      networkChannel.read(buffer, timeout, unit, continuation, readCompletionHandler)
    }
  }

  /**
   * Reads a sequence of bytes from this channel into the given buffer until the no more space left
   * in the buffer or until the end of the channel is reached. [ByteBuffer.position] is advanced to
   * the end of the read data.
   *
   * @throws InterruptedByTimeoutException if the timeout elapses before the operation completes
   * @throws EOFException if the end of channel is reached before filling the buffer; all available
   *     data is already transferred to the buffer when the exception is thrown
   * @throws IOException if any I/O error occurs during the operation
   */
  suspend fun readFully(buffer: ByteBuffer, timeout: Long = 0, unit: TimeUnit = TimeUnit.MILLISECONDS) {
    if (buffer.hasRemaining()) {
      var remainingTime = unit.convert(timeout, TimeUnit.MILLISECONDS)
      val deadline = if (timeout == 0L) 0 else System.currentTimeMillis() + unit.toMillis(timeout)
      while (true) {
        read(buffer, remainingTime, TimeUnit.MILLISECONDS)
        if (!buffer.hasRemaining()) {
          break
        }
        if (timeout != 0L) {
          remainingTime = deadline - System.currentTimeMillis()
          if (remainingTime <= 0) {
            throw InterruptedByTimeoutException()
          }
        }
      }
    }
  }

  /**
   * Writes a sequence of bytes from the given buffer into this channel.
   *
   * [ByteBuffer.position] is advanced to the end of the written data. The write operation might not
   * write all data remaining in the buffer, but it is guaranteed to write at least one byte unless
   * there is no remaining data in the buffer.
   *
   * @throws InterruptedByTimeoutException if the timeout elapses before the operation completes
   * @throws IOException if any I/O error occurs during the operation
   */
  suspend fun write(buffer: ByteBuffer, timeout: Long = 0, unit: TimeUnit = TimeUnit.MILLISECONDS) {
    return suspendCancellableCoroutine { continuation ->
      // Ensure that the asynchronous operation is stopped if the coroutine is cancelled.
      closeOnCancel(continuation)
      networkChannel.write(buffer, timeout, unit, continuation, writeCompletionHandler)
    }
  }

  /**
   * Writes all remaining data contained in the given buffer into this channel.
   * [ByteBuffer.position] is advanced to the end of the written data.
   *
   * @throws InterruptedByTimeoutException if the timeout elapses before the operation completes
   * @throws IOException if any I/O error occurs during the operation
   */
  suspend fun writeFully(buffer: ByteBuffer, timeout: Long = 0, unit: TimeUnit = TimeUnit.MILLISECONDS) {
    if (buffer.hasRemaining()) {
      var remainingTime = unit.convert(timeout, TimeUnit.MILLISECONDS)
      val deadline = if (timeout == 0L) 0 else System.currentTimeMillis() + unit.toMillis(timeout)
      while (true) {
        write(buffer, remainingTime, TimeUnit.MILLISECONDS)
        if (!buffer.hasRemaining()) {
          break
        }
        if (timeout != 0L) {
          remainingTime = deadline - System.currentTimeMillis()
          if (remainingTime <= 0) {
            throw InterruptedByTimeoutException()
          }
        }
      }
    }
  }

  private class CompletionHandlerAdapter(private val operation: Operation) : CompletionHandler<Int, CancellableContinuation<Unit>> {

    override fun completed(result: Int, continuation: CancellableContinuation<Unit>) {
      if (result == -1) {
        assert(operation == Operation.READ)
        continuation.resumeWithException(EOFException("Reached the end of channel"))
        return
      }

      continuation.resume(Unit)
    }

    override fun failed(e: Throwable, continuation: CancellableContinuation<Unit>) {
      val message = if (operation == Operation.READ) "Error reading from asynchronous channel"
          else "Error writing to asynchronous channel"
      continuation.resumeWithException(IOException(message, e))
    }
  }

  private enum class Operation {
    READ, WRITE
  }
}