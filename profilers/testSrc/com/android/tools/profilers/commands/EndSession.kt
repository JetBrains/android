/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.commands

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Profiler

/**
 * This class handles end session commands by finding the group a begin session command created then adding a session ended event to that
 * group.
 */
class EndSession(timer: FakeTimer) : CommandHandler(timer) {
  override fun handleCommand(command: Profiler.Command, events: MutableList<Profiler.EventGroup.Builder>) {
    events.find { it.groupId == command.endSession.sessionId }!!.addEvents(Common.Event.newBuilder().apply {
      groupId = command.endSession.sessionId
      sessionId = command.endSession.sessionId
      kind = Common.Event.Kind.SESSION
      type = Common.Event.Type.SESSION_ENDED
      timestamp = timer.currentTimeNs
    })
  }
}
