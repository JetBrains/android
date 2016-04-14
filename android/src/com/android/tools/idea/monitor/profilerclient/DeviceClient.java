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
package com.android.tools.idea.monitor.profilerclient;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The handle/API for clients to communicate with the server on the device.
 */
public class DeviceClient {
  @NotNull private final DeviceClientChannel myDeviceClientChannel;
  @NotNull private final ProfilerClientListener myProfilerClientListener;

  DeviceClient(@NotNull DeviceClientChannel deviceClientChannel, @NotNull ProfilerClientListener profilerClientListener) {
    myDeviceClientChannel = deviceClientChannel;
    myProfilerClientListener = profilerClientListener;
  }

  @NotNull
  DeviceClientChannel getDeviceClientChannel() {
    return myDeviceClientChannel;
  }

  @NotNull
  ProfilerClientListener getProfilerClientListener() {
    return myProfilerClientListener;
  }

  /**
   * Attempt to connect to an App on the current Device.
   * @param client  is the ddmlib Client that is available to connect to
   * @param profilerClientListener  a listener to listen for messages from the App
   * @return null if a connection can't be established, or an {@link AppClient} handle for communication
   */
  @Nullable
  public AppClient connect(@NotNull Client client, @NotNull ProfilerClientListener profilerClientListener) {
    return myDeviceClientChannel.connect(client, profilerClientListener);
  }

  public void disconnect(@NotNull AppClient appClient) {
    myDeviceClientChannel.disconnect(appClient);
  }

  @NotNull
  public IDevice getDevice() {
    return myDeviceClientChannel.getDevice();
  }
}
