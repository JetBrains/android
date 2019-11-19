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
import com.android.tools.idea.appinspection.api.AppInspectionTestUtils.createSuccessfulServiceResponse
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common.Event
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.AsyncFunction
import com.google.common.util.concurrent.Futures
import junit.framework.TestCase
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class AppInspectionPipelineConnectionTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  private val gRpcServerRule = FakeGrpcServer.createFakeGrpcServer("InspectorPipelineConnectionTest", transportService, transportService)!!
  private val appInspectionRule = AppInspectionServiceRule(timer, transportService, gRpcServerRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(gRpcServerRule).around(appInspectionRule)!!

  @get:Rule
  val timeoutRule = Timeout(ASYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS)

  @Test
  fun launchInspector() {
    val clientFuture = Futures.transformAsync(
      appInspectionRule.launchPipelineConnection(),
      AsyncFunction<AppInspectionPipelineConnection, TestInspectorClient> { pipelineConnection ->
        pipelineConnection!!.launchInspector(INSPECTOR_ID, TEST_JAR) { commandMessenger ->
          assertThat(appInspectionRule.jarCopier.copiedJar).isEqualTo(TEST_JAR)
          TestInspectorClient(commandMessenger)
        }
      }, appInspectionRule.executorService
    )
    assertThat(clientFuture.get()).isNotNull()
  }

  @Test
  fun launchInspectorReturnsCorrectConnection() {
    val connection = appInspectionRule.launchPipelineConnection().get()

    val latch = CountDownLatch(1)
    // Don't let command handler reply to any commands. We'll manually add events.
    transportService.setCommandHandler(
      Commands.Command.CommandType.APP_INSPECTION,
      object : CommandHandler(timer) {
        override fun handleCommand(command: Commands.Command, events: MutableList<Event>) {
          latch.countDown()
        }
      })

    val inspectorConnection =
      connection.launchInspector(INSPECTOR_ID, TEST_JAR) { commandMessenger ->
        TestInspectorClient(commandMessenger)
      }

    latch.await()

    val incorrectEvent = createSuccessfulServiceResponse(12345)
    appInspectionRule.addAppInspectionEvent(incorrectEvent)

    appInspectionRule.poller.poll()

    assertThat(appInspectionRule.executorService.activeCount).isEqualTo(0)
    assertThat(inspectorConnection.isDone).isFalse()

    appInspectionRule.addAppInspectionEvent(
      createSuccessfulServiceResponse(
        AppInspectionTransport.lastGeneratedCommandId()
      )
    )

    assertThat(inspectorConnection.get()).isNotNull()
  }
}

