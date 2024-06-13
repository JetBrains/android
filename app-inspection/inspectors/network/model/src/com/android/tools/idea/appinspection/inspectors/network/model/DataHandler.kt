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
import com.android.tools.idea.appinspection.inspectors.network.model.DataHandler.Result
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.NetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.connections.GrpcData
import com.android.tools.idea.appinspection.inspectors.network.model.connections.HttpData
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit.NANOSECONDS
import kotlin.Boolean
import kotlin.Long
import kotlin.synchronized
import studio.network.inspection.NetworkInspectorProtocol.Event
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.UnionCase.GRPC_CALL_ENDED
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.UnionCase.GRPC_CALL_STARTED
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.UnionCase.GRPC_MESSAGE_RECEIVED
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.UnionCase.GRPC_MESSAGE_SENT
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.UnionCase.GRPC_RESPONSE_HEADERS
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.UnionCase.GRPC_STREAM_CREATED
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.UnionCase.GRPC_THREAD
import studio.network.inspection.NetworkInspectorProtocol.GrpcEvent.UnionCase.UNION_NOT_SET
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
import studio.network.inspection.NetworkInspectorProtocol.SpeedEvent

/**
 * Handles [HttpConnectionEvent]s and [SpeedEvent]s
 *
 * 'HttpConnectionEvent's are assembled into [HttpData] objects and 'SpeedEvent's are collected to a
 * list.
 *
 * The functions [handleSpeedEvent] and [handleHttpConnectionEvent] return a [Result] object
 * containing hints to the caller.
 */
internal class DataHandler(private val usageTracker: NetworkInspectorTracker) {
  private val logger = thisLogger()

  private val speedData = CopyOnWriteArrayList<Event>()
  @GuardedBy("itself") private val httpDataMap = Long2ObjectLinkedOpenHashMap<HttpData>()
  @GuardedBy("itself") private val grpcDataMap = Long2ObjectLinkedOpenHashMap<GrpcData>()

  /**
   * A collection of all the currently active connections. This is used to determine if a
   * [SpeedEvent] should extend the timeline or not. The reason this is needed is that we often get
   * out-of-band (OOB) speed events.
   *
   * An OOB speed event is a non-zero speed event that occurs outside a tracked connection. These
   * can be caused non-HTTP network traffic or HTTP traffic from an unsupported transport layer.
   * These events cause spikes in the timeline which do not have corresponding connection entries in
   * the ConnectionView. We should still probably display these in the timeline, but we don't want
   * to automatically extend the timeline when they arrive.
   *
   * The heuristics for updating the timeline due to a speed event is as follows: Once a connection
   * is started, all speed events extend the timeline until the first zero-event with a timestamp
   * later than (greater than) the connection end time.
   */
  private val activeConnections: MutableMap<Long, ActiveConnection> = Long2ObjectOpenHashMap()

  fun handleSpeedEvent(event: Event): Result {
    speedData.add(event)
    return Result(shouldUpdateTimeline(event))
  }

