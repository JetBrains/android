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
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.appinspection.inspector.api.StubTestAppInspectorClient
import com.android.tools.idea.appinspection.internal.AppInspectionTransport
import com.android.tools.idea.appinspection.internal.DefaultAppInspectionTarget
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils.createFakeLaunchParameters
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils.createFakeProcessDescriptor
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils.createSuccessfulServiceResponse
import com.android.tools.idea.appinspection.test.TEST_JAR
import com.android.tools.idea.concurrency.transformAsync
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.CountDownLatch
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
  fun launchInspector() {
    appInspectionRule.launchTarget(createFakeProcessDescriptor()).transformAsync(appInspectionRule.executorService) { target ->
      target.launchInspector(createFakeLaunchParameters()) { commandMessenger ->
        assertThat(appInspectionRule.jarCopier.copiedJar).isEqualTo(TEST_JAR)
        TestInspectorClient(commandMessenger)
      }
    }.get()
  }

  @Test
  fun launchInspectorReturnsCorrectConnection() {
    val target = appInspectionRule.launchTarget(createFakeProcessDescriptor()).get()

    // We set the App Inspection command handler to not do anything with commands, because the test will manually insert responses. This is
    // done to better control the timing of events.
    val commandsExecutedLatch = CountDownLatch(2)
    transportService.setCommandHandler(
      Commands.Command.CommandType.APP_INSPECTION,
      object : CommandHandler(timer) {
        override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
          commandsExecutedLatch.countDown()
        }
      })

    // Launch an inspector connection that will never be established (if the test passes).
    val unsuccessfulConnection =
      target.launchInspector(createFakeLaunchParameters(inspectorId = "never_connects")) { commandMessenger ->
        TestInspectorClient(commandMessenger)
      }

    try {
      // Generate a false "successful" service response with a non-existent commandId, to test connection filtering. That is, we don't want
      // this response to be accepted by any pending connections.
      appInspectionRule.addAppInspectionResponse(createSuccessfulServiceResponse(12345))

      // Launch an inspector connection that will be successfully established.
      val successfulConnection = target.launchInspector(
        createFakeLaunchParameters(inspectorId = "connects_successfully")) { commandMessenger ->
        TestInspectorClient(commandMessenger)
      }

      // Wait for launch inspector commands to be executed. Otherwise, target may not be in a testable state.
      commandsExecutedLatch.await()

      // Manually generate correct response to the command.
      appInspectionRule.addAppInspectionResponse(
        createSuccessfulServiceResponse(
          AppInspectionTransport.lastGeneratedCommandId()
        )
      )

      // Verify the first connection is still pending and the second connection is successful.
      assertThat(successfulConnection.get()).isNotNull()
      assertThat(unsuccessfulConnection.isDone).isFalse()
    }
    finally {
      unsuccessfulConnection.cancel(false)
    }
  }

  @Test
  fun clientIsCached() {
    val process = createFakeProcessDescriptor()
    val target = appInspectionRule.launchTarget(process).get()

    // Launch an inspector.
    val firstClient = target.launchInspector(createFakeLaunchParameters(process)) { StubTestAppInspectorClient(it) }.get()

    // Launch another inspector with same parameters.
    val secondClient = target.launchInspector(createFakeLaunchParameters(process)) { StubTestAppInspectorClient(it) }.get()

    // Check they are the same.
    assertThat(firstClient).isSameAs(secondClient)
  }


  @Test
  fun processTerminationDisposesClient() {
    val target = appInspectionRule.launchTarget(createFakeProcessDescriptor()).get() as DefaultAppInspectionTarget

    // Launch an inspector client.
    target.launchInspector(createFakeLaunchParameters()) { StubTestAppInspectorClient(it) }.get()

    // Verify there is one new client.
    assertThat(target.clients).hasSize(1)

    // Set up latch to wait for client disposal.
    // Note: The callback below must use the same executor as discovery service because we rely on the knowledge that the single threaded
    // executor will execute them AFTER executing the system's cleanup code. Otherwise, the latch may get released too early due to race,
    // and cause the test to flake.
    val clientDisposedLatch = CountDownLatch(1)
    target.clients.values.first().get().addServiceEventListener(
      object : AppInspectorClient.ServiceEventListener {
        override fun onDispose() {
          clientDisposedLatch.countDown()
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

    // Wait and verify
    clientDisposedLatch.await()
    assertThat(target.clients).isEmpty()
  }

  @Test
  fun disposeTarget() = runBlocking<Unit> {
    val target = appInspectionRule.launchTarget(createFakeProcessDescriptor()).get() as DefaultAppInspectionTarget

    val clientLaunchParams1 = createFakeLaunchParameters(inspectorId = "a")
    val clientLaunchParams2 = createFakeLaunchParameters(inspectorId = "b")

    suspendCoroutine<Unit> { cont ->
      val messenger = object : AppInspectorClient.CommandMessenger {
        override suspend fun disposeInspector() {
          cont.resume(Unit)
        }

        override suspend fun sendRawCommand(rawData: ByteArray): ByteArray {
          throw NotImplementedError()
        }
      }
      val client = object : AppInspectorClient(messenger) {
        override val rawEventListener: RawEventListener
          get() = throw NotImplementedError()
      }

      val clientFuture1 = Futures.immediateFuture<AppInspectorClient>(client)
      val clientFuture2 = SettableFuture.create<AppInspectorClient>()

      target.clients[clientLaunchParams1] = clientFuture1
      target.clients[clientLaunchParams2] = clientFuture2

      target.dispose()

      // TODO(b/144771043): Can't reliably assert these until AppInspectionTarget is migrated to coroutines.
      //assertThat(target.clients).isEmpty()
      //assertThat(clientFuture2.isCancelled).isTrue()
    }
  }
}

