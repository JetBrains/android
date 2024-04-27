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
package com.android.tools.idea.appinspection.inspectors.network.model.connections

import com.android.tools.adtui.model.Range
import com.android.tools.idea.protobuf.ByteString
import java.util.TreeMap
import java.util.concurrent.TimeUnit
import studio.network.inspection.NetworkInspectorProtocol
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.GrpcMetadata

/**
 * Data of gRPC connection. Each [GrpcData] object matches a gRPC connection with a unique id, and
 * it includes both request data and response data. Request data is filled immediately when the
 * connection starts. Response data may start empty but filled when connection completes.
 */
@Suppress("DataClassPrivateConstructor")
data class GrpcData
private constructor(
  override val id: Long,
  override val updateTimeUs: Long,
  override val requestStartTimeUs: Long,
  override val requestCompleteTimeUs: Long,
  override val responseStartTimeUs: Long,
  override val responseCompleteTimeUs: Long,
  override val connectionEndTimeUs: Long,
  override val threads: List<JavaThread>,
  override val address: String,
  val service: String,
  override val method: String,
  override val trace: String,
  override val requestHeaders: Map<String, List<String>>,
  override val requestPayload: ByteString,
  override val requestType: String,
  override val requestPayloadText: String,
  override val status: String,
  override val error: String,
  override val responseHeaders: Map<String, List<String>>,
  override val responsePayload: ByteString,
  override val responseType: String,
  override val responsePayloadText: String,
  override val responseTrailers: Map<String, List<String>>,
) : ConnectionData {
  override val transport: String
    get() = "gRPC"

  override val url: String
    get() = "$schema://$address/$path"

  override val schema: String
    get() = "grpc"

  override val path: String
    get() = "$service/$method"

  override val name: String
    get() = "$service/$method"

  internal fun withGrpcCallStarted(event: NetworkInspectorProtocol.Event): GrpcData {
    val timestamp = TimeUnit.NANOSECONDS.toMicros(event.timestamp)
    return copy(
      id = id,
      updateTimeUs = timestamp,
      requestStartTimeUs = timestamp,
      service = event.grpcEvent.grpcCallStarted.service,
      method = event.grpcEvent.grpcCallStarted.method,
      requestHeaders = requestHeaders + event.grpcEvent.grpcCallStarted.requestHeadersList.toMap(),
      trace = event.grpcEvent.grpcCallStarted.trace,
    )
  }

  internal fun withGrpcMessageSent(event: NetworkInspectorProtocol.Event): GrpcData {
    val timestamp = TimeUnit.NANOSECONDS.toMicros(event.timestamp)
    return copy(
      id = id,
      updateTimeUs = timestamp,
      requestCompleteTimeUs = timestamp,
      requestPayload = event.grpcEvent.grpcMessageSent.payload.bytes,
      requestType = event.grpcEvent.grpcMessageSent.payload.type,
      requestPayloadText = event.grpcEvent.grpcMessageSent.payload.text,
    )
  }

  internal fun withGrpcStreamCreated(event: NetworkInspectorProtocol.Event): GrpcData {
    val timestamp = TimeUnit.NANOSECONDS.toMicros(event.timestamp)
    return copy(
      id = id,
      updateTimeUs = timestamp,
      responseStartTimeUs = timestamp,
      address = event.grpcEvent.grpcStreamCreated.address,
      requestHeaders =
        requestHeaders + event.grpcEvent.grpcStreamCreated.requestHeadersList.toMap(),
    )
  }

  internal fun withGrpcMessageReceived(event: NetworkInspectorProtocol.Event): GrpcData {
    val timestamp = TimeUnit.NANOSECONDS.toMicros(event.timestamp)
    return copy(
      id = id,
      updateTimeUs = timestamp,
      responseCompleteTimeUs = timestamp,
      responsePayload = event.grpcEvent.grpcMessageReceived.payload.bytes,
      responseType = event.grpcEvent.grpcMessageReceived.payload.type,
      responsePayloadText = event.grpcEvent.grpcMessageReceived.payload.text,
    )
  }

  internal fun withGrpcResponseHeaders(event: NetworkInspectorProtocol.Event): GrpcData {
    val timestamp = TimeUnit.NANOSECONDS.toMicros(event.timestamp)
    return copy(
      id = id,
      updateTimeUs = timestamp,
      responseHeaders = event.grpcEvent.grpcResponseHeaders.responseHeadersList.toMap()
    )
  }

  internal fun withGrpcCallEnded(event: NetworkInspectorProtocol.Event): GrpcData {
    val timestamp = TimeUnit.NANOSECONDS.toMicros(event.timestamp)
    return copy(
      id = id,
      updateTimeUs = timestamp,
      connectionEndTimeUs = timestamp,
      status = event.grpcEvent.grpcCallEnded.status,
      error = event.grpcEvent.grpcCallEnded.error,
      responseTrailers = event.grpcEvent.grpcCallEnded.trailersList.toMap(),
    )
  }

  internal fun withGrpcThread(event: NetworkInspectorProtocol.Event): GrpcData {
    val timestamp = TimeUnit.NANOSECONDS.toMicros(event.timestamp)
    return copy(
      id = id,
      updateTimeUs = timestamp,
      threads = threads + event.toJavaThread(),
    )
  }

  internal fun intersectsRange(range: Range): Boolean {
    val start = requestStartTimeUs
    val end = updateTimeUs
    val min = range.min.toLong()
    val max = range.max.toLong()
    return start <= max && end >= min
  }

  companion object {
    fun createGrpcData(
      id: Long,
      updateTimeUs: Long = 0,
      requestStartTimeUs: Long = 0,
      requestCompleteTimeUs: Long = 0,
      responseStartTimeUs: Long = 0,
      responseCompleteTimeUs: Long = 0,
      connectionEndTimeUs: Long = 0,
      threads: List<JavaThread> = emptyList(),
      address: String = "",
      service: String = "",
      method: String = "",
      trace: String = "",
      requestHeaders: List<GrpcMetadata> = emptyList(),
      requestPayload: ByteString = ByteString.EMPTY,
      requestType: String = "",
      requestPayloadText: String = "",
      status: String = "",
      error: String = "",
      responseHeaders: List<GrpcMetadata> = emptyList(),
      responsePayload: ByteString = ByteString.EMPTY,
      responseType: String = "",
      responsePayloadText: String = "",
      responseTrailers: List<GrpcMetadata> = emptyList(),
    ): GrpcData =
      GrpcData(
        id,
        updateTimeUs,
        requestStartTimeUs,
        requestCompleteTimeUs,
        responseStartTimeUs,
        responseCompleteTimeUs,
        connectionEndTimeUs,
        threads,
        address,
        service,
        method,
        trace,
        requestHeaders.toMap(),
        requestPayload,
        requestType,
        requestPayloadText,
        status,
        error,
        responseHeaders.toMap(),
        responsePayload,
        responseType,
        responsePayloadText,
        responseTrailers.toMap(),
      )
  }
}

private fun List<GrpcMetadata>.toMap() =
  associateTo(TreeMap(String.CASE_INSENSITIVE_ORDER)) { it.key to it.valuesList }

private fun NetworkInspectorProtocol.Event.toJavaThread() =
  JavaThread(grpcEvent.grpcThread.threadId, grpcEvent.grpcThread.threadName)
