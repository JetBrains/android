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
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executors
import kotlin.test.fail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol.Event
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent
import studio.network.inspection.NetworkInspectorProtocol.SpeedEvent

class NetworkInspectorDataSourceTest {
  private val executor = Executors.newSingleThreadExecutor()
  private val scope = CoroutineScope(executor.asCoroutineDispatcher() + SupervisorJob())

  @Test
  fun basicSearch() = runBlocking {
    val speedEvent =
      Event.newBuilder()
        .setTimestamp(1000)
        .setSpeedEvent(SpeedEvent.newBuilder().setRxSpeed(10).setTxSpeed(20))
        .build()
    val httpEvent =
      Event.newBuilder()
        .setTimestamp(1002)
        .setHttpConnectionEvent(
          HttpConnectionEvent.newBuilder()
            .setConnectionId(1)
            .setHttpRequestStarted(
              HttpConnectionEvent.RequestStarted.newBuilder()
                .setFields("a")
                .setMethod("http")
                .setTrace("abc")
                .setUrl("www.google.com")
            )
        )
        .build()
    val testMessenger =
      TestMessenger(scope, flowOf(speedEvent.toByteArray(), httpEvent.toByteArray()))
    val dataSource = NetworkInspectorDataSourceImpl(testMessenger, scope)
    testMessenger.await()

    val speedEvents = dataSource.queryForSpeedData(Range(1.0, 2.0))
    assertThat(speedEvents).hasSize(1)
    assertThat(speedEvents[0]).isEqualTo(speedEvent)

    val httpEvents = dataSource.queryForHttpData(Range(1.0, 2.0))
    assertThat(httpEvents).hasSize(1)
    assertThat(httpEvents[0]).isEqualTo(httpEvent)
  }

  @Test
  fun advancedSearch(): Unit = runBlocking {
    val speedEvent1 =
      Event.newBuilder().setTimestamp(1000).setSpeedEvent(SpeedEvent.getDefaultInstance()).build()
    val speedEvent2 =
      Event.newBuilder().setTimestamp(2000).setSpeedEvent(SpeedEvent.getDefaultInstance()).build()
    val speedEvent3 =
      Event.newBuilder().setTimestamp(2000).setSpeedEvent(SpeedEvent.getDefaultInstance()).build()
    val speedEvent4 =
      Event.newBuilder().setTimestamp(3000).setSpeedEvent(SpeedEvent.getDefaultInstance()).build()
    val speedEvent5 =
      Event.newBuilder().setTimestamp(3000).setSpeedEvent(SpeedEvent.getDefaultInstance()).build()
    val speedEvent6 =
      Event.newBuilder().setTimestamp(3000).setSpeedEvent(SpeedEvent.getDefaultInstance()).build()
    val speedEvent7 =
      Event.newBuilder().setTimestamp(3001).setSpeedEvent(SpeedEvent.getDefaultInstance()).build()
    val speedEvent8 =
      Event.newBuilder().setTimestamp(6000).setSpeedEvent(SpeedEvent.getDefaultInstance()).build()

    val testMessenger =
      TestMessenger(
        scope,
        flowOf(
          speedEvent1.toByteArray(),
          speedEvent2.toByteArray(),
          speedEvent3.toByteArray(),
          speedEvent4.toByteArray(),
          speedEvent5.toByteArray(),
          speedEvent6.toByteArray(),
          speedEvent7.toByteArray(),
          speedEvent8.toByteArray()
        )
      )
    val dataSource = NetworkInspectorDataSourceImpl(testMessenger, scope)
    testMessenger.await()

    // basic inclusive search
    run {
      val speedEvents = dataSource.queryForSpeedData(Range(0.0, 10.0))
      assertThat(speedEvents)
        .containsExactly(
          speedEvent1,
          speedEvent2,
          speedEvent3,
          speedEvent4,
          speedEvent5,
          speedEvent6,
          speedEvent7,
          speedEvent8
        )
    }

    // search beginning
    run {
      val speedEvents = dataSource.queryForSpeedData(Range(0.0, 1.0))
      assertThat(speedEvents).containsExactly(speedEvent1)
    }

    // search end
    run {
      val speedEvents = dataSource.queryForSpeedData(Range(6.0, 10.0))
      assertThat(speedEvents).containsExactly(speedEvent8)
    }

    // search outside range of data
    run {
      val speedEvents = dataSource.queryForSpeedData(Range(9.0, 10.0))
      assertThat(speedEvents).isEmpty()
    }

    // no data exist in this range
    run {
      val speedEvents = dataSource.queryForSpeedData(Range(4.0, 5.0))
      assertThat(speedEvents).isEmpty()
    }

    // multiple elements with same timestamp at search boundary
    run {
      val speedEvents = dataSource.queryForSpeedData(Range(2.0, 3.0))
      assertThat(speedEvents)
        .containsExactly(speedEvent2, speedEvent3, speedEvent4, speedEvent5, speedEvent6)
    }
  }

