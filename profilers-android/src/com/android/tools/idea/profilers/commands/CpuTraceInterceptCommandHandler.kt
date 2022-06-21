/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.tools.idea.transport.TransportProxy
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.TransportServiceGrpc

class CpuTraceInterceptCommandHandler(val device: IDevice,
                                      private val transportStub: TransportServiceGrpc.TransportServiceBlockingStub)
  : TransportProxy.ProxyCommandHandler {

  override fun shouldHandle(command: Commands.Command): Boolean {
    // We only check perfetto traces.
    return when (command.type) {
      Commands.Command.CommandType.START_CPU_TRACE -> {
        command.startCpuTrace.configuration.userOptions.traceType == Cpu.CpuTraceType.PERFETTO
      }
      else -> false
    }
  }

  override fun execute(command: Commands.Command): Transport.ExecuteResponse {
    when (command.type) {
      Commands.Command.CommandType.START_CPU_TRACE -> enableTrackingCompose(command)
    }
    return transportStub.execute(Transport.ExecuteRequest.newBuilder()
                                   .setCommand(command)
                                   .build())
  }

  private fun enableTrackingCompose(command: Commands.Command) {
    // TODO (gijosh): replace direct call with wrapper to handle broadcast output.
    device.executeShellCommand(
      "am broadcast -a androidx.tracing.perfetto.action.ENABLE_TRACING ${command.pid}/androidx.tracing.perfetto.TracingReceiver",
      object : IShellOutputReceiver {
        override fun addOutput(data: ByteArray?, offset: Int, length: Int) {
        }

        override fun flush() {
        }

        override fun isCancelled(): Boolean = false
      })
  }
}