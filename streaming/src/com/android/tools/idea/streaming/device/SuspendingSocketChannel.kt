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
package com.android.tools.idea.streaming.device

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.IOException
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
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
   * Connects this channel to the given remote address.
   * @see AsynchronousSocketChannel.connect
   */
  suspend fun connect(remote: SocketAddress) {
    val continuationHandler = object : CompletionHandler<Void?, CancellableContinuation<Void?>> {

      override fun completed(result: Void?, continuation: CancellableContinuation<Void?>) {
        continuation.resume(null)
      }

      override fun failed(exception: Throwable, continuation: CancellableContinuation<Void?>) {
        continuation.resumeWithException(exception)
      }
    }
    suspendCancellableCoroutine<Void?> { continuation ->
      networkChannel.connect(remote, continuation, continuationHandler)
    }
  }

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
    suspendCancellableCoroutine<Unit> { continuation ->
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
    suspendCancellableCoroutine<Unit> { continuation ->
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

  companion object {
    /**
     * Opens a suspending socket channel.
     *
     * @param group the group to which the newly constructed channel should be bound, or null for the default group
     *
     * @see AsynchronousSocketChannel.open(group: AsynchronousChannelGroup?)
     */
    @JvmOverloads
    @JvmStatic
    suspend fun open(group: AsynchronousChannelGroup? = null): SuspendingSocketChannel {
      return withContext(Dispatchers.IO) {
        SuspendingSocketChannel(AsynchronousSocketChannel.open(group))
      }
    }
  }

  private class CompletionHandlerAdapter(private val operation: Operation) : CompletionHandler<Int, CancellableContinuation<Unit>> {

    override fun completed(result: Int, continuation: CancellableContinuation<Unit>) {
      if (continuation.isCancelled) {
        return
      }

      if (result == -1) {
        assert(operation == Operation.READ)
        continuation.resumeWithException(EOFException("Reached the end of channel"))
      }
      else {
        continuation.resume(Unit)
      }
    }

    override fun failed(e: Throwable, continuation: CancellableContinuation<Unit>) {
      if (continuation.isCancelled) {
        return
      }

      continuation.resumeWithException(e)
    }
  }

  private enum class Operation {
    READ, WRITE
  }
}