  fun handleHttpConnectionEvent(event: Event): Result {
    val httpConnectionEvent = event.httpConnectionEvent
    trackUsage(httpConnectionEvent)

    val id = httpConnectionEvent.connectionId
    val data =
      synchronized(httpDataMap) {
        httpDataMap.getOrPut(id) {
          activeConnections[id] = ActiveConnection(event.timestamp)
          logger.debug { "HTTP Connection added: id=$id time=${event.timestamp.nanosToSeconds()}" }
          HttpData.createHttpData(id)
        }
      }

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
          logger.warn("Unexpected event: ${httpConnectionEvent.unionCase}")
          return Result(updateTimeline = false)
        }
      }
    if (newData.connectionEndTimeUs > 0) {
      activeConnections.getValue(id).endNs = event.timestamp
      logger.debug { "Connection ended: id=$id time=${event.timestamp.nanosToSeconds()}" }
    }
    synchronized(httpDataMap) { httpDataMap[id] = newData }
    return Result(updateTimeline = true)
  }

  fun handleGrpcEvent(event: Event): Result {
    if (!StudioFlags.NETWORK_INSPECTOR_GRPC.get()) {
      return Result(updateTimeline = false)
    }

    val grpcEvent = event.grpcEvent

    // TODO(aalbert): Track gRPC data?

    val id = grpcEvent.connectionId
    val data =
      synchronized(grpcDataMap) {
        grpcDataMap.getOrPut(id) {
          activeConnections[id] = ActiveConnection(event.timestamp)
          logger.debug { "gRPC Connection added: id=$id time=${event.timestamp.nanosToSeconds()}" }
          GrpcData.createGrpcData(id)
        }
      }

    val newData =
      when (grpcEvent.unionCase) {
        GRPC_CALL_STARTED -> data.withGrpcCallStarted(event)
        GRPC_MESSAGE_SENT -> data.withGrpcMessageSent(event)
        GRPC_STREAM_CREATED -> data.withGrpcStreamCreated(event)
        GRPC_RESPONSE_HEADERS -> data.withGrpcResponseHeaders(event)
        GRPC_MESSAGE_RECEIVED -> data.withGrpcMessageReceived(event)
        GRPC_CALL_ENDED -> data.withGrpcCallEnded(event)
        GRPC_THREAD -> data.withGrpcThread(event)
        UNION_NOT_SET,
        null -> {
          logger.warn("Unexpected event: ${grpcEvent.unionCase}")
          return Result(updateTimeline = false)
        }
      }
    if (newData.connectionEndTimeUs > 0) {
      activeConnections.getValue(id).endNs = event.timestamp
      logger.debug { "Connection ended: id=$id time=${event.timestamp.nanosToSeconds()}" }
    }
    synchronized(grpcDataMap) { grpcDataMap[id] = newData }
    return Result(updateTimeline = true)
  }

  fun getSpeedForRange(range: Range) = speedData.searchRange(range)

  fun getHttpDataForRange(range: Range) =
    synchronized(httpDataMap) { httpDataMap.values.filter { it.intersectsRange(range) } }

  fun getGrpcDataForRange(range: Range) =
    synchronized(grpcDataMap) { grpcDataMap.values.filter { it.intersectsRange(range) } }

  fun reset() {
    speedData.clear()
    activeConnections.clear()
    synchronized(httpDataMap) { httpDataMap.clear() }
    synchronized(grpcDataMap) { grpcDataMap.clear() }
  }

  private fun shouldUpdateTimeline(event: Event): Boolean {
    val endedConnections = findEndedConnections(event)
    val isActive = hasActiveConnection(event)
    if (logger.isDebugEnabled) {
      logger.debug("SpeedEvent: time=${event.timestamp.nanosToSeconds()} isZero=${event.isZero()}")
      logger.debug("  Connections: $activeConnections")
      logger.debug("  isActive=$isActive")
      logger.debug("  Ended connections=$endedConnections")
    }
    val result = isActive || endedConnections.isNotEmpty()
    if (event.isZero()) {
      logger.debug { "  Removing connections: $endedConnections" }
      endedConnections.forEach { activeConnections.remove(it) }
    }
    logger.debug { "  shouldUpdateTimeline: $result" }
    return result
  }

  private fun trackUsage(event: HttpConnectionEvent) {
    if (event.hasHttpResponseIntercepted()) {
      val interception = event.httpResponseIntercepted
      usageTracker.trackResponseIntercepted(
        statusCode = interception.statusCode,
        headerAdded = interception.headerAdded,
        headerReplaced = interception.headerReplaced,
        bodyReplaced = interception.bodyReplaced,
        bodyModified = interception.bodyModified,
      )
    }
  }

  private fun findEndedConnections(event: Event) =
    activeConnections.filter { it.value.endsBefore(event.timestamp) }.keys

  private fun hasActiveConnection(event: Event) =
    activeConnections.values.find { it.contains(event.timestamp) } != null

  /**
   * Contains hints to the caller of [handleSpeedEvent] & [handleHttpConnectionEvent]
   *
   * @param updateTimeline true if the timeline should be updated after handling the event
   */
  class Result(val updateTimeline: Boolean)

  private data class ActiveConnection(val startNs: Long, var endNs: Long = Long.MAX_VALUE) {
    fun contains(timestampNs: Long) = timestampNs in startNs..endNs

    fun endsBefore(timestampNs: Long) = endNs < timestampNs

    override fun toString() =
      "ActiveConnection: ${startNs.nanosToSeconds()}-${if (endNs < Long.MAX_VALUE) endNs.nanosToSeconds() else "..."}"
  }
}

private fun Event.isZero() = speedEvent.rxSpeed == 0L && speedEvent.txSpeed == 0L

private fun Long.nanosToSeconds() = "%.03f".format(NANOSECONDS.toMillis(this).toFloat() / 1000)
