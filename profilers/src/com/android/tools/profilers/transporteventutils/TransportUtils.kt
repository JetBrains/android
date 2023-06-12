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
package com.android.tools.profilers.transporteventutils

import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.StudioProfilers
import java.util.function.Function

object TransportUtils {
  @JvmStatic
  fun registerListener(profilers: StudioProfilers,
                       eventKind: Common.Event.Kind,
                       streamId: Long,
                       processId: Int,
                       commandId: Int,
                       callback: Function<Common.Event, Boolean>) {
    val eventListener = TransportEventListener(
      eventKind = eventKind,
      executor = profilers.ideServices.mainExecutor,
      filter = { event: Common.Event -> event.commandId == commandId },
      streamId = { streamId },
      processId = { processId },
      callback = { event: Common.Event -> callback.apply(event) })

    profilers.transportPoller.registerListener(eventListener)
  }
}
