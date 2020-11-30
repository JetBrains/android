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

import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.LogService;
import com.android.tools.idea.diagnostics.crash.exception.NoPiiException;
import com.android.tools.profiler.proto.Common;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.messages.MessageBus;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

/**
 * An application-level service for establishing a connection to a device, which can then be used to retrieve Android system and app data.
 * The service is application-level because devices/processes are accessible through multiple projects, and we want the pipeline to work
 * across project where users can use different client features in multiple studio instances.
 */
public class TransportService implements Disposable {
  public static TransportService getInstance() {
    return ApplicationManager.getApplication().getService(TransportService.class);
  }

  private static Logger getLogger() {
    return Logger.getInstance(TransportService.class);
  }

  private static final String DATASTORE_NAME = "DataStoreService";
  public static final String CHANNEL_NAME = DATASTORE_NAME;

  @NotNull private final LogService myLogService;
  @NotNull private final MessageBus myMessageBus;
  @NotNull private final DataStoreService myDataStoreService;
  @NotNull private final TransportDeviceManager myDeviceManager;

  // Forever incrementing stream id for custom servers. Collision with device-based stream id is unlikely as they are generated based on
  // device's boot_id ^ timestamp.
  @NotNull private final AtomicInteger myCustomStreamId = new AtomicInteger();
  @NotNull private final Map<Long, EventStreamServer> myStreamIdToServerMap = new HashMap<>();

  @VisibleForTesting
  TransportService() {
    String datastoreDirectory = Paths.get(PathManager.getSystemPath(), ".android").toString() + File.separator;
    myLogService = new IntellijLogService();
    myDataStoreService =
      new DataStoreService(DATASTORE_NAME, datastoreDirectory, ApplicationManager.getApplication()::executeOnPooledThread, myLogService);
    myDataStoreService.setNoPiiExceptionHandler((t) -> getLogger().error(new NoPiiException(t)));

    myMessageBus = ApplicationManager.getApplication().getMessageBus();
    myDeviceManager = new TransportDeviceManager(myDataStoreService, myMessageBus, this);
  }

  @Override
  public void dispose() {
    myStreamIdToServerMap.forEach((id, server) -> server.stop());
    myStreamIdToServerMap.clear();

    myDataStoreService.shutdown();
  }

  @NotNull
  public LogService getLogService() {
    return myLogService;
  }

  @NotNull
  public MessageBus getMessageBus() {
    return myMessageBus;
  }

  /**
   * @return The {@link Common.Stream} instance that was created for the server.
   */
  @NotNull
  public Common.Stream registerStreamServer(Common.Stream.Type streamType, @NotNull EventStreamServer streamServer) {
    Common.Stream stream = Common.Stream.newBuilder().setStreamId(myCustomStreamId.incrementAndGet()).setType(streamType).build();
    ManagedChannel channel = InProcessChannelBuilder.forName(streamServer.getServerName()).usePlaintext().directExecutor().build();
    myDataStoreService.connect(stream, channel);

    myStreamIdToServerMap.put(stream.getStreamId(), streamServer);

    return stream;
  }

  public void unregisterStreamServer(long streamId) {
    if (myStreamIdToServerMap.containsKey(streamId)) {
      myStreamIdToServerMap.get(streamId).stop();
      myDataStoreService.disconnect(streamId);
      myStreamIdToServerMap.remove(streamId);
    }
  }
}
