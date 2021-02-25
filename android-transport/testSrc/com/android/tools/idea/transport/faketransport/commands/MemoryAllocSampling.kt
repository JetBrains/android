/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport.faketransport.commands

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Common

class MemoryAllocSampling(timer: FakeTimer) : CommandHandler(timer) {
  var samplingRate = 1

  override fun handleCommand(command: Command, events: MutableList<Common.Event>) {
    samplingRate = command.memoryAllocSampling.samplingNumInterval
    events.add(Common.Event.newBuilder().apply {
      pid = command.pid
      kind = Common.Event.Kind.MEMORY_ALLOC_SAMPLING
      timestamp = timer.currentTimeNs
      memoryAllocSampling = command.memoryAllocSampling
    }.build())
  }
}
