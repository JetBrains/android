/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.adtui.model.Range
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.NetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import java.util.concurrent.CopyOnWriteArrayList
import studio.network.inspection.NetworkInspectorProtocol.Event
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_CLOSED
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_REQUEST_COMPLETED
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_REQUEST_STARTED
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_RESPONSE_COMPLETED
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_RESPONSE_INTERCEPTED
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_RESPONSE_STARTED
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_THREAD
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.REQUEST_PAYLOAD
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD

/** Creates and collects [HttpData] from incoming `HttpConnectionEvent`s */
internal class DataHandler(private val usageTracker: NetworkInspectorTracker) {
  private val speedData = CopyOnWriteArrayList<Event>()
  @GuardedBy("itself") private val httpDataMap = Long2ObjectLinkedOpenHashMap<HttpData>()

  fun handleSpeedEvent(event: Event) {
    speedData.add(event)
  }

  fun handleHttpConnectionEvent(event: Event) {
    val httpConnectionEvent = event.httpConnectionEvent
    trackUsage(httpConnectionEvent)

    val id = httpConnectionEvent.connectionId
    val data =
      synchronized(httpDataMap) { httpDataMap.getOrPut(id) { HttpData.createHttpData(id) } }

    val newData =
      when (httpConnectionEvent.unionCase) {
        HTTP_REQUEST_STARTED -> data.withRequestStarted(event)
        HTTP_REQUEST_COMPLETED -> data.withRequestCompleted(event)
        HTTP_RESPONSE_STARTED -> data.withResponseStarted(event)
        HTTP_RESPONSE_INTERCEPTED -> data
        HTTP_RESPONSE_COMPLETED -> data.withResponseCompleted(event)
        HTTP_CLOSED -> data.withHttpClosed(event)
        REQUEST_PAYLOAD -> data.withRequestPayload(event)
        RESPONSE_PAYLOAD -> data.withResponsePayload(event)
        HTTP_THREAD -> data.withHttpThread(event)
        else -> {
          thisLogger().warn("Unexpected event: ${httpConnectionEvent.unionCase}")
          return
        }
      }
    synchronized(httpDataMap) {
      httpDataMap[id] = newData
      thisLogger().debug {
        "Connection $id: ${httpConnectionEvent.unionCase}. total: ${httpDataMap.count()}"
      }
    }
  }

  fun getSpeedForRange(range: Range) = speedData.searchRange(range)

  fun getHttpDataForRange(range: Range) =
    synchronized(httpDataMap) { httpDataMap.values.filter { it.intersectsRange(range) } }
      .sortedBy { it.requestStartTimeUs }

  private fun trackUsage(event: HttpConnectionEvent) {
    if (event.hasHttpResponseIntercepted()) {
      val interception = event.httpResponseIntercepted
      usageTracker.trackResponseIntercepted(
        statusCode = interception.statusCode,
        headerAdded = interception.headerAdded,
        headerReplaced = interception.headerReplaced,
        bodyReplaced = interception.bodyReplaced,
        bodyModified = interception.bodyModified
      )
    }
  }
}
