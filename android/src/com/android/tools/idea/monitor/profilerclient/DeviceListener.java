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

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Main listener implementation for profiler.
 * WIP: Needs more fleshed out API for the listener. Component-specific calls should be made generic.
 */
public class DeviceListener implements ProfilerClientListener {
  private static final Logger LOG = Logger.getInstance(DeviceListener.class.getCanonicalName());

  private static final short NETWORKING_DATA_SUBTYPE = (short)0;

  @Override
  public void onMessage(@NotNull MessageHeader messageHeader, @NotNull ByteBuffer inputBuffer) throws IOException {
    // TODO install default handlers
    switch (messageHeader.type) {
      case ProfilerComponentIds.NETWORKING:
        processNetworkMessage(messageHeader, inputBuffer);
        break;
      case ProfilerComponentIds.SERVER:
        throw new RuntimeException("Should not handle server messages here");
      default:
        // Process messages to different client components.
        break;
    }
  }

  private static void processNetworkMessage(@NotNull MessageHeader header, @NotNull ByteBuffer input) throws IOException {
    switch (header.subType) {
      case NETWORKING_DATA_SUBTYPE:
        long time = input.getLong();
        long txBytes = input.getLong();
        long rxBytes = input.getLong();
        short networkType = input.getShort();
        byte highPowerState = input.get();
        break;
      default:
        LOG.error(String.format("Unexpected network subtype %1$d", header.subType));
        break;
    }
  }
}
