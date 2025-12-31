/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.profilers.commands

import com.android.tools.idea.transport.TransportProxy
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryAnalysisData
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.BlockingDeque

/**
 * Handles the SEND_LEAKCANARY_ANALYSIS command from Studio, converting the payload into a
 * LEAKCANARY_ANALYSIS event and adding it to the event queue for persistence.
 */
class LeakCanaryAnalysisCommandHandler(
  private val transportStub: TransportServiceGrpc.TransportServiceBlockingStub,
  private val eventQueue: BlockingDeque<Common.Event>
) : TransportProxy.ProxyCommandHandler {
  private val logger = Logger.getInstance(LeakCanaryAnalysisCommandHandler::class.java)

  override fun shouldHandle(command: Commands.Command) = command.type == Commands.Command.CommandType.SEND_LEAKCANARY_ANALYSIS

  override fun execute(command: Commands.Command): Transport.ExecuteResponse {
    logger.info("Received SEND_LEAKCANARY_ANALYSIS command.")
    val leakCanaryEvent = LeakCanaryAnalysisData.newBuilder().setData(command.sendLeakcanaryAnalysis.data).build()

    val event = Common.Event.newBuilder()
      .setGroupId(command.pid.toLong())
      .setPid(command.pid)
      .setKind(Common.Event.Kind.LEAKCANARY_ANALYSIS)
      .setTimestamp(transportStub.getCurrentTime(Transport.TimeRequest.getDefaultInstance()).timestampNs)
      .setLeakcanaryAnalysis(leakCanaryEvent)
      .build()
    eventQueue.offer(event)
    logger.info("Sent LEAKCANARY_ANALYSIS event to queue.")
    return Transport.ExecuteResponse.getDefaultInstance()
  }
}