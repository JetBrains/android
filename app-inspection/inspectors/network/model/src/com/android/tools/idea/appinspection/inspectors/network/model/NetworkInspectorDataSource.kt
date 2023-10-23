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
import com.android.tools.idea.appinspection.inspectors.network.model.Intention.InsertData
import com.android.tools.idea.appinspection.inspectors.network.model.Intention.QueryForHttpData
import com.android.tools.idea.appinspection.inspectors.network.model.Intention.QueryForSpeedData
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.NetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.concurrency.createChildScope
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.network.inspection.NetworkInspectorProtocol.Event

/**
 * The data backend of network inspector.
 *
 * It collects all the events sent by the inspector and makes them available for queries based on
 * time ranges.
 */
interface NetworkInspectorDataSource {
  suspend fun queryForHttpData(range: Range): List<HttpData>

  suspend fun queryForSpeedData(range: Range): List<Event>

  fun addOnExtendTimelineListener(listener: (Long) -> Unit)
}

class NetworkInspectorDataSourceImpl(
  messenger: AppInspectorMessenger,
  parentScope: CoroutineScope,
  private val usageTracker: NetworkInspectorTracker,
) : NetworkInspectorDataSource {
  val scope = parentScope.createChildScope()
  private val channel = Channel<Intention>()
  private val listeners = ContainerUtil.createLockFreeCopyOnWriteList<(Long) -> Unit>()

  init {
    scope.launch {
      messenger.eventFlow.map { InsertData(Event.parseFrom(it)) }.collect { channel.send(it) }
    }
    scope.launch { processEvents() }
  }

  override fun addOnExtendTimelineListener(listener: (Long) -> Unit) {
    listeners.add(listener)
  }

  override suspend fun queryForHttpData(range: Range) =
    withContext(scope.coroutineContext) {
      val deferred = CompletableDeferred<List<HttpData>>()
      channel.send(QueryForHttpData(range, deferred))
      deferred.await()
    }

  override suspend fun queryForSpeedData(range: Range) =
    withContext(scope.coroutineContext) {
      val deferred = CompletableDeferred<List<Event>>()
      channel.send(QueryForSpeedData(range, deferred))
      deferred.await()
    }

  /**
   * An actor that is used to maintain synchronization of the data collected from network inspector
   * against the frequent updates and queried performed against it.
   *
   * It performs two types of work:
   * 1. Collects events sent from the network inspector and accumulates them.
   * 2. Performs queries from UI frontend on the collected data.
   */
  private suspend fun processEvents() {
    val speedData = mutableListOf<Event>()
    val httpData = HttpDataCollector()

    channel.consumeEach { command ->
      when (command) {
        is QueryForSpeedData -> command.deferred.complete(speedData.searchRange(command.range))
        is QueryForHttpData -> command.deferred.complete(httpData.getDataForRange(command.range))
        is InsertData -> {
          val event = command.event
          notifyTimelineExtended(event.timestamp)
          when {
            event.hasSpeedEvent() -> speedData.add(event)
            event.hasHttpConnectionEvent() -> handleHttpEvent(httpData, event)
          }
        }
      }
    }
  }

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

/**
 * These objects are used to communicate with the actor. They specify the work the actor needs to
 * perform.
 */
private sealed class Intention {
  class QueryForSpeedData(val range: Range, val deferred: CompletableDeferred<List<Event>>) :
    Intention()

  class QueryForHttpData(val range: Range, val deferred: CompletableDeferred<List<HttpData>>) :
    Intention()

  class InsertData(val event: Event) : Intention()
}
