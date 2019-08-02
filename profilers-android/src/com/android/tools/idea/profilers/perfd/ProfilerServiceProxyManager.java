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
package com.android.tools.idea.profilers.perfd;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.profilers.StudioLegacyAllocationTracker;
import com.android.tools.idea.profilers.StudioLegacyCpuTraceProfiler;
import com.android.tools.idea.profilers.commands.GcCommandHandler;
import com.android.tools.idea.profilers.commands.LegacyAllocationCommandHandler;
import com.android.tools.idea.profilers.commands.LegacyCpuTraceCommandHandler;
import com.android.tools.idea.transport.TransportProxy;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.grpc.ManagedChannel;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.NotNull;

public class ProfilerServiceProxyManager {
  @NotNull private static final String MEMORY_PROXY_EXECUTOR_NAME = "MemoryAllocationDataFetchExecutor";

  public static void registerProxies(TransportProxy transportProxy) {
    IDevice device = transportProxy.getDevice();
    ManagedChannel transportChannel = transportProxy.getTransportChannel();

    transportProxy.registerProxyService(new ProfilerServiceProxy(transportChannel));
    transportProxy.registerProxyService(new EventServiceProxy(device, transportChannel));
    transportProxy.registerProxyService(
      new CpuServiceProxy(device,
                          transportChannel,
                          new StudioLegacyCpuTraceProfiler(device,
                                                           CpuServiceGrpc.newBlockingStub(transportChannel),
                                                           TransportServiceGrpc.newBlockingStub(transportChannel),
                                                           transportProxy.getBytesCache())));
    transportProxy.registerProxyService(new MemoryServiceProxy(
      device, transportChannel,
      Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(MEMORY_PROXY_EXECUTOR_NAME).build()),
      (d, p) -> new StudioLegacyAllocationTracker(d, p),
      transportProxy.getBytesCache()));
    transportProxy.registerProxyService(new NetworkServiceProxy(transportChannel));
    transportProxy.registerProxyService(new EnergyServiceProxy(transportChannel));
  }

  public static void registerCommandHandlers(TransportProxy transportProxy) {
    IDevice device = transportProxy.getDevice();
    ManagedChannel transportChannel = transportProxy.getTransportChannel();

    GcCommandHandler gcCommandHandler = new GcCommandHandler(device);
    transportProxy.registerProxyCommandHandler(Commands.Command.CommandType.GC, gcCommandHandler);

    if (!StudioFlags.PROFILER_USE_LIVE_ALLOCATIONS.get() || device.getVersion().getFeatureLevel() < AndroidVersion.VersionCodes.O) {
      LegacyAllocationCommandHandler trackAllocationHandler =
        new LegacyAllocationCommandHandler(device,
                                           transportProxy.getEventQueue(),
                                           transportProxy.getBytesCache(),
                                           Executors.newSingleThreadExecutor(
                                             new ThreadFactoryBuilder().setNameFormat(MEMORY_PROXY_EXECUTOR_NAME).build()),
                                           (d, p) -> new StudioLegacyAllocationTracker(d, p));
      transportProxy.registerProxyCommandHandler(Commands.Command.CommandType.START_ALLOC_TRACKING, trackAllocationHandler);
      transportProxy.registerProxyCommandHandler(Commands.Command.CommandType.STOP_ALLOC_TRACKING, trackAllocationHandler);
    }

    if (device.getVersion().getFeatureLevel() < AndroidVersion.VersionCodes.O) {
      LegacyCpuTraceCommandHandler cpuTraceHandler =
        new LegacyCpuTraceCommandHandler(device,
                                         TransportServiceGrpc.newBlockingStub(transportChannel),
                                         transportProxy.getEventQueue(),
                                         transportProxy.getBytesCache());
      transportProxy.registerProxyCommandHandler(Commands.Command.CommandType.START_CPU_TRACE, cpuTraceHandler);
      transportProxy.registerProxyCommandHandler(Commands.Command.CommandType.STOP_CPU_TRACE, cpuTraceHandler);
    }
  }
}
