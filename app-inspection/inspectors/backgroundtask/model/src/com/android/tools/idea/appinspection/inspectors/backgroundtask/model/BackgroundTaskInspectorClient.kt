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

import androidx.work.inspection.WorkManagerInspectorProtocol
import backgroundtask.inspection.BackgroundTaskInspectorProtocol
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed class WmiMessengerTarget {
  class Resolved(val messenger: AppInspectorMessenger) : WmiMessengerTarget()
  class Unresolved(val error: String) : WmiMessengerTarget()
}

/**
 * Class used to send commands to and handle events from the on-device work manager inspector and background task inspector.
 */
class BackgroundTaskInspectorClient(
  private val btiMessenger: AppInspectorMessenger,
  private val wmiMessengerTarget: WmiMessengerTarget,
  val scope: CoroutineScope,
  val uiThread: CoroutineDispatcher
) {
  private val _listeners = mutableListOf<(EventWrapper) -> Unit>()

  /**
   * Add a listener which is fired whenever a new event is collected.
   */
  fun addEventListener(listener: (EventWrapper) -> Unit) = _listeners.add(listener)

  init {
    val trackBackgroundTaskCommand = BackgroundTaskInspectorProtocol.Command.newBuilder()
      .setTrackBackgroundTask(BackgroundTaskInspectorProtocol.TrackBackgroundTaskCommand.getDefaultInstance())
      .build()
    scope.launch {
      btiMessenger.sendRawCommand(trackBackgroundTaskCommand.toByteArray())
      btiMessenger.eventFlow.collect { eventData ->
        _listeners.forEach { listener -> listener(EventWrapper(EventWrapper.Case.BACKGROUND_TASK, eventData)) }
      }
    }

    if (wmiMessengerTarget is WmiMessengerTarget.Resolved) {
      val trackWorkManagerCommand = WorkManagerInspectorProtocol.Command.newBuilder()
        .setTrackWorkManager(WorkManagerInspectorProtocol.TrackWorkManagerCommand.getDefaultInstance())
        .build()
      scope.launch {
        wmiMessengerTarget.messenger.sendRawCommand(trackWorkManagerCommand.toByteArray())
        wmiMessengerTarget.messenger.eventFlow.collect { eventData ->
          _listeners.forEach { listener -> listener(EventWrapper(EventWrapper.Case.WORK, eventData)) }
        }
      }
    }
  }
}
