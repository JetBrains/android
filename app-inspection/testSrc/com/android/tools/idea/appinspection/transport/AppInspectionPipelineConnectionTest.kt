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
import com.android.tools.app.inspection.AppInspection.AppInspectionEvent
import com.android.tools.app.inspection.AppInspection.ServiceResponse
import com.android.tools.app.inspection.AppInspection.ServiceResponse.Status.SUCCESS
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands.Command
import com.android.tools.profiler.proto.Commands.Command.CommandType.ATTACH_AGENT
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.AgentData
import com.android.tools.profiler.proto.Common.AgentData.Status.ATTACHED
import com.android.tools.profiler.proto.Common.Event
import com.android.tools.profiler.proto.Common.Event.Kind.AGENT
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.AsyncFunction
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.android.tools.profiler.proto.Commands.Command.CommandType.APP_INSPECTION as APP_INSPECTION_COMMAND

class AppInspectionPipelineConnectionTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)
  private val executorService = Executors.newSingleThreadExecutor()

  @get:Rule
  val gRpcServerRule = FakeGrpcServer.createFakeGrpcServer("InspectorPipelineConnectionTest", transportService, transportService)!!

  @After
  fun tearDown() {
    executorService.shutdownNow()
  }

  @Test
  fun launchInspector() {
    transportService.setCommandHandler(
      ATTACH_AGENT,
      object : CommandHandler(timer) {
        override fun handleCommand(command: Command, events: MutableList<Event>) {
          events.add(
            Event.newBuilder()
              .setKind(AGENT)
              .setAgentData(AgentData.newBuilder().setStatus(ATTACHED).build())
              .build()
          )
        }
      })
    transportService.setCommandHandler(
      APP_INSPECTION_COMMAND,
      object : CommandHandler(timer) {
        override fun handleCommand(command: Command, events: MutableList<Event>) {
          events.add(
            Event.newBuilder()
              .setKind(APP_INSPECTION)
              .setPid(command.pid)
              .setTimestamp(timer.currentTimeNs)
              .setCommandId(command.commandId)
              .setIsEnded(true)
              .setAppInspectionEvent(AppInspectionEvent.newBuilder()
                                       .setResponse(ServiceResponse.newBuilder()
                                                      .setStatus(SUCCESS)
                                                      .build())
                                       .build())
              .build()
          )
        }
      })
    val connection = AppInspectionPipelineConnection.attach(
       Common.Stream.getDefaultInstance(), Common.Process.getDefaultInstance(), TransportClient(gRpcServerRule.name), executorService)
    val clientFuture = Futures.transformAsync(connection, AsyncFunction<AppInspectionPipelineConnection, TestInspectorClient> {
      it!!.launchInspector(
        "test.inspector",
        Paths.get("path", "to", "inspector", "dex")) { TestInspectorClient(it) }
    }, MoreExecutors.directExecutor())
    assertThat(clientFuture.get(1, TimeUnit.SECONDS)).isNotNull()
  }
}

