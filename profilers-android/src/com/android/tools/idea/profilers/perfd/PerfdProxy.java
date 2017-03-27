/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.profilers.StudioLegacyAllocationTracker;
import com.android.tools.idea.profilers.StudioLegacyCpuTraceProfiler;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Manages the start/stop of a proxy layer that run services bridging between the perfd-host and device perfd.
 */
public final class PerfdProxy implements AndroidDebugBridge.IDeviceChangeListener {

  @NotNull private static final String MEMORY_PROXY_EXECUTOR_NAME = "MemoryServiceProxy";

  @NotNull private Server myProxyServer;
  @NotNull private final List<PerfdProxyService> myProxyServices;
  @NotNull private IDevice myDevice;
  @Nullable private Runnable myOnDisconnect;

  public PerfdProxy(@NotNull IDevice device, @NotNull ManagedChannel perfdChannel, String channelName) {
    myDevice = device;

    myProxyServices = new LinkedList<>();
    myProxyServices.add(new ProfilerServiceProxy(device, perfdChannel));
    myProxyServices.add(new EventServiceProxy(device, perfdChannel));
    myProxyServices.add(new CpuServiceProxy(device,
                                            perfdChannel,
                                            new StudioLegacyCpuTraceProfiler(device)));
    myProxyServices.add(new MemoryServiceProxy(device,
                                               perfdChannel,
                                               Executors.newSingleThreadExecutor(
                                                 new ThreadFactoryBuilder().setNameFormat(MEMORY_PROXY_EXECUTOR_NAME).build()),
                                               (d, p) -> new StudioLegacyAllocationTracker(d, p)));
    myProxyServices.add(new NetworkServiceProxy(device, perfdChannel));

    ServerBuilder builder = InProcessServerBuilder.forName(channelName);
    myProxyServices.forEach(service -> builder.addService(service.getServiceDefinition()));
    myProxyServer = builder.build();
  }

  public void connect() throws IOException {
    myProxyServer.start();
    AndroidDebugBridge.addDeviceChangeListener(this);
  }

  public void disconnect() {
    myProxyServices.forEach(PerfdProxyService::disconnect);
    myProxyServer.shutdownNow();
    AndroidDebugBridge.removeDeviceChangeListener(this);
    if (myOnDisconnect != null) {
      myOnDisconnect.run();
    }
  }

  /**
   * Sets a callback to be executed when the proxy is disconnected.
   * As the proxy is usually associated with a device, services that create the proxy can use this callback
   * for instance to remove references to the proxy when the device is disconnected or when the references to it are lost.
   */
  public void setOnDisconnectCallback(Runnable onDisconnect) {
    myOnDisconnect = onDisconnect;
  }

  @Override
  public void deviceDisconnected(@NonNull IDevice device) {
    // Disconnect the proxy if device is disconnected.
    if (device == myDevice) {
      disconnect();
    }
  }

  @Override
  public void deviceConnected(@NonNull IDevice device) {
    // Do nothing.
  }

  @Override
  public void deviceChanged(@NonNull IDevice device, int changeMask) {
    // Do nothing.
  }
}
