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
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.concurrency.createChildScope
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.CopyOnWriteArrayList
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
  fun queryForHttpData(range: Range): List<HttpData>

  fun queryForSpeedData(range: Range): List<Event>

  fun addOnExtendTimelineListener(listener: (Long) -> Unit)
}

class NetworkInspectorDataSourceImpl(
  private val messenger: AppInspectorMessenger,
  parentScope: CoroutineScope,
  private val usageTracker: NetworkInspectorTracker,
) : NetworkInspectorDataSource {
  val scope = parentScope.createChildScope()
  private val listeners = ContainerUtil.createLockFreeCopyOnWriteList<(Long) -> Unit>()
  private val speedData = CopyOnWriteArrayList<Event>()
  private val httpData = HttpDataCollector()

  init {
    start()
  }

  fun start() {
    scope.launch {
      messenger.eventFlow
        .map { Event.parseFrom(it) }
        .collect { event ->
          notifyTimelineExtended(event.timestamp)
          when {
            event.hasSpeedEvent() -> speedData.add(event)
            event.hasHttpConnectionEvent() -> handleHttpEvent(httpData, event)
          }
        }
    }
  }

  override fun addOnExtendTimelineListener(listener: (Long) -> Unit) {
    listeners.add(listener)
  }

  override fun queryForHttpData(range: Range): List<HttpData> = httpData.getDataForRange(range)

  override fun queryForSpeedData(range: Range): List<Event> = speedData.searchRange(range)

  private fun handleHttpEvent(httpData: HttpDataCollector, event: Event) {
    httpData.processEvent(event)
    val httpConnectionEvent = event.httpConnectionEvent
    if (httpConnectionEvent.hasHttpResponseIntercepted()) {
      val interception = httpConnectionEvent.httpResponseIntercepted
      usageTracker.trackResponseIntercepted(
        statusCode = interception.statusCode,
        headerAdded = interception.headerAdded,
        headerReplaced = interception.headerReplaced,
        bodyReplaced = interception.bodyReplaced,
        bodyModified = interception.bodyModified
      )
    }
  }

  private fun notifyTimelineExtended(timestampNs: Long) {
    listeners.forEach { listener -> listener(timestampNs) }
  }
}
