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
package com.android.tools.idea.appinspection.inspectors.network.model

import com.android.tools.adtui.model.Range
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.NetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.connections.ConnectionData
import com.android.tools.idea.concurrency.createChildScope
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import studio.network.inspection.NetworkInspectorProtocol.Event

/**
 * The data backend of network inspector.
 *
 * It collects all the events sent by the inspector and makes them available for queries based on
 * time ranges.
 */
interface NetworkInspectorDataSource {
  fun queryForConnectionData(range: Range): List<ConnectionData>

  fun queryForSpeedData(range: Range): List<Event>

  fun addOnExtendTimelineListener(listener: (Long) -> Unit)

  fun start()

  fun reset()
}

class NetworkInspectorDataSourceImpl(
  private val messenger: AppInspectorMessenger,
  parentScope: CoroutineScope,
  usageTracker: NetworkInspectorTracker,
) : NetworkInspectorDataSource {
  val scope = parentScope.createChildScope()
  private val listeners = ContainerUtil.createLockFreeCopyOnWriteList<(Long) -> Unit>()
  private val dataHandler = DataHandler(usageTracker)

  @Volatile private var isStarted = false

  override fun start() {
    if (isStarted) {
      return
    }
    synchronized(this) {
      if (isStarted) {
        return
      }
      isStarted = true
    }
    scope.launch {
      messenger.eventFlow
        .map { Event.parseFrom(it) }
        .collect { event ->
          val result =
            when {
              event.hasSpeedEvent() -> dataHandler.handleSpeedEvent(event)
              event.hasHttpConnectionEvent() -> dataHandler.handleHttpConnectionEvent(event)
              event.hasGrpcEvent() -> dataHandler.handleGrpcEvent(event)
              else -> null
            }
          if (result?.updateTimeline == true) {
            notifyTimelineExtended(event.timestamp)
          }
        }
    }
  }

  override fun addOnExtendTimelineListener(listener: (Long) -> Unit) {
    listeners.add(listener)
  }

  override fun queryForConnectionData(range: Range): List<ConnectionData> =
    (dataHandler.getHttpDataForRange(range) + dataHandler.getGrpcDataForRange(range)).sortedBy {
      it.requestStartTimeUs
    }

  override fun queryForSpeedData(range: Range): List<Event> = dataHandler.getSpeedForRange(range)

  override fun reset() {
    dataHandler.reset()
  }

  private fun notifyTimelineExtended(timestampNs: Long) {
    listeners.forEach { listener -> listener(timestampNs) }
  }
}
