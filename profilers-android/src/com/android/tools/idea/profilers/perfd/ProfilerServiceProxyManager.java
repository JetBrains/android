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
import com.android.tools.idea.profilers.StudioLegacyAllocationTracker;
import com.android.tools.idea.profilers.StudioLegacyCpuTraceProfiler;
import com.android.tools.idea.transport.TransportProxy;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.grpc.ManagedChannel;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.NotNull;

public class ProfilerServiceProxyManager {
  @NotNull private static final String MEMORY_PROXY_EXECUTOR_NAME = "MemoryServiceProxy";

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
      (d, p) -> new StudioLegacyAllocationTracker(d, p)));
    transportProxy.registerProxyService(new NetworkServiceProxy(transportChannel));
    transportProxy.registerProxyService(new EnergyServiceProxy(transportChannel));
  }
}
