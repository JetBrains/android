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

/**
 * The handle/API for clients that need to request data from the App, in order for the client to communicate with the App.
 */
public class AppClient {
  @NotNull private final AppClientChannel myAppClientChannel;
  @NotNull private final ProfilerClientListener myProfilerClientListener;

  AppClient(@NotNull AppClientChannel appClientChannel, @NotNull ProfilerClientListener profilerClientListener) {
    myAppClientChannel = appClientChannel;
    myProfilerClientListener = profilerClientListener;
  }

  @NotNull
  AppClientChannel getAppClientChannel() {
    return myAppClientChannel;
  }

  @NotNull
  ProfilerClientListener getProfilerClientListener() {
    return myProfilerClientListener;
  }

  @NotNull
  public Client getClient() {
    return myAppClientChannel.getClient();
  }

  public void sendData(byte flags, byte type, short subType, boolean waitForResponse) {
    sendData(null, 0, 0, flags, type, subType, waitForResponse);
  }

  public void sendData(@NotNull byte[] data, byte flags, byte type, short subType, boolean waitForResponse) {
    sendData(data, 0, data.length, flags, type, subType, waitForResponse);
  }

  /**
   * Sends data over to the App. Note that this may or may not get intercepted by the Main Server (Device Server).
   *
   * @param data is the data to send
   * @param offset is the offset into {@code data}
   * @param length is the number of bytes from the {@code offset} in {@code data}
   * @param flags as defined by the protocol specification
   * @param type is the {@link ProfilerComponentIds} type
   * @param subType is a profiler component-dependent type
   * @param waitForResponse whether or not this data requires a response from the App
   */
  void sendData(@Nullable byte[] data,
                 int offset,
                 int length,
                 byte flags,
                 byte type,
                 short subType,
                 boolean waitForResponse) {
    myAppClientChannel.sendData(myProfilerClientListener, data, offset, length, flags, type, subType, waitForResponse);
  }
}
