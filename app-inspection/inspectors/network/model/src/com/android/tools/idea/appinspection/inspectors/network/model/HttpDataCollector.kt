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
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.JavaThread
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import java.util.concurrent.TimeUnit.NANOSECONDS
import studio.network.inspection.NetworkInspectorProtocol.Event
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
internal class HttpDataCollector {
  @GuardedBy("itself") private val httpDataMap = Long2ObjectLinkedOpenHashMap<HttpData>()

  fun processEvent(event: Event) {
    val httpConnectionEvent = event.httpConnectionEvent
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

  fun getDataForRange(range: Range) =
    synchronized(httpDataMap) { httpDataMap.values.filter { it.intersectsRange(range) } }
      .sortedBy { it.requestStartTimeUs }
}

private fun HttpData.withRequestStarted(event: Event): HttpData {
  val timestamp = NANOSECONDS.toMicros(event.timestamp)
  return copy(
    updateTimeUs = timestamp,
    requestStartTimeUs = timestamp,
    url = event.httpConnectionEvent.httpRequestStarted.url,
    method = event.httpConnectionEvent.httpRequestStarted.method,
    trace = event.httpConnectionEvent.httpRequestStarted.trace,
    requestFields = event.httpConnectionEvent.httpRequestStarted.fields,
  )
}

private fun HttpData.withHttpThread(event: Event) =
  copy(
    updateTimeUs = NANOSECONDS.toMicros(event.timestamp),
    threads = threads + event.toJavaThread(),
  )

private fun HttpData.withRequestPayload(event: Event) =
  copy(
    updateTimeUs = NANOSECONDS.toMicros(event.timestamp),
    requestPayload = event.httpConnectionEvent.requestPayload.payload,
  )

private fun HttpData.withRequestCompleted(event: Event): HttpData {
  val timestamp = NANOSECONDS.toMicros(event.timestamp)
  return copy(
    updateTimeUs = timestamp,
    requestCompleteTimeUs = timestamp,
  )
}

private fun HttpData.withResponseStarted(event: Event): HttpData {
  val timestamp = NANOSECONDS.toMicros(event.timestamp)
  return copy(
    updateTimeUs = timestamp,
    responseStartTimeUs = timestamp,
    responseFields = event.httpConnectionEvent.httpResponseStarted.fields,
  )
}

private fun HttpData.withResponsePayload(event: Event) =
  copy(
    updateTimeUs = NANOSECONDS.toMicros(event.timestamp),
    rawResponsePayload = event.httpConnectionEvent.responsePayload.payload,
  )

private fun HttpData.withResponseCompleted(event: Event): HttpData {
  val timestamp = NANOSECONDS.toMicros(event.timestamp)
  return copy(
    updateTimeUs = timestamp,
    responseCompleteTimeUs = timestamp,
  )
}

private fun HttpData.withHttpClosed(event: Event): HttpData {
  val timestamp = NANOSECONDS.toMicros(event.timestamp)
  return copy(
    updateTimeUs = timestamp,
    connectionEndTimeUs = timestamp,
  )
}

private fun HttpData.intersectsRange(range: Range): Boolean {
  val start = requestStartTimeUs
  val end = updateTimeUs
  val min = range.min.toLong()
  val max = range.max.toLong()
  return start <= max && end >= min
}

private fun Event.toJavaThread() =
  JavaThread(httpConnectionEvent.httpThread.threadId, httpConnectionEvent.httpThread.threadName)
