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
package com.android.tools.idea.appinspection.api

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.appinspection.inspector.api.StubTestAppInspectorClient
import com.android.tools.idea.appinspection.internal.DefaultAppInspectionTarget
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils.createFakeLaunchParameters
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils.createFakeProcessDescriptor
import com.android.tools.idea.appinspection.test.TEST_JAR
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AppInspectionTargetTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  private val gRpcServerRule = FakeGrpcServer.createFakeGrpcServer("InspectorTargetTest", transportService, transportService)!!
  private val appInspectionRule = AppInspectionServiceRule(timer, transportService, gRpcServerRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(gRpcServerRule).around(appInspectionRule)!!

  @Test
  fun launchInspector() = runBlocking<Unit> {
    val target = appInspectionRule.launchTarget(createFakeProcessDescriptor())
    target.launchInspector(createFakeLaunchParameters()) { commandMessenger ->
      assertThat(appInspectionRule.jarCopier.copiedJar).isEqualTo(TEST_JAR)
      TestInspectorClient(commandMessenger)
    }
  }

  @Test
  fun launchInspectorReturnsCorrectConnection() = runBlocking<Unit> {
    val target = appInspectionRule.launchTarget(createFakeProcessDescriptor())

    transportService.setCommandHandler(
      Commands.Command.CommandType.APP_INSPECTION,
      object : CommandHandler(timer) {
        override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
          if (command.appInspectionCommand.inspectorId == "never_connects") {
            // Generate a false "successful" service response with a non-existent commandId, to test connection filtering. That is, we don't
            // want this response to be accepted by any pending connections.
            events.add(Common.Event.newBuilder()
                         .setKind(Common.Event.Kind.APP_INSPECTION_RESPONSE)
                         .setPid(command.pid)
                         .setTimestamp(timer.currentTimeNs)
                         .setCommandId(command.commandId)
                         .setIsEnded(true)
                         .setAppInspectionResponse(AppInspection.AppInspectionResponse.newBuilder()
                                                     .setCommandId(1324562)
                                                     .setStatus(AppInspection.AppInspectionResponse.Status.SUCCESS)
                                                     .setServiceResponse(AppInspection.ServiceResponse.getDefaultInstance())
                                                     .build())
                         .build())
          }
          else {
            // Manually generate correct response to the following launch command.
            events.add(Common.Event.newBuilder()
                         .setKind(Common.Event.Kind.APP_INSPECTION_RESPONSE)
                         .setPid(command.pid)
                         .setTimestamp(timer.currentTimeNs)
                         .setCommandId(command.commandId)
                         .setIsEnded(true)
                         .setAppInspectionResponse(AppInspection.AppInspectionResponse.newBuilder()
                                                     .setCommandId(command.appInspectionCommand.commandId)
                                                     .setStatus(AppInspection.AppInspectionResponse.Status.SUCCESS)
                                                     .setServiceResponse(AppInspection.ServiceResponse.getDefaultInstance())
                                                     .build())
                         .build())
          }
        }
      })
    // Launch an inspector connection that will never be established (assuming the test passes).
    val unsuccessfulJob = launch {
      target.launchInspector(createFakeLaunchParameters(inspectorId = "never_connects")) { commandMessenger ->
        TestInspectorClient(commandMessenger)
      }
    }

    try {
      // Launch an inspector connection that will be successfully established.
      val successfulJob = launch {
        target.launchInspector(
          createFakeLaunchParameters(inspectorId = "connects_successfully")) { commandMessenger ->
          TestInspectorClient(commandMessenger)
        }
      }

      successfulJob.join()
      assertThat(unsuccessfulJob.isActive).isTrue()
    }
    finally {
      unsuccessfulJob.cancelAndJoin()
    }
  }

  @Test
  fun clientIsCached() = runBlocking<Unit> {
    val process = createFakeProcessDescriptor()
    val target = appInspectionRule.launchTarget(process)

    // Launch an inspector.
    val firstClient = target.launchInspector(createFakeLaunchParameters(process)) { StubTestAppInspectorClient(it) }

    // Launch another inspector with same parameters.
    val secondClient = target.launchInspector(createFakeLaunchParameters(process)) { StubTestAppInspectorClient(it) }

    // Check they are the same.
    assertThat(firstClient).isSameAs(secondClient)
  }


  @Test
  fun processTerminationDisposesClient() = runBlocking<Unit> {
    val target = appInspectionRule.launchTarget(createFakeProcessDescriptor()) as DefaultAppInspectionTarget

    // Launch an inspector client.
    val client = target.launchInspector(createFakeLaunchParameters()) { StubTestAppInspectorClient(it) }

    // Verify there is one new client.
    assertThat(target.clients).hasSize(1)

    suspendCoroutine<Unit> { cont ->
      client.addServiceEventListener(
        object : AppInspectorClient.ServiceEventListener {
          override fun onDispose() {
            cont.resume(Unit)
          }
        }, appInspectionRule.executorService)

      // Fake target termination to dispose of client.
      transportService.addEventToStream(
        FakeTransportService.FAKE_DEVICE_ID,
        Common.Event.newBuilder()
          .setTimestamp(timer.currentTimeNs)
          .setKind(Common.Event.Kind.PROCESS)
          .setGroupId(FakeTransportService.FAKE_PROCESS.pid.toLong())
          .setPid(FakeTransportService.FAKE_PROCESS.pid)
          .setIsEnded(true)
          .build()
      )
    }

    assertThat(target.clients).isEmpty()
  }

  @Test
  fun disposeTarget() = runBlocking<Unit> {
    val target = appInspectionRule.launchTarget(createFakeProcessDescriptor()) as DefaultAppInspectionTarget

    val clientLaunchParams1 = createFakeLaunchParameters(inspectorId = "a")
    val clientLaunchParams2 = createFakeLaunchParameters(inspectorId = "b")

    val client1 = target.launchInspector(clientLaunchParams1) { messenger ->
      object : AppInspectorClient(messenger) {
        override val rawEventListener = object : RawEventListener {
          override fun onRawEvent(eventData: ByteArray) {
          }
        }
      }
    }

    val jobForClient2 = launch {
      transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, object : CommandHandler(timer) {
        override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
          // Keep client2 hanging so we can verify its cancellation.
        }
      })
      target.launchInspector(clientLaunchParams2) { messenger ->
        object : AppInspectorClient(messenger) {
          override val rawEventListener = object : RawEventListener {
            override fun onRawEvent(eventData: ByteArray) {
            }
          }
        }
      }
    }

    target.dispose()

    suspendCoroutine<Unit> { cont ->
      client1.addServiceEventListener(object : AppInspectorClient.ServiceEventListener {
        override fun onDispose() {
          cont.resume(Unit)
        }
      }, MoreExecutors.directExecutor())
    }

    jobForClient2.join()
  }
}