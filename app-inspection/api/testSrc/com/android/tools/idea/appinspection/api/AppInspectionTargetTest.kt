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
import com.android.tools.idea.appinspection.internal.AppInspectionTransport
import com.android.tools.idea.appinspection.test.ASYNC_TIMEOUT_MS
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils.createSuccessfulServiceResponse
import com.android.tools.idea.appinspection.test.INSPECTOR_ID
import com.android.tools.idea.appinspection.test.TEST_JAR
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.AsyncFunction
import com.google.common.util.concurrent.Futures
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class AppInspectionTargetTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  private val gRpcServerRule = FakeGrpcServer.createFakeGrpcServer("InspectorTargetTest", transportService, transportService)!!
  private val appInspectionRule = AppInspectionServiceRule(timer, transportService, gRpcServerRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(gRpcServerRule).around(appInspectionRule)!!

  @get:Rule
  val timeoutRule = Timeout(ASYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS)

  @Test
  fun launchInspector() {
    val clientFuture = Futures.transformAsync(
      appInspectionRule.launchTarget(),
      AsyncFunction<AppInspectionTarget, TestInspectorClient> { target ->
        target!!.launchInspector(INSPECTOR_ID, TEST_JAR) { commandMessenger ->
          assertThat(appInspectionRule.jarCopier.copiedJar).isEqualTo(TEST_JAR)
          TestInspectorClient(commandMessenger)
        }
      }, appInspectionRule.executorService
    )
    assertThat(clientFuture.get()).isNotNull()
  }

  @Test
  fun launchInspectorReturnsCorrectConnection() {
    val target = appInspectionRule.launchTarget().get()

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
      target.launchInspector("never_connects", TEST_JAR) { commandMessenger ->
        TestInspectorClient(commandMessenger)
      }

    try {
      // Generate a false "successful" service response with a non-existent commandId, to test connection filtering. That is, we don't want
      // this response to be accepted by any pending connections.
      appInspectionRule.addAppInspectionResponse(createSuccessfulServiceResponse(12345))

      // Launch an inspector connection that will be successfully established.
      val successfulConnection = target.launchInspector("connects_successfully", TEST_JAR) { commandMessenger ->
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
    } finally {
      unsuccessfulConnection.cancel(false)
    }
  }
}

