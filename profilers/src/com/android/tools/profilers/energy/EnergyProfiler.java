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
package com.android.tools.profilers.energy;

import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyStartRequest;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyStopRequest;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfiler;
import com.android.tools.profilers.StudioProfilers;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

public class EnergyProfiler extends StudioProfiler {
  private static Logger getLogger() {
    return Logger.getInstance(EnergyProfiler.class);
  }

  public EnergyProfiler(@NotNull StudioProfilers profilers) {
    super(profilers);
  }

  @Override
  public ProfilerMonitor newMonitor() {
    return new EnergyMonitor(myProfilers);
  }

  @Override
  public void startProfiling(Common.Session session) {
    myProfilers.getClient().getEnergyClient().startMonitoringApp(EnergyStartRequest.newBuilder().setSession(session).build());

    if (myProfilers.getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      // Issue GetCpuCoreConfig command once so we can calculate CPU energy usage.
      assert myProfilers.getDevice() != null;
      try {
        myProfilers.getClient().getTransportClient().execute(
          Transport.ExecuteRequest.newBuilder()
            .setCommand(
              Commands.Command.newBuilder()
                .setStreamId(session.getStreamId())
                .setPid(session.getPid())
                .setType(Commands.Command.CommandType.GET_CPU_CORE_CONFIG)
                .setGetCpuCoreConfig(Commands.GetCpuCoreConfig.newBuilder().setDeviceId(myProfilers.getDevice().getDeviceId())))
            .build());
      }
      catch (StatusRuntimeException e) {
        // CPU frequency files may not always be available (e.g. emulator), in which case we still have a fallback model to use from
        // DefaultPowerProfile.
        getLogger().warn("Unable to parse CPU frequency files.");
      }
    }
  }

  @Override
  public void stopProfiling(Common.Session session) {
    myProfilers.getClient().getEnergyClient().stopMonitoringApp(EnergyStopRequest.newBuilder().setSession(session).build());
  }
}
