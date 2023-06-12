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
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.idea.io.grpc.ManagedChannel;
import com.android.tools.idea.io.grpc.Server;
import com.android.tools.idea.io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * Manages the start/stop of a proxy layer that run services bridging between the transport database and device daemon.
 */
public class TransportProxy {

  public interface ProxyCommandHandler {
    Transport.ExecuteResponse execute(Commands.Command command);

    // Returns true if the registered proxy command handler should handle the command, false if the proxy should delegate to the device.
    default boolean shouldHandle(Commands.Command command) {
      return true;
    }
  }

  private Server myProxyServer;
  @NotNull private final IDevice myDevice;
  @NotNull private final ManagedChannel myTransportChannel;
  @NotNull private final TransportServiceProxy myProxyService;
  @NotNull private final LinkedBlockingDeque<Common.Event> myProxyEventQueue = new LinkedBlockingDeque<>();
  // General file/byte cache used in the proxy layer.
  @NotNull private final Map<String, ByteString> myProxyBytesCache = Collections.synchronizedMap(new HashMap<>());

  public TransportProxy(@NotNull IDevice ddmlibDevice, @NotNull Common.Device transportDevice, @NotNull ManagedChannel transportChannel) {
    myDevice = ddmlibDevice;
    myTransportChannel = transportChannel;
    myProxyService = new TransportServiceProxy(ddmlibDevice, transportDevice, transportChannel, myProxyEventQueue, myProxyBytesCache);
  }

  @NotNull
  public BlockingDeque<Common.Event> getEventQueue() {
    return myProxyEventQueue;
  }

  @NotNull
  public Map<String, ByteString> getBytesCache() {
    return myProxyBytesCache;
  }

  /**
   * Registers to handle specific command types in the proxy layer, useful for supporting workflows that are otherwise unavailable
   * from the device directly. e.g. pre-O memory allocation tracking and cpu traces.
   */
  public void registerProxyCommandHandler(Commands.Command.CommandType commandType,
                                          ProxyCommandHandler handler) {
    myProxyService.registerCommandHandler(commandType, handler);
  }

  public void registerEventPreprocessor(TransportEventPreprocessor eventPreprocessor) {
    myProxyService.registerEventPreprocessor(eventPreprocessor);
  }

  public void registerDataPreprocessor(TransportBytesPreprocessor dataPreprocessor) {
    myProxyService.registerDataPreprocessor(dataPreprocessor);
  }

  /**
   * Must be called as the last step of initializing the proxy server, after registering proxy services
   */
  public void initializeProxyServer(String channelName) {
    myProxyServer = InProcessServerBuilder.forName(channelName)
      .addService(myProxyService.getServiceDefinition())
      .build();
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
    myProxyService.disconnect();
    // Shutdown instead of shutdown now so that the proxy services have a chance to finish sending through their data.
    myProxyServer.shutdown();

    if (!myTransportChannel.isShutdown()) {
      myTransportChannel.shutdown();
    }
    try {
      myTransportChannel.awaitTermination(2, TimeUnit.SECONDS);
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
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
