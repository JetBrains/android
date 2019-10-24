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
package com.android.tools.idea.appinspection.transport

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.app.inspection.AppInspection
import com.android.tools.app.inspection.AppInspection.AppInspectionEvent
import com.android.tools.app.inspection.AppInspection.CrashEvent
import com.android.tools.app.inspection.AppInspection.RawEvent
import com.android.tools.app.inspection.AppInspection.ServiceResponse.Status.ERROR
import com.android.tools.app.inspection.AppInspection.ServiceResponse.Status.SUCCESS
import com.android.tools.idea.appinspection.transport.AppInspectorClient.EventListener
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Event
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION
import com.android.tools.profiler.proto.Common.Event.Kind.PROCESS
import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase.fail
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TIMEOUT_MILLISECONDS: Long = 10000

private const val INSPECTOR_ID = "test.inspector"

/**
 * Keeps track of all events so they can be gotten later and compared.
 */
private open class TestInspectorEventListener : EventListener {
  private val events = mutableListOf<ByteArray>()
  private val crashes = mutableListOf<String>()
  var isDisposed = false

  val rawEvents
    get() = events.toList()

  val crashEvents
    get() = crashes.toList()

  override fun onRawEvent(eventData: ByteArray) {
    events.add(eventData)
  }

  override fun onCrashEvent(message: String) {
    crashes.add(message)
  }

  override fun onDispose() {
    isDisposed = true
  }
}

class AppInspectorConnectionTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  @get:Rule
  val gRpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectorConnectionTest", transportService, transportService)!!

  private val stream = Common.Stream.getDefaultInstance()
  private val process = Common.Process.getDefaultInstance()

  private val executorService = Executors.newSingleThreadExecutor()

  @After
  fun tearDown() {
    executorService.shutdownNow()
  }

  private fun setUpServiceAndConnection(
    appInspectionHandler: CommandHandler,
    listener: EventListener = TestInspectorEventListener()
  ): AppInspectorClient.CommandMessenger {
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, appInspectionHandler)
    val client = launchInspectorForTest(
      INSPECTOR_ID, TransportClient(gRpcServerRule.name), stream, process,
      executorService) { TestInspectorClient(it, listener) }
    return client.messenger
  }

  @Test
  fun disposeInspectorSucceedWithCallback() {
    val connection = setUpServiceAndConnection(TestInspectorCommandHandler(timer))

    assertThat(connection.disposeInspector().get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS))
      .isEqualTo(AppInspection.ServiceResponse.newBuilder()
                   .setStatus(SUCCESS)
                   .build())
  }

  @Test
  fun disposeInspectorFailWithCallback() {
    val connection = setUpServiceAndConnection(TestInspectorCommandHandler(timer, false, "error"))

    assertThat(connection.disposeInspector().get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS))
      .isEqualTo(AppInspection.ServiceResponse.newBuilder()
                   .setStatus(ERROR)
                   .setErrorMessage("error")
                   .build())
  }

  @Test
  fun sendRawCommandSucceedWithCallback() {
    val connection = setUpServiceAndConnection(TestInspectorCommandHandler(timer))

    assertThat(connection.sendRawCommand("TestData".toByteArray()).get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS))
      .isEqualTo("TestData".toByteArray())
  }

  @Test
  fun sendRawCommandFailWithCallback() {
    val connection = setUpServiceAndConnection(TestInspectorCommandHandler(timer, false, "error"))

    assertThat(connection.sendRawCommand("TestData".toByteArray()).get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS))
      .isEqualTo("error".toByteArray())
  }

  @Test
  fun receiveGeneralEvent() {
    val latch = CountDownLatch(1)
    val eventListener = object : TestInspectorEventListener() {
      override fun onRawEvent(eventData: ByteArray) {
        super.onRawEvent(eventData)
        latch.countDown()
      }
    }
    setUpServiceAndConnection(/* doesn't matter */TestInspectorCommandHandler(timer), eventListener)

    val generalEvent = Event.newBuilder()
      .setKind(APP_INSPECTION)
      .setTimestamp(timer.currentTimeNs)
      .setIsEnded(true)
      .setAppInspectionEvent(AppInspectionEvent.newBuilder()
                               .setRawEvent(RawEvent.newBuilder()
                                              .setInspectorId(INSPECTOR_ID)
                                              .setContent(ByteString.copyFrom(byteArrayOf(0x12, 0x15)))
                                              .build())
                               .build())
      .build()

    transportService.addEventToStream(0, generalEvent)

    try {
      assertThat(latch.await(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)).isEqualTo(true)
    }
    catch (e: InterruptedException) {
      e.printStackTrace()
      fail("Test interrupted")
    }
    assertThat(eventListener.rawEvents[0]).isEqualTo(byteArrayOf(0x12, 0x15))
  }

  @Test
  fun rawEventWithCommandIdOnlyTriggersEventListener() {
    val eventListener = TestInspectorEventListener()
    val connection = setUpServiceAndConnection(/* doesn't matter */TestInspectorCommandHandler(timer), eventListener)

    assertThat(connection.sendRawCommand("TestData".toByteArray()).get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS))
      .isEqualTo("TestData".toByteArray())
    assertThat(eventListener.rawEvents).isEmpty()
  }

  @Test
  fun disposeConnectionClosesConnection() {
    val connection = setUpServiceAndConnection(TestInspectorCommandHandler(timer))

    assertThat(connection.disposeInspector().get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS))
      .isEqualTo(AppInspection.ServiceResponse.newBuilder().setStatus((SUCCESS)).build())

    // connection should be closed
    try {
      connection.sendRawCommand("Test".toByteArray()).get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    }
    catch (e: ExecutionException) {
      assertThat(e.cause).isInstanceOf(IllegalStateException::class.java)
      assertThat(e.cause!!.message).isEqualTo("Failed to send a command because the $INSPECTOR_ID connection is already closed.")
    }
  }

  @Test
  fun receiveCrashEventClosesConnection() {
    val latch = CountDownLatch(1)
    val eventListener = object : TestInspectorEventListener() {
      override fun onCrashEvent(message: String) {
        super.onCrashEvent(message)
        latch.countDown()
      }
    }
    val connection = setUpServiceAndConnection(/* doesn't matter */TestInspectorCommandHandler(timer), eventListener)

    val crashEvent = Event.newBuilder()
      .setKind(APP_INSPECTION)
      .setTimestamp(timer.currentTimeNs)
      .setIsEnded(true)
      .setAppInspectionEvent(AppInspectionEvent.newBuilder()
                               .setCrashEvent(CrashEvent.newBuilder()
                                                .setInspectorId(INSPECTOR_ID)
                                                .setErrorMessage("error")
                                                .build())
                               .build())
      .build()

    transportService.addEventToStream(0, crashEvent)

    try {
      assertThat(latch.await(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)).isEqualTo(true)
    }
    catch (e: InterruptedException) {
      e.printStackTrace()
      fail("Test interrupted")
    }
    assertThat(eventListener.crashEvents).containsExactly("error")
    assertThat(eventListener.isDisposed).isTrue()

    // connection should be closed
    try {
      connection.disposeInspector().get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    }
    catch (e: ExecutionException) {
      assertThat(e.cause).isInstanceOf(RuntimeException::class.java)
      assertThat(e.cause!!.message).isEqualTo("Inspector test.inspector has crashed.")
    }
  }

  @Test
  fun disposeUsingClosedConnectionThrowsException() {
    val latch = CountDownLatch(1)
    val eventListener = object : TestInspectorEventListener() {
      override fun onDispose() {
        latch.countDown()
      }
    }
    val connection = setUpServiceAndConnection(/* doesn't matter */TestInspectorCommandHandler(timer), eventListener)

    val processEndedEvent = Event.newBuilder()
      .setKind(PROCESS)
      .setIsEnded(true)
      .setTimestamp(timer.currentTimeNs)
      .build()

    transportService.addEventToStream(stream.streamId, processEndedEvent)

    try {
      assertThat(latch.await(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)).isEqualTo(true)
    }
    catch (e: InterruptedException) {
      e.printStackTrace()
      fail("Test interrupted")
    }

    // connection should be closed
    try {
      connection.disposeInspector().get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    }
    catch (e: ExecutionException) {
      assertThat(e.cause).isInstanceOf(RuntimeException::class.java)
      assertThat(e.cause!!.message).isEqualTo("Inspector $INSPECTOR_ID was disposed, because app process terminated.")
    }
  }

  @Test
  fun sendCommandUsingClosedConnectionThrowsException() {
    val latch = CountDownLatch(1)
    val eventListener = object : TestInspectorEventListener() {
      override fun onDispose() {
        super.onDispose()
        latch.countDown()
      }
    }
    val connection = setUpServiceAndConnection(/* doesn't matter */TestInspectorCommandHandler(timer), eventListener)

    val processEndedEvent = Event.newBuilder()
      .setKind(PROCESS)
      .setIsEnded(true)
      .setTimestamp(timer.currentTimeNs)
      .build()

    transportService.addEventToStream(stream.streamId, processEndedEvent)

    try {
      assertThat(latch.await(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)).isEqualTo(true)
    }
    catch (e: InterruptedException) {
      e.printStackTrace()
      fail("Test interrupted")
    }

    // connection should be closed
    try {
      connection.sendRawCommand("Data".toByteArray()).get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    }
    catch (e: ExecutionException) {
      assertThat(e.cause).isInstanceOf(IllegalStateException::class.java)
      assertThat(e.cause!!.message).isEqualTo("Failed to send a command because the $INSPECTOR_ID connection is already closed.")
    }
  }

  @Test
  fun crashSetsAllOutstandingFutures() {
    val latch = CountDownLatch(1)
    val eventListener = object : TestInspectorEventListener() {
      override fun onCrashEvent(message: String) {
        super.onCrashEvent(message)
        latch.countDown()
      }
    }
    val connection = setUpServiceAndConnection(object : CommandHandler(timer) {
      override fun handleCommand(command: Commands.Command, events: MutableList<Event>) {
        // do nothing
      }
    }, eventListener)

    val disposeFuture = connection.disposeInspector()
    val commandFuture = connection.sendRawCommand("Blah".toByteArray())

    val crashEvent = Event.newBuilder()
      .setKind(APP_INSPECTION)
      .setTimestamp(timer.currentTimeNs)
      .setIsEnded(true)
      .setAppInspectionEvent(AppInspectionEvent.newBuilder()
                               .setCrashEvent(CrashEvent.newBuilder()
                                                .setInspectorId(INSPECTOR_ID)
                                                .setErrorMessage("error")
                                                .build())
                               .build())
      .build()

    transportService.addEventToStream(0, crashEvent)

    try {
      assertThat(latch.await(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)).isEqualTo(true)
    }
    catch (e: InterruptedException) {
      e.printStackTrace()
      fail("Test interrupted")
    }
    assertThat(eventListener.crashEvents).containsExactly("error")

    try {
      disposeFuture.get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    }
    catch (e: ExecutionException) {
      assertThat(e.cause).isInstanceOf(RuntimeException::class.java)
      assertThat(e.cause!!.message).isEqualTo("Inspector $INSPECTOR_ID has crashed.")
    }

    try {
      commandFuture.get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    }
    catch (e: ExecutionException) {
      assertThat(e.cause).isInstanceOf(RuntimeException::class.java)
      assertThat(e.cause!!.message).isEqualTo("Inspector $INSPECTOR_ID has crashed.")
    }
  }
}