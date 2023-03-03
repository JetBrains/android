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
package com.android.tools.idea.concurrency

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private object ConflatingFlow

/**
 * Similar to [kotlinx.coroutines.flow.conflate] but it does allow the given [operation] to join the previous unprocessed value
 * and the new one.
 * If the flow is slow to process elements, you can use this call to bundle the previous elements instead of building
 * a backlog.
 */
fun <T> Flow<T>.conflateLatest(operation: suspend (waiting: T, new: T) -> T): Flow<T> = channelFlow {
  // We use this waitingChannel as the waiting area with a buffer of one. We use two competing consumers that will try to receive the
  // element in the buffer: the sender (declared within the launch) and the collector (below).
  // When the sender receives an element, it passes back to the consumer channel using `send`. It will wait until the element is processed
  // before going into the next one.
  // If the collector is the one to win, it means the sender was busy, so we "accumulate" the waiting and new values and put it back to the
  // the waiting queue.
  val waitingChannel = Channel<T>(1)
  launch {
    waitingChannel
      .receiveAsFlow()
      .collect {
        send(it)
      }
  }

  onCompletion {
    waitingChannel.close()
  }
    .collect { value ->
      val waitingValue = waitingChannel.tryReceive().getOrNull()
      val newWaitingValue = if (waitingValue == null)
        value
      else
        operation(waitingValue, value)
      waitingChannel.trySend(newWaitingValue).also { sendResult ->
        if (sendResult.isFailure) {
          Logger.getInstance(ConflatingFlow::class.java).warn("Failed to send new value result=$sendResult")
        }
      }
    }
}
  // 0 buffer so send blocks when sending the accumulator until the flow has processed
  // the element.
  .buffer(0)