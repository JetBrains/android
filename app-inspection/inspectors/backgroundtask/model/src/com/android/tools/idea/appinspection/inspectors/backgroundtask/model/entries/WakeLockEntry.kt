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
 * An entry with all information of a WakeLock Task.
 *
 * @param id a unique identifier across background entries other than work.
 */
class WakeLockEntry(override val id: String) : BackgroundTaskEntry {
  enum class State {
    ACQUIRED,
    RELEASED,
    UNSPECIFIED
  }

  private var _className = ""
  private var _status = State.UNSPECIFIED
  private var _startTime = -1L
  private var _isValid = false

  override val isValid
    get() = _isValid

  override val className
    get() = _className

  override val status
    get() = _status.name

  override val startTimeMs
    get() = _startTime

  override val tags = mutableListOf<String>()
  override val callstacks = mutableListOf<BackgroundTaskCallStack>()
  override val retries = 0

  var acquired: BackgroundTaskInspectorProtocol.Event? = null
  var released: BackgroundTaskInspectorProtocol.Event? = null

  override fun consume(eventWrapper: EventWrapper) {
    val backgroundTaskEvent = eventWrapper.backgroundTaskEvent
    val timestamp = eventWrapper.backgroundTaskEvent.timestamp
    when (backgroundTaskEvent.backgroundTaskEvent.metadataCase) {
      BackgroundTaskEvent.MetadataCase.WAKE_LOCK_ACQUIRED -> {
        _isValid = true
        acquired = backgroundTaskEvent
        _className =
          getTopExternalClassSimpleName(
            backgroundTaskEvent.backgroundTaskEvent.stacktrace,
            "android.os.PowerManager\$WakeLock"
          )
            ?: "WakeLock $id"
        _status = State.ACQUIRED
        _startTime = timestamp
        tags.add(acquired!!.backgroundTaskEvent.wakeLockAcquired.tag)
        callstacks.clear()
        callstacks.add(
          BackgroundTaskCallStack(timestamp, backgroundTaskEvent.backgroundTaskEvent.stacktrace)
        )
      }
      BackgroundTaskEvent.MetadataCase.WAKE_LOCK_RELEASED -> {
        released = backgroundTaskEvent
        _status = State.RELEASED
        callstacks.add(
          BackgroundTaskCallStack(timestamp, backgroundTaskEvent.backgroundTaskEvent.stacktrace)
        )
      }
    }
  }
}
