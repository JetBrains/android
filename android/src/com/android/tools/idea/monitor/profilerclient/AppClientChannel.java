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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * This handles the connection between the ProfilerService and the App.
 * It is package private since it should only ever be accessed by ProfilerClient.
 */
class AppClientChannel {
  @NotNull private final DeviceClientChannel myDeviceClientChannel;
  @NotNull private final Client myClient;
  @NotNull private final List<ProfilerClientListener> myListeners;

  public AppClientChannel(@NotNull DeviceClientChannel deviceClientChannel,
                   @NotNull Client client,
                   @NotNull ProfilerClientListener listener) {
    myDeviceClientChannel = deviceClientChannel;
    myClient = client;
    myListeners = new ArrayList<ProfilerClientListener>(2);
    myListeners.add(listener);
  }

  @NotNull
  public Client getClient() {
    return myClient;
  }

  @NotNull
  public DeviceClientChannel getDeviceClientChannel() {
    return myDeviceClientChannel;
  }

  public void sendData(@NotNull ProfilerClientListener listener,
                @Nullable byte[] data,
                int offset,
                int length,
                byte flags,
                byte type,
                short subType,
                boolean waitForResponse) {
    assert offset >= 0;
    assert length >= 0;

    myDeviceClientChannel.queueCommand(
      new ClientCommand(ClientCommand.CommandType.SEND, listener, data, offset, length, flags, type, subType, waitForResponse));
  }

  public void addListener(@NotNull ProfilerClientListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(@NotNull ProfilerClientListener listener) {
    myListeners.remove(listener);
  }

  public int getProfilerClientListenersCount() {
    return myListeners.size();
  }
}
