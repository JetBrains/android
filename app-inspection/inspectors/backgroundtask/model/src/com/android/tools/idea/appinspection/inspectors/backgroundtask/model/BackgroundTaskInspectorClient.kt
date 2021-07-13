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
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.BackgroundTaskEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WorkEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.createBackgroundTaskEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

typealias EntryUpdateEventListener = (type: EntryUpdateEventType, entry: BackgroundTaskEntry) -> Unit

enum class EntryUpdateEventType {
  ADD,
  UPDATE,
  REMOVE
}

sealed class WmiMessengerTarget {
  class Resolved(val messenger: AppInspectorMessenger) : WmiMessengerTarget()
  class Unresolved(val error: String) : WmiMessengerTarget()
}

/**
 * A Wrapper class that contains either [WorkManagerInspectorProtocol.Event] or [BackgroundTaskInspectorProtocol.Event].
 */
class EventWrapper(val case: Case, data: ByteArray) {
  enum class Case {
    WORK,
    BACKGROUND_TASK
  }

  val workEvent =
    (if (case == Case.WORK) WorkManagerInspectorProtocol.Event.parseFrom(data)
    else WorkManagerInspectorProtocol.Event.getDefaultInstance())!!
  val backgroundTaskEvent =
    (if (case == Case.BACKGROUND_TASK) BackgroundTaskInspectorProtocol.Event.parseFrom(data)
    else BackgroundTaskInspectorProtocol.Event.getDefaultInstance())!!
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
  private val listeners = mutableListOf<EntryUpdateEventListener>()
  private val entryMap = mutableMapOf<String, BackgroundTaskEntry>()

  /**
   * Add a listener which is fired whenever an entry is added updated or removed.
   */
  fun addEntryUpdateEventListener(listener: EntryUpdateEventListener) = listeners.add(listener)

  private fun fireEntryUpdateEvent(type: EntryUpdateEventType, entry: BackgroundTaskEntry) {
    listeners.forEach { it(type, entry) }
  }

  init {
    val trackBackgroundTaskCommand = BackgroundTaskInspectorProtocol.Command.newBuilder()
      .setTrackBackgroundTask(BackgroundTaskInspectorProtocol.TrackBackgroundTaskCommand.getDefaultInstance())
      .build()
    scope.launch {
      btiMessenger.sendRawCommand(trackBackgroundTaskCommand.toByteArray())
      btiMessenger.eventFlow.collect { eventData ->
        handleEvent(EventWrapper(EventWrapper.Case.BACKGROUND_TASK, eventData))
      }
    }

    if (wmiMessengerTarget is WmiMessengerTarget.Resolved) {
      val trackWorkManagerCommand = WorkManagerInspectorProtocol.Command.newBuilder()
        .setTrackWorkManager(WorkManagerInspectorProtocol.TrackWorkManagerCommand.getDefaultInstance())
        .build()
      scope.launch {
        wmiMessengerTarget.messenger.sendRawCommand(trackWorkManagerCommand.toByteArray())
        wmiMessengerTarget.messenger.eventFlow.collect { eventData ->
          handleEvent(EventWrapper(EventWrapper.Case.WORK, eventData))
        }
      }
    }
  }

  private fun handleEvent(event: EventWrapper) {
    scope.launch(uiThread) {
      val candidate = createBackgroundTaskEntry(event)

      entryMap[candidate.id]?.let { oldEntry ->
        // Update or remove an existing entry.
        oldEntry.consume(event)
        if (oldEntry.isValid) {
          fireEntryUpdateEvent(EntryUpdateEventType.UPDATE, oldEntry)
        }
        else {
          entryMap.remove(candidate.id)?.let {
            fireEntryUpdateEvent(EntryUpdateEventType.REMOVE, it)
          }
        }
      } ?: candidate.let { newEntry ->
        // Insert a new entry.
        newEntry.consume(event)
        entryMap[newEntry.id] = newEntry
        fireEntryUpdateEvent(EntryUpdateEventType.ADD, newEntry)
      }
    }
  }

  fun getEntry(entryId: String): BackgroundTaskEntry? {
    return entryMap[entryId]
  }

  /**
   * Returns a chain of works with topological ordering containing the selected work.
   *
   * @param id id of the selected work.
   */
  fun getOrderedWorkChain(id: String): List<WorkManagerInspectorProtocol.WorkInfo> {
    val work = entryMap[id]?.getWorkInfo() ?: return listOf()
    val connectedWorks = mutableListOf(work)
    val visitedWorks = mutableSetOf(work)
    val orderedWorks = mutableListOf<WorkManagerInspectorProtocol.WorkInfo>()
    // Number of prerequisites not loaded into [orderedWorks].
    val degreeMap = mutableMapOf<WorkManagerInspectorProtocol.WorkInfo, Int>()
    var index = 0

    // Find works connected with the selected work and load works without prerequisites.
    while (index < connectedWorks.size) {
      val currentWork = connectedWorks[index]
      val previousWorks = currentWork.prerequisitesList.mapNotNull { entryMap[it]?.getWorkInfo() }
      val nextWorks = currentWork.dependentsList.mapNotNull { entryMap[it]?.getWorkInfo() }
      degreeMap[currentWork] = previousWorks.size
      if (previousWorks.isEmpty()) {
        orderedWorks.add(currentWork)
      }
      for (connectedWork in (previousWorks + nextWorks)) {
        if (!visitedWorks.contains(connectedWork)) {
          visitedWorks.add(connectedWork)
          connectedWorks.add(connectedWork)
        }
      }
      index += 1
    }
    // Load works with topological ordering.
    index = 0
    while (index < orderedWorks.size) {
      val currentWork = orderedWorks[index]
      val nextWorks = currentWork.dependentsList.mapNotNull { entryMap[it]?.getWorkInfo() }
      for (nextWork in nextWorks) {
        degreeMap[nextWork] = degreeMap[nextWork]!! - 1
        if (degreeMap[nextWork] == 0) {
          orderedWorks.add(nextWork)
        }
      }
      index += 1
    }
    return orderedWorks
  }

  private fun BackgroundTaskEntry.getWorkInfo() = (this as? WorkEntry)?.getWorkInfo()
}
