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
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.GrpcCallEnded
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.GrpcCallStarted
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.GrpcMessageReceived.*
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.GrpcMessageSent
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.GrpcMetadata
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.GrpcPayload
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.GrpcResponseHeaders
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.GrpcStreamCreated
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.Closed
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.Header
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.Payload
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.RequestCompleted
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.RequestStarted
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.ResponseCompleted
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.ResponseStarted
import studio.network.inspection.NetworkInspectorProtocol.SpeedEvent

@Suppress("SameParameterValue")
internal fun httpRequestStarted(
  id: Long,
  timestampNanos: Long,
  url: String = "",
  method: String = "",
  headers: List<Header> = emptyList(),
  trace: String = "",
) =
  httpConnectionEvent(id, timestampNanos) {
      setHttpRequestStarted(
        RequestStarted.newBuilder()
          .setUrl(url)
          .setMethod(method)
          .setTrace(trace)
          .addAllHeaders(headers)
      )
    }
    .build()

@Suppress("SameParameterValue")
internal fun httpRequestPayload(id: Long, timestampNanos: Long, payload: String) =
  httpConnectionEvent(id, timestampNanos) {
      setRequestPayload(Payload.newBuilder().setPayload(ByteString.copyFromUtf8(payload)))
    }
    .build()

@Suppress("SameParameterValue")
internal fun httpRequestCompleted(id: Long, timestampNanos: Long) =
  httpConnectionEvent(id, timestampNanos) { setHttpRequestCompleted(RequestCompleted.newBuilder()) }
    .build()

@Suppress("SameParameterValue")
internal fun httpResponseStarted(
  id: Long,
  timestampNanos: Long,
  responseCode: Int,
  headers: List<Header>,
) =
  httpConnectionEvent(id, timestampNanos) {
      setHttpResponseStarted(
        ResponseStarted.newBuilder().setResponseCode(responseCode).addAllHeaders(headers)
      )
    }
    .build()

@Suppress("SameParameterValue")
internal fun httpResponsePayload(id: Long, timestampNanos: Long, payload: String) =
  httpConnectionEvent(id, timestampNanos) {
      setResponsePayload(Payload.newBuilder().setPayload(ByteString.copyFromUtf8(payload)))
    }
    .build()

@Suppress("SameParameterValue")
internal fun httpResponseCompleted(id: Long, timestampNanos: Long) =
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

internal fun grpcCallStarted(
  id: Long,
  timestampNanos: Long,
  service: String = "",
  method: String = "",
  headers: List<GrpcMetadata> = emptyList(),
  trace: String = "",
) =
  grpcEvent(id, timestampNanos) {
      setGrpcCallStarted(
        GrpcCallStarted.newBuilder()
          .setService(service)
          .setMethod(method)
          .addAllRequestHeaders(headers)
          .setTrace(trace)
      )
    }
    .build()

internal fun grpcMessageSent(
  id: Long,
  timestampNanos: Long,
  bytes: ByteString,
  type: String,
  text: String,
) =
  grpcEvent(id, timestampNanos) {
      setGrpcMessageSent(
        GrpcMessageSent.newBuilder()
          .setPayload(GrpcPayload.newBuilder().setBytes(bytes).setType(type).setText(text))
      )
    }
    .build()

internal fun grpcStreamCreated(
  id: Long,
  timestampNanos: Long,
  address: String,
  headers: List<GrpcMetadata>,
) =
  grpcEvent(id, timestampNanos) {
      setGrpcStreamCreated(
        GrpcStreamCreated.newBuilder().setAddress(address).addAllRequestHeaders(headers)
      )
    }
    .build()

internal fun grpcResponseHeaders(id: Long, timestampNanos: Long, headers: List<GrpcMetadata>) =
  grpcEvent(id, timestampNanos) {
      setGrpcResponseHeaders(GrpcResponseHeaders.newBuilder().addAllResponseHeaders(headers))
    }
    .build()

internal fun grpcMessageReceived(
  id: Long,
  timestampNanos: Long,
  bytes: ByteString,
  type: String,
  text: String,
) =
  grpcEvent(id, timestampNanos) {
      setGrpcMessageReceived(
        newBuilder()
          .setPayload(GrpcPayload.newBuilder().setBytes(bytes).setType(type).setText(text))
      )
    }
    .build()

internal fun grpcCallEnded(
  id: Long,
  timestampNanos: Long,
  status: String = "",
  error: String = "",
  trailers: List<GrpcMetadata> = emptyList(),
) =
  grpcEvent(id, timestampNanos) {
      setGrpcCallEnded(
        GrpcCallEnded.newBuilder().setStatus(status).setError(error).addAllTrailers(trailers)
      )
    }
    .build()

internal fun grpcThread(id: Long, timestampNanos: Long, threadId: Long, threadName: String) =
  grpcEvent(id, timestampNanos) {
      setGrpcThread(
        NetworkInspectorProtocol.ThreadData.newBuilder()
          .setThreadId(threadId)
          .setThreadName(threadName)
      )
    }
    .build()

private fun httpConnectionEvent(
  id: Long,
  timestampNanos: Long,
  block: HttpConnectionEvent.Builder.() -> HttpConnectionEvent.Builder,
) =
  NetworkInspectorProtocol.Event.newBuilder()
    .setTimestamp(timestampNanos)
    .setHttpConnectionEvent(HttpConnectionEvent.newBuilder().setConnectionId(id).block())

internal fun httpHeader(key: String, vararg values: String) =
  Header.newBuilder().setKey(key).addAllValues(values.asList()).build()

private fun grpcEvent(
  id: Long,
  timestampNanos: Long,
  block: GrpcEvent.Builder.() -> GrpcEvent.Builder,
) =
  NetworkInspectorProtocol.Event.newBuilder()
    .setTimestamp(timestampNanos)
    .setGrpcEvent(GrpcEvent.newBuilder().setConnectionId(id).block().build())

internal fun grpcMetadata(key: String, vararg values: String) =
  GrpcMetadata.newBuilder().setKey(key).addAllValues(values.asList()).build()
