/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport

import com.android.tools.idea.protobuf.ByteString
import com.android.tools.pipeline.example.proto.Echo
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Rule
import org.junit.rules.Timeout
import java.io.IOException
import java.util.concurrent.BlockingDeque
import java.util.concurrent.CountDownLatch

/**
 * Extends PlatformTestCase as initialization of TransportService is dependent on IJ's Application instance.
 */
class TransportServiceTest : LightPlatformTestCase() {

  private lateinit var service: TransportService

  @get:Rule
  val timeout: Timeout = Timeout.seconds(60)

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    service = TransportServiceImpl()
    Disposer.register(testRootDisposable, service)
  }

  /**
   * Validate that events and bytes can be sent from a custom stream to the database, which can then be queried via a client.
   */
  fun testRegisterStreamServer() {
    val event1 = Common.Event.newBuilder().apply {
      pid = 1
      kind = Common.Event.Kind.ECHO
      echo = Echo.EchoData.newBuilder().setData("event1").build()
    }.build()
    val event2 = Common.Event.newBuilder().apply {
      pid = 2
      kind = Common.Event.Kind.ECHO
      echo = Echo.EchoData.newBuilder().setData("event2").build()
    }.build()
    val event3 = Common.Event.newBuilder().apply {
      pid = 3
      kind = Common.Event.Kind.ECHO
      echo = Echo.EchoData.newBuilder().setData("event3").build()
    }.build()

    val testStreamServer = EventStreamServer("testRegisterStreamServer")
    testStreamServer.eventDeque.offer(event1)
    try {
      testStreamServer.start()
    }
    catch (ignored: IOException) {
    }

    val stream = service.registerStreamServer(Common.Stream.Type.FILE, testStreamServer)
    waitForQueueDrained(testStreamServer.eventDeque)

    // Validates that we can query all events from the database.
    val client = TransportClient(TransportService.channelName)
    val request = Transport.GetEventGroupsRequest.newBuilder().apply {
      streamId = stream.streamId
      kind = Common.Event.Kind.ECHO
    }.build()

    // Retry to avoid a race condition where the events are drained from the deque but yet to be inserted into the database.
    val retryCount = 10
    var eventsFound = false
    for (i in 1..retryCount) {
      val response = client.transportStub.getEventGroups(request)
      eventsFound = response.groupsList.flatMap { group -> group.eventsList }.containsAll(listOf(event1))
      if (eventsFound) {
        break
      }
      try {
        Thread.sleep(10)
      }
      catch (ignored: InterruptedException) {
      }
    }
    assertThat(eventsFound).isTrue()

    // Validates that events that are inserted after will also be streamed.
    testStreamServer.eventDeque.offer(event2)
    testStreamServer.eventDeque.offer(event3)
    waitForQueueDrained(testStreamServer.eventDeque)
    eventsFound = false
    for (i in 1..retryCount) {
      val response = client.transportStub.getEventGroups(request)
      eventsFound = response.groupsList.flatMap { group -> group.eventsList }.containsAll(listOf(event1, event2, event3))
      if (eventsFound) {
        break
      }
      try {
        Thread.sleep(10)
      }
      catch (ignored: InterruptedException) {
      }
    }
    assertThat(eventsFound).isTrue()

    // Validates that bytes can be queried from the custom stream as well.
    val testBytes = ByteString.copyFrom("DeadBeef".toByteArray())
    testStreamServer.byteCacheMap["test"] = testBytes
    assertThat(client.transportStub
                 .getBytes(Transport.BytesRequest.newBuilder().setStreamId(stream.streamId).setId("test").build())
                 .contents)
      .isEqualTo(testBytes)

    // Validates that bytes can't be queried after server stopped.
    service.unregisterStreamServer(stream.streamId)
    testStreamServer.byteCacheMap["test2"] = ByteString.copyFrom("DeadBeef2".toByteArray())
    assertThat(client.transportStub
                 .getBytes(Transport.BytesRequest.newBuilder().setStreamId(stream.streamId).setId("test2").build()))
      .isEqualTo(Transport.BytesResponse.getDefaultInstance())
    client.shutdown()
  }

  // Wait for the events to be drained from the deque. This ensures that they are in the database ready to be queried.
  private fun waitForQueueDrained(deque: BlockingDeque<Common.Event>) {
    val doneLatch = CountDownLatch(1)
    Thread {
      while (!deque.isEmpty()) {
        try {
          Thread.sleep(100)
        }
        catch (ignored: InterruptedException) {
        }
      }

      doneLatch.countDown()
    }.start()

    try {
      doneLatch.await()
    }
    catch (ignored: InterruptedException) {
    }
  }
}