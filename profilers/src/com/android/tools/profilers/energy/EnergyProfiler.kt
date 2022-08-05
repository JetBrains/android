// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.profilers.energy

import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.EnergyProfiler.EnergyStartRequest
import com.android.tools.profiler.proto.EnergyProfiler.EnergyStopRequest
import com.android.tools.profilers.ProfilerMonitor
import com.android.tools.profilers.StudioProfiler
import com.android.tools.profilers.StudioProfilers
import com.intellij.openapi.diagnostic.Logger

class EnergyProfiler(private val profilers: StudioProfilers) : StudioProfiler {
  private val logger: Logger = Logger.getInstance(EnergyProfiler::class.java)

  override fun newMonitor(): ProfilerMonitor {
    return EnergyMonitor(profilers)
  }

  override fun startProfiling(session: Common.Session) {
    // TODO(b/150503095)
    val startResponse = profilers.client.energyClient.startMonitoringApp(EnergyStartRequest.newBuilder().setSession(session).build())

    if (!profilers.ideServices.featureConfig.isUnifiedPipelineEnabled) {
      return
    }

    // Issue GetCpuCoreConfig command once so we can calculate CPU energy usage.
    // We need the device ID to run the command, but there has been a report (b/146037091) that 'profilers.device' may
    // be null in release build. Therefore we use if to guard the use of the device to avoid NPE, instead of assert.
    if (profilers.device == null) {
      logger.warn("Unable to retrieve CPU frequency files; device ID unknown.")
      return
    }

    // CPU frequency files may not always be available (e.g. emulator), in which case we still have a fallback model to use from
    // DefaultPowerProfile.
    profilers.client.executeAsync(
      Commands.Command.newBuilder()
        .setStreamId(session.streamId)
        .setPid(session.pid)
        .setType(Commands.Command.CommandType.GET_CPU_CORE_CONFIG)
        .setGetCpuCoreConfig(
          Commands.GetCpuCoreConfig.newBuilder().setDeviceId(
            profilers.device!!.deviceId
          )
        ).build(),
      profilers.ideServices.poolExecutor
    )
  }

  override fun stopProfiling(session: Common.Session) {
    // TODO(b/150503095)
    val response = profilers.client.energyClient.stopMonitoringApp(EnergyStopRequest.newBuilder().setSession(session).build())
  }
}