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

import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketAddress
import java.net.SocketOption
import java.nio.channels.ClosedChannelException
import java.nio.channels.NetworkChannel
import kotlin.coroutines.CoroutineContext

/**
 * Base class for [SuspendingSocketChannel] and [SuspendingServerSocketChannel].
 */
abstract class SuspendingNetworkChannel<T : NetworkChannel>(val networkChannel: T) : SuspendingCloseable {

  override suspend fun close() {
    withContextWithoutLoggingExceptions(SupervisorJob() + Dispatchers.IO) {
      try {
        networkChannel.close()
      }
      catch (_: ClosedChannelException) {
      }
    }
  }

  /** See [NetworkChannel.isOpen]. */
  val isOpen: Boolean
    get() = networkChannel.isOpen

  /** See [NetworkChannel.getLocalAddress]. */
  val localAddress: SocketAddress
    get() = networkChannel.localAddress

  /** See [NetworkChannel.bind]. */
  suspend fun bind(local: SocketAddress) {
    withContextWithoutLoggingExceptions(Dispatchers.IO) {
      networkChannel.bind(local)
    }
  }

  /** See [NetworkChannel.setOption]. */
  suspend fun <U : Any?> setOption(name: SocketOption<U>, value: U) {
    withContextWithoutLoggingExceptions(Dispatchers.IO) {
      networkChannel.setOption(name, value)
    }
  }

  /** See [NetworkChannel.getOption]. */
  suspend fun <U : Any?> getOption(name: SocketOption<U>): U {
    return withContextWithoutLoggingExceptions(Dispatchers.IO) {
      networkChannel.getOption(name)
    }
  }

  /** See [NetworkChannel.supportedOptions]. */
  fun supportedOptions(): Set<SocketOption<*>> {
    return networkChannel.supportedOptions()
  }

  /**
   * Closes the socket channel when the coroutine is canceled.
   */
  protected fun closeOnCancel(continuation: CancellableContinuation<*>) {
    continuation.invokeOnCancellation {
      try {
        networkChannel.close()
      } catch (e: IOException) {
        thisLogger().warn("Failed to close the network channel", e)
      }
    }
  }

  private suspend fun <T> withContextWithoutLoggingExceptions(context: CoroutineContext, block: () -> T): T =
      withContext(context) { runCatching(block) }.getOrThrow()
}