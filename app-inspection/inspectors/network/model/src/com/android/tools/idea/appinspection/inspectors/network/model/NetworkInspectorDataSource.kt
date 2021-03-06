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
package com.android.tools.idea.appinspection.inspectors.network.model

import com.android.tools.adtui.model.Range
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import studio.network.inspection.NetworkInspectorProtocol.Event
import java.util.concurrent.TimeUnit


/**
 * Performs a binary search on the data using timestamp and returns the index at which a hypothetical
 * event with [timestamp] should be inserted. Or return the index of the event that matches [timestamp].
 */
private fun List<Event>.binarySearch(timestamp: Long): Int {
  return binarySearch(
    Event.newBuilder().setTimestamp(timestamp).build(),
    compareBy { it.timestamp }
  )
}

/**
 * The two functions below are only required when binary search finds an element matching either
 * the start or the end of the range. Binary search has the caveat that if the search target has
 * multiple entries - in our case multiple events with the same timestamp - it doesn't guarantee
 * which entry it will return. To amend that, we manually search to the left or right of the index
 * to see if we truly have the start or end index.
 */
private fun List<Event>.findEndIndex(startIndex: Int): Int {
  for (i in startIndex + 1 until size) {
    if (get(i).timestamp != get(startIndex).timestamp) {
      return i - 1
    }
  }
  return size - 1
}

private fun List<Event>.findStartIndex(startIndex: Int): Int {
  for (i in startIndex - 1 downTo 0) {
    if (get(i).timestamp != get(startIndex).timestamp) {
      return i + 1
    }
  }
  return 0
}

/**
 * Return all events that fall within [range] inclusive.
 *
 * This function is designed to be fast (logN) because it gets called frequently by the frontend.
 */
private fun searchRange(data: List<Event>, range: Range): List<Event> {
  val min = TimeUnit.MICROSECONDS.toNanos(range.min.toLong())
  val max = TimeUnit.MICROSECONDS.toNanos(range.max.toLong())

  // If the result of binary search is less than 0, the index of the start or end element is gotten by:
  // 1) solving for x in the formula (result = -x - 1)
  // 2) startIndex = x, endIndex = x - 1
  val startIndex = data.binarySearch(min).let { pos -> if (pos < 0) { -pos - 1 } else data.findStartIndex(pos) }
  val endIndex = data.binarySearch(max).let { pos -> if (pos < 0) { -pos - 2 } else data.findEndIndex(pos) }

  return data.slice(startIndex..endIndex)
}

/**
 * These objects are used to communicate with the actor. They specify the work the actor needs to perform.
 */
private sealed class Intention {
  class QueryForSpeedData(val range: Range, val deferred: CompletableDeferred<List<Event>>) : Intention()
  class QueryForHttpData(val range: Range, val deferred: CompletableDeferred<List<Event>>) : Intention()
  class InsertData(val event: Event) : Intention()
}

/**
 * An actor that is used to maintain synchronization of the data collected from network inspector against the
 * frequent updates and queried performed against it.
 *
 * It performs two types of work:
 *   1) collects events sent from the network inspector and accumulates them.
 *   2) performs queries from UI frontend on the collected data.
 */
private fun CoroutineScope.processEvents(commandChannel: ReceiveChannel<Intention>) = launch {
  val speedData = mutableListOf<Event>()
  val httpData = mutableListOf<Event>()

  for (command in commandChannel) {
    if (command is Intention.InsertData) {
      if (command.event.hasSpeedEvent()) {
        speedData.add(command.event)
      }
      else if (command.event.hasHttpConnectionEvent()) {
        httpData.add(command.event)
      }
    }
    else if (command is Intention.QueryForSpeedData) {
      command.deferred.complete(searchRange(speedData, command.range))
    }
    else if (command is Intention.QueryForHttpData) {
      command.deferred.complete(searchRange(httpData, command.range))
    }
  }
}

/**
 * The data backend of network inspector.
 *
 * It collects all of the events sent by the inspector and makes them available
 * for queries based on time ranges.
 */
interface NetworkInspectorDataSource {
  suspend fun queryForHttpData(range: Range): List<Event>
  suspend fun queryForSpeedData(range: Range): List<Event>
}

class NetworkInspectorDataSourceImpl(
  messenger: AppInspectorMessenger,
  scope: CoroutineScope
) : NetworkInspectorDataSource {
  private val channel = Channel<Intention>()

  init {
    scope.launch {
      supervisorScope {
        processEvents(channel)
        messenger.eventFlow.collect {
          val event = Event.parseFrom(it)
          channel.send(Intention.InsertData(event))
        }
      }
    }
  }

  override suspend fun queryForHttpData(range: Range): List<Event> {
    val deferred = CompletableDeferred<List<Event>>()
    channel.send(Intention.QueryForHttpData(range, deferred))
    return deferred.await()
  }

  override suspend fun queryForSpeedData(range: Range): List<Event> {
    val deferred = CompletableDeferred<List<Event>>()
    channel.send(Intention.QueryForSpeedData(range, deferred))
    return deferred.await()
  }
}