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
import com.android.tools.profiler.proto.Profiler

/**
 * Abstract class that manages test commands for the events framework. Commands are expected to behave in
 * the same way perfd/perfd-host will. If a command is expected to generate an event, that event can be
 * added to the events list. If a command is expected to update the state of a previously generated event
 * the command should find the proper event group and add the new event to that group accordingly.
 */
abstract class CommandHandler(val timer: FakeTimer) {
  /**
   * Each command shall implement handle command. This method is expected to modify the events list passed in. The
   * set of events passed in will already be filtered to a specific stream so each command does not need to handle this.
   */
  abstract fun handleCommand(command: Profiler.Command, events: MutableList<Profiler.EventGroup.Builder>)
}
