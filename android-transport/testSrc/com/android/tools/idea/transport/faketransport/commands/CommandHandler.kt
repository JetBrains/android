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
 * Abstract class that manages test commands for the events framework. Commands are expected to behave in
 * the same way perfd/transport-database will. If a command is expected to generate an event, that event can be
 * added to the events list. If a command is expected to update the state of a previously generated event
 * the command should find the proper event group and add the new event to that group accordingly.
 */
abstract class CommandHandler(val timer: FakeTimer, private val isTaskBasedUxEnabled: Boolean = false) {
  /**
   * Each command shall implement handle command. This method is expected to modify the events list passed in. The
   * set of events passed in will already be filtered to a specific stream so each command does not need to handle this.
   */
  abstract fun handleCommand(command: Command, events: MutableList<Common.Event>)

  /**
   * Populates the fake transport events with a session ended event if Task-Based UX is enabled.
   */
  fun addSessionEndedEvent(command: Command, events: MutableList<Common.Event>) {
    if (isTaskBasedUxEnabled) {
      events.add(Common.Event.newBuilder().apply {
        groupId = command.sessionId
        pid = command.pid
        kind = Common.Event.Kind.SESSION
        timestamp = timer.currentTimeNs
        isEnded = true
      }.build())
    }
  }
}
