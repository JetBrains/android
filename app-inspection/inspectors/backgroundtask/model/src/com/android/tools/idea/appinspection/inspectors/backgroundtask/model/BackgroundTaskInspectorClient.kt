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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.model

import backgroundtask.inspection.BackgroundTaskInspectorProtocol.Command
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.Event
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.TrackBackgroundTaskCommand
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class BackgroundTaskInspectorClient(private val messenger: AppInspectorMessenger, private val clientScope: CoroutineScope) {
  private val _listeners = mutableListOf<() -> Unit>()
  fun addWorksChangedListener(listener: () -> Unit) = _listeners.add(listener)
  var event: String = ""

  init {
    val command = Command.newBuilder().setTrackBackgroundTask(TrackBackgroundTaskCommand.getDefaultInstance()).build()
    clientScope.launch {
      messenger.sendRawCommand(command.toByteArray())
    }
    clientScope.launch {
      messenger.eventFlow.collect { eventData ->
        event = Event.parseFrom(eventData).toString()
        _listeners.forEach { listener -> listener() }
      }
    }
  }
}