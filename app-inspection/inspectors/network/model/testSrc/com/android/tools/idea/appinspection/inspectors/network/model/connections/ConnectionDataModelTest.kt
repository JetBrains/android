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
package com.android.tools.idea.appinspection.inspectors.network.model.connections

import com.android.tools.adtui.model.Range
import com.android.tools.idea.appinspection.inspectors.network.model.FakeNetworkInspectorDataSource
import com.android.tools.idea.appinspection.inspectors.network.model.httpClosed
import com.android.tools.idea.appinspection.inspectors.network.model.httpRequestCompleted
import com.android.tools.idea.appinspection.inspectors.network.model.httpRequestPayload
import com.android.tools.idea.appinspection.inspectors.network.model.httpRequestStarted
import com.android.tools.idea.appinspection.inspectors.network.model.httpResponseCompleted
import com.android.tools.idea.appinspection.inspectors.network.model.httpResponsePayload
import com.android.tools.idea.appinspection.inspectors.network.model.httpResponseStarted
import com.android.tools.idea.appinspection.inspectors.network.model.httpThread
import com.android.tools.idea.protobuf.ByteString
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.Header

private const val CONNECTION_ID = 1L
private val fakeUrl = fakeUrl(CONNECTION_ID)
private val headers = emptyList<Header>()
private val faceTrace = fakeStackTrace(CONNECTION_ID)

private val HTTP_DATA =
  listOf(
    httpRequestStarted(CONNECTION_ID, SECONDS.toNanos(0), fakeUrl, method = "", headers, faceTrace),
    httpRequestPayload(CONNECTION_ID, SECONDS.toNanos(1), payload = "REQUEST_CONTENT"),
    httpRequestCompleted(CONNECTION_ID, SECONDS.toNanos(1)),
    httpResponseStarted(CONNECTION_ID, SECONDS.toNanos(2), 200, fakeResponseHeaders(CONNECTION_ID)),
    httpResponsePayload(CONNECTION_ID, SECONDS.toNanos(3), payload = "RESPONSE_CONTENT"),
    httpResponseCompleted(CONNECTION_ID, SECONDS.toNanos(3)),
    httpClosed(CONNECTION_ID, SECONDS.toNanos(3), completed = true),
  )

private val HTTP_DATA_WITH_THREAD =
  listOf(
    httpRequestStarted(CONNECTION_ID, SECONDS.toNanos(0), fakeUrl, method = "", headers, faceTrace),
    httpRequestPayload(CONNECTION_ID, SECONDS.toNanos(1), payload = "REQUEST_CONTENT"),
    httpRequestCompleted(CONNECTION_ID, SECONDS.toNanos(1)),
    httpResponseStarted(CONNECTION_ID, SECONDS.toNanos(2), 200, fakeResponseHeaders(CONNECTION_ID)),
    httpResponsePayload(CONNECTION_ID, SECONDS.toNanos(3), payload = "RESPONSE_CONTENT"),
    httpResponseCompleted(CONNECTION_ID, SECONDS.toNanos(3)),
    httpClosed(CONNECTION_ID, SECONDS.toNanos(3), completed = true),
    httpThread(CONNECTION_ID, SECONDS.toNanos(4), 1, "thread"),
  )

class ConnectionDataModelTest {

  @Test
  fun eventsToHttpData() {
    val source = FakeNetworkInspectorDataSource(httpEventList = HTTP_DATA_WITH_THREAD)
    val model = ConnectionDataModelImpl(source)
    val dataList = model.getData(Range(0.0, SECONDS.toMicros(5).toDouble()))
    assertThat(dataList).hasSize(1)
    val httpData = dataList[0] as HttpData

    assertThat(httpData.requestStartTimeUs).isEqualTo(0)
    assertThat(httpData.requestCompleteTimeUs).isEqualTo(1000000)
    assertThat(httpData.responseStartTimeUs).isEqualTo(2000000)
    assertThat(httpData.responseCompleteTimeUs).isEqualTo(3000000)
    assertThat(httpData.connectionEndTimeUs).isEqualTo(3000000)
    assertThat(httpData.method).isEmpty()
    assertThat(httpData.url).isEqualTo(fakeUrl)
    assertThat(httpData.trace).isEqualTo(faceTrace)
    assertThat(httpData.requestPayload).isEqualTo(ByteString.copyFromUtf8("REQUEST_CONTENT"))
    assertThat(httpData.getReadableResponsePayload())
      .isEqualTo(ByteString.copyFromUtf8("RESPONSE_CONTENT"))
    assertThat(httpData.responseHeaders["connId"]).containsExactly("1")
  }

  @Test
  fun eventsWithoutThreadDataIgnored() {
    val source = FakeNetworkInspectorDataSource(httpEventList = HTTP_DATA)
    val model = ConnectionDataModelImpl(source)
    val httpDataList = model.getData(Range(0.0, SECONDS.toMicros(5).toDouble()))
    assertThat(httpDataList).isEmpty()
  }
}
