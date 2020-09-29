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
import com.android.tools.app.inspection.AppInspection
import com.android.tools.app.inspection.AppInspection.AppInspectionEvent
import com.android.tools.app.inspection.AppInspection.CrashEvent
import com.android.tools.idea.appinspection.inspector.api.AppInspectionConnectionException
import com.android.tools.idea.appinspection.inspector.api.awaitForDisposal
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils.createRawAppInspectionEvent
import com.android.tools.idea.appinspection.test.TestAppInspectorCommandHandler
import com.android.tools.idea.appinspection.test.INSPECTOR_ID
import com.android.tools.idea.appinspection.test.createCreateInspectorResponse
import com.android.tools.idea.appinspection.test.createRawResponse
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common.Event
import com.android.tools.profiler.proto.Common.Event.Kind.PROCESS
import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase.fail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class AppInspectorConnectionTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  private val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectorConnectionTest", transportService, transportService)!!
  private val appInspectionRule = AppInspectionServiceRule(timer, transportService, grpcServerRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(grpcServerRule).around(appInspectionRule)!!

  @Test
  fun disposeInspectorSucceeds() = runBlocking<Unit> {
    val connection = appInspectionRule.launchInspectorConnection()

    connection.scope.cancel()
  }

  @Test
  fun disposeFailsButInspectorIsDisposedAnyway() = runBlocking<Unit> {
    val connection = appInspectionRule.launchInspectorConnection(
      commandHandler = TestAppInspectorCommandHandler(timer, createInspectorResponse = createCreateInspectorResponse(
        AppInspection.AppInspectionResponse.Status.ERROR,
        AppInspection.CreateInspectorResponse.Status.GENERIC_SERVICE_ERROR))
    )

    connection.scope.cancel()
  }

  @Test
  fun sendRawCommandSucceedWithCallback() = runBlocking<Unit> {
    val connection = appInspectionRule.launchInspectorConnection()

    assertThat(connection.sendRawCommand("TestData".toByteArray())).isEqualTo("TestData".toByteArray())
  }

  @Test
  fun sendRawCommandFailWithCallback() = runBlocking<Unit> {
    val connection = appInspectionRule.launchInspectorConnection(
      commandHandler = TestAppInspectorCommandHandler(timer, rawInspectorResponse = createRawResponse(
        AppInspection.AppInspectionResponse.Status.ERROR, "error"))
    )

    assertThat(connection.sendRawCommand("TestData".toByteArray())).isEqualTo("error".toByteArray())
  }


  @Test
  fun receiveRawEvent() = runBlocking<Unit> {
    val connection = appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID)
    appInspectionRule.addAppInspectionEvent(createRawAppInspectionEvent(byteArrayOf(0x12, 0x15)))

    assertThat(connection.rawEventFlow.take(1).single()).isEqualTo(byteArrayOf(0x12, 0x15))

    // Verify the flow is cold.
    assertThat(connection.rawEventFlow.take(1).single()).isEqualTo(byteArrayOf(0x12, 0x15))

    // Verify flow collection when inspector is disposed.
    connection.scope.cancel()

    try {
      connection.rawEventFlow.single()
      fail()
    }
    catch (e: CancellationException) {
    }
  }

  @Test
  fun disposeConnectionClosesConnection() = runBlocking<Unit> {
    val connection = appInspectionRule.launchInspectorConnection(INSPECTOR_ID)

    connection.scope.cancel()
    connection.awaitForDisposal()

    // connection should be closed
    try {
      connection.sendRawCommand("Test".toByteArray())
      fail()
    }
    catch (e: AppInspectionConnectionException) {
      assertThat(e.message).isEqualTo("Failed to send a command because the $INSPECTOR_ID connection is already closed.")
    }
  }

  @Test
  fun receiveCrashEventClosesConnection() = runBlocking<Unit> {
    val client = appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID)

    val sendRawCommandCalled = CompletableDeferred<Unit>()
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, object : CommandHandler(timer) {
      override fun handleCommand(command: Commands.Command, events: MutableList<Event>) {
        sendRawCommandCalled.complete(Unit)
      }
    })

    val crashed = CompletableDeferred<Unit>()
    launch {
      try {
        // This next line should get stuck (because of the disabled handler above) until the
        // crash event occurs below, which should cause the exception to get thrown.
        client.sendRawCommand(ByteArray(0))
        fail()
      }
      catch (e: AppInspectionConnectionException) {
        assertThat(e.message).isEqualTo("Inspector $INSPECTOR_ID has crashed.")
        crashed.complete(Unit)
      }
    }

    sendRawCommandCalled.join()
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

    crashed.join()
  }

  @Test
  fun sendCommandUsingClosedConnectionThrowsException() = runBlocking<Unit> {
    val client = appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID)

    appInspectionRule.addEvent(
      Event.newBuilder()
        .setKind(PROCESS)
        .setIsEnded(true)
        .build()
    )

    client.awaitForDisposal()

    // connection should be closed
    try {
      client.sendRawCommand("Data".toByteArray())
      fail()
    }
    catch (e: AppInspectionConnectionException) {
      assertThat(e.message).isEqualTo("Failed to send a command because the $INSPECTOR_ID connection is already closed.")
    }
  }

  @Test
  fun crashSetsAllOutstandingFutures() = runBlocking<Unit> {
    val readyDeferred = CompletableDeferred<Unit>()
    val client = appInspectionRule.launchInspectorConnection(
      inspectorId = INSPECTOR_ID,
      commandHandler = object : CommandHandler(timer) {
        override fun handleCommand(command: Commands.Command, events: MutableList<Event>) {
          readyDeferred.complete(Unit)
        }
      }
    )

    supervisorScope {
      val commandDeferred = async { client.sendRawCommand("Blah".toByteArray()) }

      val checkResults = async {
        try {
          commandDeferred.await()
          fail()
        }
        catch (e: AppInspectionConnectionException) {
          assertThat(e.message).isEqualTo("Inspector $INSPECTOR_ID has crashed.")
        }
        catch (e: Exception) {
          println(e)
        }
      }

      readyDeferred.await()

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

      checkResults.await()
    }
  }

  @Test
  fun connectionDoesNotReceiveStaleEvents() = runBlocking<Unit> {
    val staleEventData = byteArrayOf(0x12, 0x15)
    val freshEventData = byteArrayOf(0x01, 0x02)

    timer.currentTimeNs = 3
    appInspectionRule.addAppInspectionEvent(createRawAppInspectionEvent(staleEventData))

    timer.currentTimeNs = 5

    val client = appInspectionRule.launchInspectorConnection()
    appInspectionRule.addAppInspectionEvent(createRawAppInspectionEvent(freshEventData))
    val rawData = client.rawEventFlow.take(1).single()
    assertThat(rawData).isEqualTo(freshEventData)
  }

  @ExperimentalCoroutinesApi
  @Test
  fun connectionDoesNotReceiveAlreadyReceivedEvents() = runBlocking<Unit> {
    val firstEventData = byteArrayOf(0x12, 0x15)
    val secondEventData = byteArrayOf(0x01, 0x02)

    val client = appInspectionRule.launchInspectorConnection()

    appInspectionRule.addAppInspectionEvent(createRawAppInspectionEvent(firstEventData))

    var count = 0
    val flow = client.rawEventFlow.map { eventData ->
      count++
      eventData
    }

    // This test seems more complicated than it needs to be because we want to force the two events to be polled in separate cycles. We want
    // to check the subsequence polling cycle does not pick up the events already seen in the first cycle. Therefore, we use a flow here to
    // receive the first event before adding the second event to the service.
    flow.take(2).withIndex().collect { indexedValue ->
      if (indexedValue.index == 0) {
        assertThat(indexedValue.value).isEqualTo(firstEventData)
        appInspectionRule.addAppInspectionEvent(createRawAppInspectionEvent(secondEventData))
      }
      else {
        assertThat(indexedValue.value).isEqualTo(secondEventData)
      }
    }
  }

  @Test
  fun cancelRawCommandSendsCancellationCommand() = runBlocking<Unit> {
    val client = appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID)
    val cancelReadyDeferred = CompletableDeferred<Unit>()
    val cancelCompletedDeferred = CompletableDeferred<Unit>()

    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, object : CommandHandler(timer) {
      var commandId: Int? = null
      override fun handleCommand(command: Commands.Command, events: MutableList<Event>) {
        if (command.appInspectionCommand.hasRawInspectorCommand()) {
          commandId = command.appInspectionCommand.commandId
          cancelReadyDeferred.complete(Unit)
        }
        else if (command.appInspectionCommand.hasCancellationCommand()) {
          assertThat(command.appInspectionCommand.cancellationCommand.cancelledCommandId).isEqualTo(commandId)
          cancelCompletedDeferred.complete(Unit)
        }
      }
    })

    val sendJob = launch { client.sendRawCommand(ByteString.copyFromUtf8("Blah").toByteArray()) }
    cancelReadyDeferred.await()
    sendJob.cancel()
    cancelCompletedDeferred.await()
  }

  @Test
  fun scopeCancellation() = runBlocking<Unit> {
    val client = appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID)

    val sendRawCommandCalled = CompletableDeferred<Unit>()
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, object : CommandHandler(timer) {
      override fun handleCommand(command: Commands.Command, events: MutableList<Event>) {
        sendRawCommandCalled.complete(Unit)
      }
    })

    launch {
      try {
        // This next line should get stuck (because of the disabled handler above) until the
        // `scope.cancel` call below, which should cause the exception to get thrown.
        client.sendRawCommand(byteArrayOf(0x12, 0x15))
        fail()
      }
      catch (e: AppInspectionConnectionException) {
        assertThat(e.message).isEqualTo("Inspector $INSPECTOR_ID was disposed.")
      }
    }

    sendRawCommandCalled.join()
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestAppInspectorCommandHandler(timer))
    appInspectionRule.scope.cancel()

    client.awaitForDisposal()
  }

  @Test
  fun verifyAwaitForDisposalReturnsExpectedValues() = runBlocking<Unit> {
    // Scope cancellation
    val client = appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID)
    client.scope.cancel()
    assertThat(client.awaitForDisposal()).isTrue()

    // Process ended
    val client2 = appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID)
    appInspectionRule.addEvent(
      Event.newBuilder()
        .setKind(PROCESS)
        .setIsEnded(true)
        .build()
    )
    assertThat(client2.awaitForDisposal()).isTrue()

    // Crash
    val client3 = appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID)
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
    assertThat(client3.awaitForDisposal()).isFalse()
  }
}