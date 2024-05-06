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
package com.android.tools.idea.transport.faketransport.commands

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Common

/**
 * This class handles begin session commands by creating a unique session each time this command is called.
 */
class BeginSession(timer: FakeTimer) : CommandHandler(timer) {
  var nextSessionId = 0L
  var attachAgentCalled = false
  var agentStatus = Common.AgentData.Status.ATTACHED

  fun getAgentAttachCalled(): Boolean {
    return attachAgentCalled
  }

  override fun handleCommand(command: Command, events: MutableList<Common.Event>) {
    nextSessionId++
    attachAgentCalled = command.beginSession.hasJvmtiConfig() && command.beginSession.jvmtiConfig.attachAgent
    if (attachAgentCalled) {
      events.add(Common.Event.newBuilder().apply {
        pid = command.pid
        kind = Common.Event.Kind.AGENT
        timestamp = timer.currentTimeNs
        agentData = Common.AgentData.newBuilder().apply {
          status = agentStatus
        }.build()
      }.build())
    }
    events.add(Common.Event.newBuilder().apply {
      groupId = nextSessionId
      pid = command.pid
      kind = Common.Event.Kind.SESSION
      timestamp = timer.currentTimeNs
      session = Common.SessionData.newBuilder().apply {
        sessionStarted = Common.SessionData.SessionStarted.newBuilder().apply {
          sessionId = nextSessionId
          streamId = command.streamId
          pid = command.pid
          startTimestampEpochMs = command.beginSession.requestTimeEpochMs
          sessionName = command.beginSession.sessionName
          processAbi = command.beginSession.processAbi
          jvmtiEnabled = attachAgentCalled
          type = Common.SessionData.SessionStarted.SessionType.FULL
          taskType = command.beginSession.taskType
        }.build()
      }.build()
    }.build())
  }
}