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

import com.android.ddmlib.IDevice;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Manages the start/stop of a proxy layer that run services bridging between the perfd-host and device perfd.
 */
final public class PerfdProxy {

  @NotNull private Server myProxyServer;

  public PerfdProxy(@NotNull IDevice device, @NotNull ManagedChannel perfdChannel, @NotNull String proxyServerName) {
    ServerBuilder builder = InProcessServerBuilder.forName(proxyServerName);
    builder.addService(new ProfilerServiceProxy(device, perfdChannel));
    builder.addService(new EventServiceProxy(device, perfdChannel));
    builder.addService(new CpuServiceProxy(device, perfdChannel));
    builder.addService(new MemoryServiceProxy(device, perfdChannel));
    builder.addService(new NetworkServiceProxy(device, perfdChannel));
    myProxyServer = builder.build();
  }

  public void connect() throws IOException {
    myProxyServer.start();
  }

  public void disconnect() {
    myProxyServer.shutdown();
  }
}
