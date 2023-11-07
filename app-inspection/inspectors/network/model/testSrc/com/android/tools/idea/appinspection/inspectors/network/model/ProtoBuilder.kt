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

import com.android.tools.idea.protobuf.ByteString
import studio.network.inspection.NetworkInspectorProtocol
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.Closed
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.Payload
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.RequestCompleted
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.RequestStarted
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.ResponseCompleted
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.ResponseStarted
import studio.network.inspection.NetworkInspectorProtocol.SpeedEvent

@Suppress("SameParameterValue")
internal fun requestStarted(
  id: Long,
  timestampNanos: Long,
  url: String = "",
  method: String = "",
  fields: String = "",
  trace: String = "",
) =
  httpConnectionEvent(id, timestampNanos) {
      setHttpRequestStarted(
        RequestStarted.newBuilder().setUrl(url).setMethod(method).setTrace(trace).setFields(fields)
      )
    }
    .build()

@Suppress("SameParameterValue")
internal fun requestPayload(id: Long, timestampNanos: Long, payload: String) =
  httpConnectionEvent(id, timestampNanos) {
      setRequestPayload(Payload.newBuilder().setPayload(ByteString.copyFromUtf8(payload)))
    }
    .build()

@Suppress("SameParameterValue")
internal fun requestCompleted(id: Long, timestampNanos: Long) =
  httpConnectionEvent(id, timestampNanos) { setHttpRequestCompleted(RequestCompleted.newBuilder()) }
    .build()

@Suppress("SameParameterValue")
internal fun responseStarted(id: Long, timestampNanos: Long, fields: String) =
  httpConnectionEvent(id, timestampNanos) {
      setHttpResponseStarted(ResponseStarted.newBuilder().setFields(fields))
    }
    .build()

@Suppress("SameParameterValue")
internal fun responsePayload(id: Long, timestampNanos: Long, payload: String) =
  httpConnectionEvent(id, timestampNanos) {
      setResponsePayload(Payload.newBuilder().setPayload(ByteString.copyFromUtf8(payload)))
    }
    .build()

@Suppress("SameParameterValue")
internal fun responseCompleted(id: Long, timestampNanos: Long) =
  httpConnectionEvent(id, timestampNanos) {
      setHttpResponseCompleted(ResponseCompleted.newBuilder())
    }
    .build()

@Suppress("SameParameterValue")
internal fun httpClosed(id: Long, timestamp: Long, completed: Boolean) =
  httpConnectionEvent(id, timestamp) { setHttpClosed(Closed.newBuilder().setCompleted(completed)) }
    .build()

@Suppress("SameParameterValue")
internal fun httpThread(id: Long, timestampNanos: Long, threadId: Long, threadName: String) =
  httpConnectionEvent(id, timestampNanos) {
      setHttpThread(
        NetworkInspectorProtocol.ThreadData.newBuilder()
          .setThreadId(threadId)
          .setThreadName(threadName)
      )
    }
    .build()

internal fun speedEvent(timestampNanos: Long, rxSpeed: Long = 0, txSpeed: Long = 0) =
  NetworkInspectorProtocol.Event.newBuilder()
    .setTimestamp(timestampNanos)
    .setSpeedEvent(SpeedEvent.newBuilder().setRxSpeed(rxSpeed).setTxSpeed(txSpeed))
    .build()

private fun httpConnectionEvent(
  id: Long,
  timestampNanos: Long,
  block: HttpConnectionEvent.Builder.() -> HttpConnectionEvent.Builder
) =
  NetworkInspectorProtocol.Event.newBuilder()
    .setTimestamp(timestampNanos)
    .setHttpConnectionEvent(HttpConnectionEvent.newBuilder().setConnectionId(id).block())
