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
 * This class handles begin session commands by creating a unique session each time this command is called.
 */
class BeginSession(timer: FakeTimer) : CommandHandler(timer) {
  var nextSessionId = 0L
  var attachAgentCalled = false

  fun getAgentAttachCalled(): Boolean {
    return attachAgentCalled
  }

  override fun handleCommand(command: Profiler.Command, events: MutableList<Profiler.EventGroup.Builder>) {
    nextSessionId++
    attachAgentCalled = command.beginSession.hasJvmtiConfig() && command.beginSession.jvmtiConfig.attachAgent
    events.add(Profiler.EventGroup.newBuilder().setGroupId(nextSessionId)
                 .addEvents(Common.Event.newBuilder().apply {
                   groupId = nextSessionId
                   sessionId = nextSessionId
                   kind = Common.Event.Kind.SESSION
                   type = Common.Event.Type.SESSION_STARTED
                   timestamp = timer.currentTimeNs
                   sessionStarted = Common.SessionStarted.newBuilder().apply {
                     pid = command.beginSession.pid
                     startTimestampEpochMs = command.beginSession.requestTimeEpochMs
                     sessionName = command.beginSession.sessionName
                     type = Common.SessionStarted.SessionType.FULL
                   }.build()
                 }))
  }
}
