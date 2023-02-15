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
import com.android.tools.idea.appinspection.inspectors.network.model.FakeNetworkInspectorDataSource
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.StubNetworkInspectorTracker
import com.android.tools.idea.protobuf.ByteString
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol.Event
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent

private const val CONNECTION_ID = 1L

private val HTTP_DATA =
  listOf(
    Event.newBuilder()
      .apply {
        timestamp = TimeUnit.SECONDS.toNanos(0)
        httpConnectionEvent =
          HttpConnectionEvent.newBuilder()
            .apply {
              connectionId = CONNECTION_ID
              httpRequestStarted =
                HttpConnectionEvent.RequestStarted.newBuilder()
                  .apply {
                    url = fakeUrl(CONNECTION_ID)
                    method = ""
                    trace = fakeStackTrace(1)
                    fields = ""
                  }
                  .build()
            }
            .build()
      }
      .build(),
    Event.newBuilder()
      .apply {
        timestamp = TimeUnit.SECONDS.toNanos(1)
        httpConnectionEvent =
          HttpConnectionEvent.newBuilder()
            .apply {
              connectionId = CONNECTION_ID
              requestPayload =
                HttpConnectionEvent.Payload.newBuilder()
                  .apply { payload = ByteString.copyFromUtf8("REQUEST_CONTENT") }
                  .build()
            }
            .build()
      }
      .build(),
    Event.newBuilder()
      .apply {
        timestamp = TimeUnit.SECONDS.toNanos(1)
        httpConnectionEvent =
          HttpConnectionEvent.newBuilder()
            .apply {
              connectionId = CONNECTION_ID
              httpRequestCompleted = HttpConnectionEvent.RequestCompleted.getDefaultInstance()
            }
            .build()
      }
      .build(),
    Event.newBuilder()
      .apply {
        timestamp = TimeUnit.SECONDS.toNanos(2)
        httpConnectionEvent =
          HttpConnectionEvent.newBuilder()
            .apply {
              connectionId = CONNECTION_ID
              httpResponseStarted =
                HttpConnectionEvent.ResponseStarted.newBuilder()
                  .apply { fields = fakeResponseFields(CONNECTION_ID) }
                  .build()
            }
            .build()
      }
      .build(),
    Event.newBuilder()
      .apply {
        timestamp = TimeUnit.SECONDS.toNanos(3)
        httpConnectionEvent =
          HttpConnectionEvent.newBuilder()
            .apply {
              connectionId = CONNECTION_ID
              responsePayload =
                HttpConnectionEvent.Payload.newBuilder()
                  .apply { payload = ByteString.copyFromUtf8("RESPONSE_CONTENT") }
                  .build()
            }
            .build()
      }
      .build(),
    Event.newBuilder()
      .apply {
        timestamp = TimeUnit.SECONDS.toNanos(3)
        httpConnectionEvent =
          HttpConnectionEvent.newBuilder()
            .apply {
              connectionId = CONNECTION_ID
              httpResponseCompleted = HttpConnectionEvent.ResponseCompleted.getDefaultInstance()
            }
            .build()
      }
      .build(),
    Event.newBuilder()
      .apply {
        timestamp = TimeUnit.SECONDS.toNanos(3)
        httpConnectionEvent =
          HttpConnectionEvent.newBuilder()
            .apply {
              connectionId = CONNECTION_ID
              httpClosed =
                HttpConnectionEvent.Closed.newBuilder().apply { completed = true }.build()
            }
            .build()
      }
      .build()
  )

private val HTTP_DATA_WITH_THREAD =
  HTTP_DATA +
    listOf(
      Event.newBuilder()
        .setTimestamp(TimeUnit.SECONDS.toNanos(4))
        .setHttpConnectionEvent(
          HttpConnectionEvent.newBuilder()
            .setConnectionId(CONNECTION_ID)
            .setHttpThread(
              HttpConnectionEvent.ThreadData.newBuilder().setThreadId(1).setThreadName("thread")
            )
        )
        .build()
    )

class HttpDataModelTest {

  @Test
  fun eventsToHttpData() {
    val source = FakeNetworkInspectorDataSource(httpEventList = HTTP_DATA_WITH_THREAD)
    val scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
    val model = HttpDataModelImpl(source, StubNetworkInspectorTracker(), scope)
    val httpDataList = model.getData(Range(0.0, TimeUnit.SECONDS.toMicros(5).toDouble()))
    assertThat(httpDataList).hasSize(1)
    val httpData = httpDataList[0]

    assertThat(httpData.requestStartTimeUs).isEqualTo(0)
    assertThat(httpData.requestCompleteTimeUs).isEqualTo(1000000)
    assertThat(httpData.responseStartTimeUs).isEqualTo(2000000)
    assertThat(httpData.responseCompleteTimeUs).isEqualTo(3000000)
    assertThat(httpData.connectionEndTimeUs).isEqualTo(3000000)
    assertThat(httpData.method).isEmpty()
    assertThat(httpData.url).isEqualTo(fakeUrl(CONNECTION_ID))
    assertThat(httpData.trace).isEqualTo(fakeStackTrace(CONNECTION_ID))
    assertThat(httpData.requestPayload).isEqualTo(ByteString.copyFromUtf8("REQUEST_CONTENT"))
    assertThat(httpData.responsePayload).isEqualTo(ByteString.copyFromUtf8("RESPONSE_CONTENT"))
    assertThat(httpData.responseHeader.getField("connId")).isEqualTo("1")
  }

  @Test
  fun eventsWithoutThreadDataIgnored() {
    val source = FakeNetworkInspectorDataSource(httpEventList = HTTP_DATA)
    val scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
    val model = HttpDataModelImpl(source, StubNetworkInspectorTracker(), scope)
    val httpDataList = model.getData(Range(0.0, TimeUnit.SECONDS.toMicros(5).toDouble()))
    assertThat(httpDataList).isEmpty()
  }
}
