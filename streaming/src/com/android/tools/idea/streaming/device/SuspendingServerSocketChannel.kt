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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Coroutine-friendly wrapper around an [AsynchronousServerSocketChannel] with the suspending
 * [accept] method.
 */
class SuspendingServerSocketChannel(
  asynchronousChannel: AsynchronousServerSocketChannel
) : SuspendingNetworkChannel<AsynchronousServerSocketChannel>(asynchronousChannel) {

  private val completionHandler = CompletionHandlerAdapter()

  /**
   * Accepts a connection.
   *
   * See [AsynchronousServerSocketChannel.accept]
   */
  suspend fun accept() : SuspendingSocketChannel {
    return suspendCancellableCoroutine { continuation ->
      // Ensure that the asynchronous operation is stopped if the coroutine is cancelled.
      closeOnCancel(continuation)
      networkChannel.accept(continuation, completionHandler)
    }
  }

  private class CompletionHandlerAdapter :
      CompletionHandler<AsynchronousSocketChannel, CancellableContinuation<SuspendingSocketChannel>> {

    override fun completed(socketChannel: AsynchronousSocketChannel, continuation: CancellableContinuation<SuspendingSocketChannel>) {
      continuation.resume(SuspendingSocketChannel(socketChannel))
    }

    override fun failed(e: Throwable, continuation: CancellableContinuation<SuspendingSocketChannel>) {
      continuation.resumeWithException(e)
    }
  }
}