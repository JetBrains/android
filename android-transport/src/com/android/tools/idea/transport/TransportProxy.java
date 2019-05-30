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
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * Manages the start/stop of a proxy layer that run services bridging between the transport database and device daemon.
 */
public final class TransportProxy {

  private Server myProxyServer;
  @NotNull private final List<ServiceProxy> myProxyServices;
  @NotNull private final Map<Commands.Command.CommandType, Function<Commands.Command, Transport.ExecuteResponse>> myProxyHandlers;
  @NotNull private IDevice myDevice;
  @NotNull private ManagedChannel myTransportChannel;
  @NotNull private final TransportServiceProxy myProxyService;
  // General file/byte cache used in the proxy layer.
  @NotNull private final Map<String, ByteString> myProxyBytesCache = Collections.synchronizedMap(new HashMap<>());

  public TransportProxy(@NotNull IDevice ddmlibDevice, @NotNull Common.Device transportDevice, @NotNull ManagedChannel transportChannel) {
    myDevice = ddmlibDevice;
    myTransportChannel = transportChannel;
    myProxyServices = new LinkedList<>();
    myProxyHandlers = new HashMap<>();
    myProxyService = new TransportServiceProxy(ddmlibDevice, transportDevice, transportChannel, myProxyBytesCache);
  }

  @NotNull
  public Map<String, ByteString> getBytesCache() {
    return myProxyBytesCache;
  }

  public void registerProxyService(ServiceProxy proxyService) {
    myProxyServices.add(proxyService);
  }

  /**
   * Registers to handle specific command types in the proxy layer, useful for supporting workflows that are otherwise unavailable
   * from the device directly. e.g. pre-O memory allocation tracking and cpu traces.
   */
  public void registerProxyCommandHandler(Commands.Command.CommandType commandType,
                                          Function<Commands.Command, Transport.ExecuteResponse> handler) {
    myProxyService.registerCommandHandler(commandType, handler);
  }

  public void registerEventPreprocessor(TransportEventPreprocessor eventPreprocessor) {
    myProxyService.registerEventPreprocessor(eventPreprocessor);
  }

  /**
   * Must be called as the last step of initializing the proxy server, after registering proxy services
   */
  public void initializeProxyServer(String channelName) {
    ServerBuilder builder = InProcessServerBuilder.forName(channelName);
    myProxyServices.add(myProxyService);
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
    myProxyServices.forEach(ServiceProxy::disconnect);
    // Shutdown instead of shutdown now so that the proxy services have a chance to finish sending through their data.
    myProxyServer.shutdown();
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
