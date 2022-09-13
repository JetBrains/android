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

import backgroundtask.inspection.BackgroundTaskInspectorProtocol
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.BackgroundTaskEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EventWrapper
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.getTopExternalClassSimpleName

/**
 * An entry with all information of an Alarm Task.
 *
 * @param id a unique identifier across background entries other than work.
 */
class AlarmEntry(override val id: String) : BackgroundTaskEntry {
  enum class State {
    SET,
    CANCELLED,
    FIRED,
    UNSPECIFIED
  }

  private var _className = ""
  private var _status = State.UNSPECIFIED
  private var _startTime = -1L
  private var _isValid = false
  private var _tags = mutableListOf<String>()

  override val isValid get() = _isValid

  override val className get() = _className

  override val status get() = _status.name

  override val startTimeMs get() = _startTime

  override val tags get() = _tags
  override val callstacks = mutableListOf<BackgroundTaskCallStack>()
  override val retries = 0

  var alarmSet: BackgroundTaskInspectorProtocol.AlarmSet? = null
    private set
  val alarmFiredTimestamps = mutableListOf<Long>()
  var latestEvent: BackgroundTaskInspectorProtocol.Event? = null
    private set

  override fun consume(eventWrapper: EventWrapper) {
    latestEvent = eventWrapper.backgroundTaskEvent
    val timestamp = eventWrapper.backgroundTaskEvent.timestamp
    val backgroundTaskEvent = latestEvent!!.backgroundTaskEvent
    when (backgroundTaskEvent.metadataCase) {
      BackgroundTaskEvent.MetadataCase.ALARM_SET -> {
        _isValid = true
        alarmSet = backgroundTaskEvent.alarmSet
        _className = getTopExternalClassSimpleName(backgroundTaskEvent.stacktrace, "android.app.AlarmManager") ?: "Alarm $id"
        _status = State.SET
        _startTime = latestEvent!!.timestamp
        if (alarmSet!!.hasListener()) {
          _tags.add(alarmSet!!.listener.tag)
        }
        callstacks.clear()
        callstacks.add(BackgroundTaskCallStack(timestamp, backgroundTaskEvent.stacktrace))
      }
      BackgroundTaskEvent.MetadataCase.ALARM_CANCELLED -> {
        _status = State.CANCELLED
        callstacks.add(BackgroundTaskCallStack(timestamp, backgroundTaskEvent.stacktrace))
      }
      BackgroundTaskEvent.MetadataCase.ALARM_FIRED -> {
        _status = State.FIRED
        alarmFiredTimestamps.add(latestEvent!!.timestamp)
      }
      else -> throw RuntimeException()
    }
  }
}
