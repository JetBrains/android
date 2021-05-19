/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.transport.manager

import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.TransportServiceGrpc
import kotlin.math.max

/**
 * Represents a single unit of polling work. Can be added to [TransportPoller] for periodic execution.
 */
interface PollingTask {
  fun poll(client: TransportServiceGrpc.TransportServiceBlockingStub): Boolean
}

/**
 * Defines a unit of polling work for a stream. The query of the work is configurable via [TransportStreamEventListener].
 */
internal class StreamEventPollingTask(private val streamId: Long, internal val listener: TransportStreamEventListener) :
  PollingTask {
  private var lastTimeStamp = listener.streamEventQuery.startTime?.invoke() ?: Long.MIN_VALUE

  override fun poll(client: TransportServiceGrpc.TransportServiceBlockingStub): Boolean {
    val startTimestamp = lastTimeStamp
    val query = listener.streamEventQuery
    val endTimestamp = query.endTime.invoke()

    val builder = Transport.GetEventGroupsRequest.newBuilder()
      .setStreamId(streamId)
      .setKind(query.eventKind)
      .setFromTimestamp(startTimestamp)
      .setToTimestamp(endTimestamp)
    query.processId?.invoke()?.let { builder.pid = it }
    query.groupId?.invoke()?.let { builder.groupId = it }

    val request = builder.build()

    // Order by timestamp
    val response = client.getEventGroups(request)
    if (response != Transport.GetEventGroupsResponse.getDefaultInstance()) {
      val filtered = response.groupsList
        .flatMap { group -> group.eventsList }
        .sortedWith(query.sortOrder)
        .filter { event -> event.timestamp >= startTimestamp && query.filter(event) }
      filtered.forEach { event -> listener.executor.execute { listener.callback(event) } }
      val maxTimeEvent = filtered.maxBy { it.timestamp }
      maxTimeEvent?.let { lastTimeStamp = max(startTimestamp, it.timestamp + 1) }
      if (filtered.isNotEmpty() && listener.isTransient) {
        return true
      }
    }
    return false
  }
}