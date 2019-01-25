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
package com.android.tools.idea.transport;

import com.android.ddmlib.IDevice;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Manages the start/stop of a proxy layer that run services bridging between the transport database and device daemon.
 */
public final class TransportProxy {

  private Server myProxyServer;
  @NotNull private final List<TransportProxyService> myProxyServices;
  @NotNull private IDevice myDevice;
  @NotNull private ManagedChannel myTransportChannel;

  public TransportProxy(@NotNull IDevice device, @NotNull ManagedChannel transportChannel) {
    myDevice = device;
    myTransportChannel = transportChannel;
    myProxyServices = new LinkedList<>();
  }

  public void registerProxyService(TransportProxyService proxyService) {
    myProxyServices.add(proxyService);
  }

  /**
   * Must be called as the last step of initializing the proxy server, after registering proxy services
   */
  public void initializeProxyServer(String channelName) {
    ServerBuilder builder = InProcessServerBuilder.forName(channelName);
    myProxyServices.forEach(service -> builder.addService(service.getServiceDefinition()));
    myProxyServer = builder.build();
  }

  public void connect() throws IOException {
    if (myProxyServer == null) {
      throw new IllegalStateException("Proxy server has not been built");
    }
    myProxyServer.start();
  }

  public void disconnect() {
    if (myProxyServer == null) {
      throw new IllegalStateException("Proxy server has not been built");
    }
    myProxyServices.forEach(TransportProxyService::disconnect);
    myProxyServer.shutdownNow();
  }

  @NotNull
  public IDevice getDevice() {
    return myDevice;
  }

  @NotNull
  public ManagedChannel getTransportChannel() {
    return myTransportChannel;
  }
}
