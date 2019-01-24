/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.profilers;

import com.android.annotations.NonNull;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.profilers.perfd.ProfilerServiceProxyManager;
import com.android.tools.idea.profilers.perfd.TransportServiceProxy;
import com.android.tools.idea.transport.TransportDeviceManager;
import com.android.tools.profiler.proto.Common;
import io.grpc.ManagedChannel;
import java.io.IOException;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Manages the interactions between DDMLIB provided devices, and what is needed to spawn ProfilerClient's.
 * On device connection it will spawn the performance daemon on device, and will notify the profiler system that
 * a new device has been connected. *ALL* interaction with IDevice is encapsulated in this class.
 */
class StudioProfilerDeviceManager extends TransportDeviceManager {

  StudioProfilerDeviceManager(@NotNull DataStoreService dataStoreService) {
    super(dataStoreService);
  }

  @Override
  protected TransportThread getTransportThread(@NonNull IDevice device,
                                               @NotNull Map<String, DeviceContext> serialToDeviceContextMap) {
    return new ProfilerThread(device, myDataStoreService, serialToDeviceContextMap);
  }

  private static class ProfilerThread extends TransportThread {
    private ProfilerThread(@NotNull IDevice device, @NotNull DataStoreService datastore,
                           @NotNull Map<String, DeviceContext> serialToDeviceContextMap) {
      super(device, datastore, serialToDeviceContextMap);
    }

    @Override
    protected void preStartTransportThread()
      throws SyncException, AdbCommandRejectedException, TimeoutException, ShellCommandUnresponsiveException, IOException {
      ProfilerDeviceFileManager deviceFileManager = new ProfilerDeviceFileManager(myDevice);
      deviceFileManager.copyProfilerFilesToDevice();
    }

    @Override
    protected String getExecutablePath() {
      return ProfilerDeviceFileManager.getPerfdPath();
    }

    @Override
    protected String getConfigPath() {
      return ProfilerDeviceFileManager.getAgentConfigPath();
    }

    @Override
    protected void postProxyCreation() {
      ProfilerServiceProxyManager.registerProxies(myTransportProxy);
    }

    @Override
    protected void connectDataStore(@NotNull ManagedChannel channel) {
      if (StudioFlags.PROFILER_UNIFIED_PIPELINE.get()) {
        myDataStore.connect(Common.Stream.newBuilder()
                              .setStreamId(myDataStore.getUniqueStreamId())
                              .setType(Common.Stream.Type.DEVICE)
                              .setDevice(TransportServiceProxy.transportDeviceFromIDevice(myDevice))
                              .build(),
                            channel);
      }
      else {
        myDataStore.connect(channel);
      }
    }
  }
}