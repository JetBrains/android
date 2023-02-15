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
package com.android.tools.idea.appinspection.inspectors.network.model.httpdata

import com.android.tools.adtui.model.Range
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorDataSource
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.NetworkInspectorTracker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import studio.network.inspection.NetworkInspectorProtocol.Event
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent

/** A model that allows for the querying of [HttpData] based on time range. */
interface HttpDataModel {
  /**
   * This method will be invoked in each animation cycle of the timeline view.
   *
   * Returns a list of [HttpData] that fall within the [range].
   */
  fun getData(timeCurrentRangeUs: Range): List<HttpData>
}

class HttpDataModelImpl(
  private val dataSource: NetworkInspectorDataSource,
  private val usageTracker: NetworkInspectorTracker,
  scope: CoroutineScope,
) : HttpDataModel {

  init {
    scope.launch {
      dataSource.connectionEventFlow.collect { event ->
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
  }

  override fun getData(timeCurrentRangeUs: Range) = runBlocking {
    dataSource
      .queryForHttpData(timeCurrentRangeUs)
      .groupBy { httpEvent -> httpEvent.httpConnectionEvent.connectionId }
      .values
      .filter { events ->
        events.first().httpConnectionEvent.hasHttpRequestStarted() &&
          events.find { it.httpConnectionEvent.hasHttpThread() } != null
      }
      .mapNotNull { eventGroup ->
        val eventByType = eventGroup.groupBy { it.httpConnectionEvent.unionCase }
        val requestStartEvent =
          eventByType[HttpConnectionEvent.UnionCase.HTTP_REQUEST_STARTED]?.first()
            ?: return@mapNotNull null
        val threadData =
          eventByType[HttpConnectionEvent.UnionCase.HTTP_THREAD] ?: return@mapNotNull null
        if (threadData.isEmpty()) return@mapNotNull null

        val requestCompleteEvent =
          eventByType[HttpConnectionEvent.UnionCase.HTTP_REQUEST_COMPLETED]?.first()
            ?: Event.getDefaultInstance()
        val responseStartEvent =
          eventByType[HttpConnectionEvent.UnionCase.HTTP_RESPONSE_STARTED]?.first()
            ?: Event.getDefaultInstance()
        val responseCompleteEvent =
          eventByType[HttpConnectionEvent.UnionCase.HTTP_RESPONSE_COMPLETED]?.first()
            ?: Event.getDefaultInstance()
        val httpCloseEvent =
          eventByType[HttpConnectionEvent.UnionCase.HTTP_CLOSED]?.first()
            ?: Event.getDefaultInstance()
        val requestPayloadEvent =
          eventByType[HttpConnectionEvent.UnionCase.REQUEST_PAYLOAD]?.first()
            ?: Event.getDefaultInstance()
        val responsePayloadEvent =
          eventByType[HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD]?.first()
            ?: Event.getDefaultInstance()

        val requestStartTimeUs = TimeUnit.NANOSECONDS.toMicros(requestStartEvent.timestamp)
        val requestCompleteTimeUs = TimeUnit.NANOSECONDS.toMicros(requestCompleteEvent.timestamp)
        val respondStartTimeUs = TimeUnit.NANOSECONDS.toMicros(responseStartEvent.timestamp)
        val respondCompleteTimeUs = TimeUnit.NANOSECONDS.toMicros(responseCompleteEvent.timestamp)
        val connectionEndTimeUs = TimeUnit.NANOSECONDS.toMicros(httpCloseEvent.timestamp)
        val threads =
          threadData.map {
            JavaThread(
              it.httpConnectionEvent.httpThread.threadId,
              it.httpConnectionEvent.httpThread.threadName
            )
          }
        val requestStartData = requestStartEvent.httpConnectionEvent.httpRequestStarted
        HttpData.createHttpData(
          requestStartEvent.httpConnectionEvent.connectionId,
          requestStartTimeUs,
          requestCompleteTimeUs,
          respondStartTimeUs,
          respondCompleteTimeUs,
          connectionEndTimeUs,
          threads,
          requestStartData.url,
          requestStartData.method,
          requestStartData.trace,
          requestStartData.fields,
          requestPayloadEvent.httpConnectionEvent.requestPayload.payload,
          responseStartEvent.httpConnectionEvent.httpResponseStarted.fields,
          responsePayloadEvent.httpConnectionEvent.responsePayload.payload
        )
      }
  }
}
