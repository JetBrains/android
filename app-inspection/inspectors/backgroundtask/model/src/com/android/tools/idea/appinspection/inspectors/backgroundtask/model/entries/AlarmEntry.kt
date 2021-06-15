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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries

import backgroundtask.inspection.BackgroundTaskInspectorProtocol.BackgroundTaskEvent
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.Event

/**
 * An entry with all information of an Alarm Task.
 */
class AlarmEntry(override val id: Long) : BackgroundTaskEntry {
  enum class State {
    SET,
    CANCELLED,
    FIRED,
    UNSPECIFIED
  }

  private var _className = ""
  private var _status = State.UNSPECIFIED
  private var _startTime = -1L
  private var _isValid = true

  override val isValid get() = _isValid

  override val className get() = _className

  override val status get() = _status.name

  override val startTimeMs get() = _startTime

  override fun consume(event: Any) {
    val backgroundTaskEvent = (event as Event).backgroundTaskEvent
    when (backgroundTaskEvent.metadataCase) {
      BackgroundTaskEvent.MetadataCase.ALARM_SET -> {
        val alarmSet = backgroundTaskEvent.alarmSet
        _className = "Alarm $id"
        _status = State.SET
        _startTime = alarmSet.triggerMs
      }
      BackgroundTaskEvent.MetadataCase.ALARM_CANCELLED -> {
        _status = State.CANCELLED
      }
      BackgroundTaskEvent.MetadataCase.ALARM_FIRED -> {
        _status = State.FIRED
      }
    }
  }
}
