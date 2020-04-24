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
package com.android.tools.idea.appinspection.internal

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.app.inspection.AppInspection.AppInspectionEvent
import com.android.tools.app.inspection.AppInspection.CrashEvent
import com.android.tools.idea.appinspection.api.AppInspectionConnectionException
import com.android.tools.idea.appinspection.api.TestInspectorCommandHandler
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.appinspection.test.ASYNC_TIMEOUT_MS
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils.createRawAppInspectionEvent
import com.android.tools.idea.appinspection.test.INSPECTOR_ID
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common.Event
import com.android.tools.profiler.proto.Common.Event.Kind.PROCESS
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import junit.framework.TestCase.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class AppInspectorConnectionTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  private val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectorConnectionTest", transportService, transportService)!!
  private val appInspectionRule = AppInspectionServiceRule(timer, transportService, grpcServerRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(grpcServerRule).around(appInspectionRule)!!

  @get:Rule
  val timeoutRule = Timeout(ASYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS)

  @Test
  fun disposeInspectorSucceeds() {
    val connection = appInspectionRule.launchInspectorConnection()

    connection.messenger.disposeInspector().get()
  }

  @Test
  fun disposeFailsButInspectorIsDisposedAnyway() {
    val connection = appInspectionRule.launchInspectorConnection(
      commandHandler = TestInspectorCommandHandler(timer, false, "error")
    )

    connection.messenger.disposeInspector().get()
  }

  @Test
  fun sendRawCommandSucceedWithCallback() {
    val connection = appInspectionRule.launchInspectorConnection()

    assertThat(connection.messenger.sendRawCommand("TestData".toByteArray()).get()).isEqualTo("TestData".toByteArray())
  }

  @Test
  fun sendRawCommandFailWithCallback() {
    val connection = appInspectionRule.launchInspectorConnection(
      commandHandler = TestInspectorCommandHandler(timer, false, "error")
    )

    assertThat(connection.messenger.sendRawCommand("TestData".toByteArray()).get()).isEqualTo("error".toByteArray())
  }


  @Test
  fun receiveGeneralEvent() {
    val latch = CountDownLatch(1)
    val eventListener = object : AppInspectionServiceRule.TestInspectorRawEventListener() {
      override fun onRawEvent(eventData: ByteArray) {
        super.onRawEvent(eventData)
        latch.countDown()
      }
    }
    appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID, eventListener = eventListener)

    appInspectionRule.addAppInspectionEvent(createRawAppInspectionEvent(byteArrayOf(0x12, 0x15)))

    latch.await()
    assertThat(eventListener.rawEvents[0]).isEqualTo(byteArrayOf(0x12, 0x15))
  }

  @Test
  fun rawEventWithCommandIdOnlyTriggersEventListener() {
    val eventListener = AppInspectionServiceRule.TestInspectorRawEventListener()
    val connection = appInspectionRule.launchInspectorConnection(eventListener = eventListener)

    assertThat(connection.messenger.sendRawCommand("TestData".toByteArray()).get()).isEqualTo("TestData".toByteArray())
    assertThat(eventListener.rawEvents).isEmpty()
  }

  @Test
  fun disposeConnectionClosesConnection() {
    val connection = appInspectionRule.launchInspectorConnection(INSPECTOR_ID)

    connection.messenger.disposeInspector().get()

    // connection should be closed
    try {
      connection.messenger.sendRawCommand("Test".toByteArray()).get()
      fail()
    } catch (e: ExecutionException) {
      assertThat(e.cause).isInstanceOf(AppInspectionConnectionException::class.java)
      assertThat(e.cause!!.message).isEqualTo("Failed to send a command because the $INSPECTOR_ID connection is already closed.")
    }
  }

  @Test
  fun receiveCrashEventClosesConnection() {
    val clientDisposedLatch = CountDownLatch(2)
    val client = appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID)
    client.addServiceEventListener(object : AppInspectorClient.ServiceEventListener {
      override fun onCrashEvent(message: String) {
        assertThat(message).isEqualTo("error")
        clientDisposedLatch.countDown()
      }

      override fun onDispose() {
        clientDisposedLatch.countDown()
      }
    }, MoreExecutors.directExecutor())

    appInspectionRule.addAppInspectionEvent(
      AppInspectionEvent.newBuilder()
        .setInspectorId(INSPECTOR_ID)
        .setCrashEvent(
          CrashEvent.newBuilder()
            .setErrorMessage("error")
            .build()
        )
        .build()
    )

    clientDisposedLatch.await()

    // connection should be closed
    try {
      client.messenger.disposeInspector().get()
      fail()
    } catch (e: ExecutionException) {
      assertThat(e.cause).isInstanceOf(AppInspectionConnectionException::class.java)
      assertThat(e.cause!!.message).isEqualTo("Inspector $INSPECTOR_ID has crashed.")
    }
  }

  @Test
  fun disposeUsingClosedConnectionThrowsException() {
    val disposedLatch = CountDownLatch(1)
    val client = appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID)
    client.addServiceEventListener(object : AppInspectorClient.ServiceEventListener {
      override fun onDispose() {
        disposedLatch.countDown()
      }
    }, MoreExecutors.directExecutor())

    appInspectionRule.addEvent(
      Event.newBuilder()
        .setKind(PROCESS)
        .setIsEnded(true)
        .setTimestamp(timer.currentTimeNs)
        .build()
    )

    disposedLatch.await()

    // connection should be closed
    try {
      client.messenger.disposeInspector().get()
      fail()
    } catch (e: ExecutionException) {
      assertThat(e.cause).isInstanceOf(AppInspectionConnectionException::class.java)
      assertThat(e.cause!!.message).isEqualTo("Inspector $INSPECTOR_ID was disposed, because app process terminated.")
    }
  }

  @Test
  fun sendCommandUsingClosedConnectionThrowsException() {
    val latch = CountDownLatch(1)

    val client = appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID)
    client.addServiceEventListener(object : AppInspectorClient.ServiceEventListener {
      override fun onDispose() {
        latch.countDown()
      }
    }, MoreExecutors.directExecutor())

    appInspectionRule.addEvent(
      Event.newBuilder()
        .setKind(PROCESS)
        .setIsEnded(true)
        .build()
    )

    latch.await()

    // connection should be closed
    try {
      client.messenger.sendRawCommand("Data".toByteArray()).get()
      fail()
    } catch (e: ExecutionException) {
      assertThat(e.cause).isInstanceOf(AppInspectionConnectionException::class.java)
      assertThat(e.cause!!.message).isEqualTo("Failed to send a command because the $INSPECTOR_ID connection is already closed.")
    }
  }

  @Test
  fun crashSetsAllOutstandingFutures() {
    val latch = CountDownLatch(1)
    val client = appInspectionRule.launchInspectorConnection(
      inspectorId = INSPECTOR_ID,
      commandHandler = object : CommandHandler(timer) {
        override fun handleCommand(command: Commands.Command, events: MutableList<Event>) {
          // do nothing
        }
      }
    )
    client.addServiceEventListener(object : AppInspectorClient.ServiceEventListener {
      override fun onCrashEvent(message: String) {
        assertThat(message).isEqualTo("error")
        latch.countDown()
      }
    }, MoreExecutors.directExecutor())

    val disposeFuture = client.messenger.disposeInspector()
    val commandFuture = client.messenger.sendRawCommand("Blah".toByteArray())

    appInspectionRule.addAppInspectionEvent(
      AppInspectionEvent.newBuilder()
        .setInspectorId(INSPECTOR_ID)
        .setCrashEvent(
          CrashEvent.newBuilder()
            .setErrorMessage("error")
            .build()
        )
        .build()
    )

    latch.await()

    try {
      disposeFuture.get()
      fail()
    } catch (e: ExecutionException) {
      assertThat(e.cause).isInstanceOf(AppInspectionConnectionException::class.java)
      assertThat(e.cause!!.message).isEqualTo("Inspector $INSPECTOR_ID has crashed.")
    }

    try {
      commandFuture.get()
      fail()
    } catch (e: ExecutionException) {
      assertThat(e.cause).isInstanceOf(AppInspectionConnectionException::class.java)
      assertThat(e.cause!!.message).isEqualTo("Inspector $INSPECTOR_ID has crashed.")
    }
  }

  @Test
  fun connectionDoesNotReceiveStaleEvents() {
    val latch = CountDownLatch(1)
    val staleEventData = byteArrayOf(0x12, 0x15)
    val freshEventData = byteArrayOf(0x01, 0x02)
    val listener = object : AppInspectionServiceRule.TestInspectorRawEventListener() {
      override fun onRawEvent(eventData: ByteArray) {
        super.onRawEvent(eventData)
        latch.countDown()
      }
    }

    timer.currentTimeNs = 3
    appInspectionRule.addAppInspectionEvent(createRawAppInspectionEvent(staleEventData))

    timer.currentTimeNs = 5
    appInspectionRule.launchInspectorConnection(
      inspectorId = INSPECTOR_ID,
      eventListener = listener
    )
    appInspectionRule.addAppInspectionEvent(createRawAppInspectionEvent(freshEventData))

    latch.await()

    assertThat(listener.rawEvents).hasSize(1)
    assertThat(listener.rawEvents[0]).isEqualTo(freshEventData)
  }

  @Test
  fun connectionDoesNotReceiveAlreadyReceivedEvents() {
    val firstLatch = CountDownLatch(1)
    val secondLatch = CountDownLatch(1)
    val firstEventData = byteArrayOf(0x12, 0x15)
    val secondEventData = byteArrayOf(0x01, 0x02)
    val listener = object : AppInspectionServiceRule.TestInspectorRawEventListener() {
      override fun onRawEvent(eventData: ByteArray) {
        super.onRawEvent(eventData)
        if (firstLatch.count > 0) {
          firstLatch.countDown()
        } else {
          secondLatch.countDown()
        }
      }
    }

    appInspectionRule.launchInspectorConnection(eventListener = listener)

    appInspectionRule.addAppInspectionEvent(createRawAppInspectionEvent(firstEventData))

    firstLatch.await()
    assertThat(listener.rawEvents).hasSize(1)

    timer.currentTimeNs = 3
    appInspectionRule.addAppInspectionEvent(createRawAppInspectionEvent(secondEventData))

    secondLatch.await()
    assertThat(listener.rawEvents).hasSize(2)
    assertThat(listener.rawEvents[0]).isEqualTo(firstEventData)
    assertThat(listener.rawEvents[1]).isEqualTo(secondEventData)
  }

  @Test
  fun cancelRawCommandSendsCancellationCommand() {
    val client = appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID)

    val cancelledLatch = CountDownLatch(1)
    var toBeCancelledCommandId: Int? = null
    var cancelledCommandId: Int? = null

    // Override App Inspection command handler to not respond to any commands, so the test can have control over timing of events.
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, object : CommandHandler(timer) {
      override fun handleCommand(command: Commands.Command, events: MutableList<Event>) {
        if (command.appInspectionCommand.hasRawInspectorCommand()) {
          toBeCancelledCommandId = command.appInspectionCommand.commandId
        } else if (command.appInspectionCommand.hasCancellationCommand()) {
          cancelledCommandId = command.appInspectionCommand.cancellationCommand.cancelledCommandId
          cancelledLatch.countDown()
        }
      }
    })

    client.messenger.sendRawCommand(ByteString.copyFromUtf8("Blah").toByteArray()).cancel(false)

    cancelledLatch.await()

    assertThat(toBeCancelledCommandId).isNotNull()
    assertThat(toBeCancelledCommandId).isEqualTo(cancelledCommandId)
  }
}