  @Test
  fun searchHttpData(): Unit = runBlocking {
    // Request that starts in the selection range but ends outside of it.
    val httpEvent =
      Event.newBuilder()
        .setTimestamp(1002)
        .setHttpConnectionEvent(
          HttpConnectionEvent.newBuilder()
            .setConnectionId(1)
            .setHttpRequestStarted(
              HttpConnectionEvent.RequestStarted.newBuilder()
                .setFields("a")
                .setMethod("http")
                .setTrace("abc")
                .setUrl("www.google.com")
            )
        )
        .build()
    val httpEvent2 =
      Event.newBuilder()
        .setTimestamp(3000)
        .setHttpConnectionEvent(
          HttpConnectionEvent.newBuilder()
            .setConnectionId(1)
            .setHttpRequestCompleted(HttpConnectionEvent.RequestCompleted.getDefaultInstance())
        )
        .build()

    // Request that starts outside of the range, but ends inside of it.
    val httpEvent3 =
      Event.newBuilder()
        .setTimestamp(44)
        .setHttpConnectionEvent(
          HttpConnectionEvent.newBuilder()
            .setConnectionId(2)
            .setHttpRequestStarted(
              HttpConnectionEvent.RequestStarted.newBuilder()
                .setFields("a")
                .setMethod("http")
                .setTrace("abc")
                .setUrl("www.google.com")
            )
        )
        .build()
    val httpEvent4 =
      Event.newBuilder()
        .setTimestamp(1534)
        .setHttpConnectionEvent(
          HttpConnectionEvent.newBuilder()
            .setConnectionId(2)
            .setHttpRequestCompleted(HttpConnectionEvent.RequestCompleted.getDefaultInstance())
        )
        .build()

    // Request that starts and ends outside the range, but spans over it.
    val httpEvent5 =
      Event.newBuilder()
        .setTimestamp(55)
        .setHttpConnectionEvent(
          HttpConnectionEvent.newBuilder()
            .setConnectionId(3)
            .setHttpRequestStarted(
              HttpConnectionEvent.RequestStarted.newBuilder()
                .setFields("a")
                .setMethod("http")
                .setTrace("abc")
                .setUrl("www.google.com")
            )
        )
        .build()
    val httpEvent6 =
      Event.newBuilder()
        .setTimestamp(4500)
        .setHttpConnectionEvent(
          HttpConnectionEvent.newBuilder()
            .setConnectionId(3)
            .setHttpRequestCompleted(HttpConnectionEvent.RequestCompleted.getDefaultInstance())
        )
        .build()

    // Request that starts and ends outside and not overlap the range.
    val httpEvent7 =
      Event.newBuilder()
        .setTimestamp(58)
        .setHttpConnectionEvent(
          HttpConnectionEvent.newBuilder()
            .setConnectionId(4)
            .setHttpRequestStarted(
              HttpConnectionEvent.RequestStarted.newBuilder()
                .setFields("a")
                .setMethod("http")
                .setTrace("abc")
                .setUrl("www.google.com")
            )
        )
        .build()
    val httpEvent8 =
      Event.newBuilder()
        .setTimestamp(67)
        .setHttpConnectionEvent(
          HttpConnectionEvent.newBuilder()
            .setConnectionId(4)
            .setHttpRequestCompleted(HttpConnectionEvent.RequestCompleted.getDefaultInstance())
        )
        .build()

    val testMessenger =
      TestMessenger(
        scope,
        flowOf(
          httpEvent.toByteArray(),
          httpEvent2.toByteArray(),
          httpEvent3.toByteArray(),
          httpEvent4.toByteArray(),
          httpEvent5.toByteArray(),
          httpEvent6.toByteArray(),
          httpEvent7.toByteArray(),
          httpEvent8.toByteArray()
        )
      )
    val dataSource = NetworkInspectorDataSourceImpl(testMessenger, scope)
    testMessenger.await()

    val httpEvents = dataSource.queryForHttpData(Range(1.0, 2.0))
    assertThat(httpEvents).hasSize(6)
    assertThat(httpEvents).containsNoneOf(httpEvent7, httpEvent8)
  }

  @Test
  fun cleanUpChannelOnDispose() =
    runBlocking<Unit> {
      val testMessenger =
        TestMessenger(scope, flow { throw ArithmeticException("Something went wrong!") })
      val dataSource = NetworkInspectorDataSourceImpl(testMessenger, scope)
      testMessenger.await()
      try {
        dataSource.queryForSpeedData(Range(0.0, 5.0))
        fail()
      } catch (e: Throwable) {
        assertThat(e).isInstanceOf(CancellationException::class.java)
        var cause: Throwable? = e.cause
        while (cause != null && cause !is ArithmeticException) {
          cause = cause.cause
        }
        assertThat(cause).isInstanceOf(ArithmeticException::class.java)
      }
    }
}

private class TestMessenger(override val scope: CoroutineScope, val flow: Flow<ByteArray>) :
  AppInspectorMessenger {
  private val isCollected = CompletableDeferred<Boolean>()

  override suspend fun sendRawCommand(rawData: ByteArray): ByteArray {
    throw NotImplementedError()
  }

  override val eventFlow = flow {
    try {
      flow.collect { value -> emit(value) }
    } finally {
      isCollected.complete(true)
    }
  }

  suspend fun await() {
    isCollected.await()
  }
}
