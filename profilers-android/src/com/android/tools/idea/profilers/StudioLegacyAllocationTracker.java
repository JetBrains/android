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
package com.android.tools.idea.profilers;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.datastore.poller.MemoryDataPoller;
import com.intellij.openapi.diagnostic.Logger;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * Since older releases of Android and uninstrumented apps will not have JVMTI allocation tracking, we therefore need to support the older
 * JDWP allocation tracking functionality.
 */
public class StudioLegacyAllocationTracker implements LegacyAllocationTracker {

  private static Logger getLogger() {
    return Logger.getInstance(MemoryDataPoller.class);
  }

  private IDevice myDevice;
  private int myPid;
  private boolean myOngoingTracking;

  public StudioLegacyAllocationTracker(@NotNull IDevice device, int processId) {
    myDevice = device;
    myPid = processId;
  }

  /**
   * @return true if the AllocationTracking state has successfully changed to the specified state. False otherwise.
   */
  @Override
  public boolean trackAllocations(boolean enabled,
                                  @Nullable Executor executor,
                                  @Nullable Consumer<byte[]> allocationConsumer) {
    Client client = getClient(myPid);
    if (client == null) {
      return false;
    }

    if (enabled == myOngoingTracking) {
      return false;
    }

    myOngoingTracking = enabled;
    if (!enabled) {
      assert executor != null;

      // TODO fix this so this method is reentrant/locks
      getAllocationTrackingDump(client, executor, data -> {
        assert allocationConsumer != null;
        allocationConsumer.accept(data);
      });
    }

    client.enableAllocationTracker(enabled);
    return true;
  }

  private void getAllocationTrackingDump(@NotNull Client callingClient, @NotNull Executor executor, @NotNull Consumer<byte[]> consumer) {
    AndroidDebugBridge.addClientChangeListener(new AndroidDebugBridge.IClientChangeListener() {
      @Override
      public void clientChanged(@NonNull Client client, int changeMask) {
        if (callingClient == client && (changeMask & Client.CHANGE_HEAP_ALLOCATIONS) != 0) {
          final byte[] data = client.getClientData().getAllocationsData();
          executor.execute(() -> consumer.accept(data));
          AndroidDebugBridge.removeClientChangeListener(this);
        }
      }
    });
    callingClient.requestAllocationDetails();
  }

  @Nullable
  private Client getClient(int processId) {
    if (!myDevice.isOnline()) {
      return null;
    }

    Client client = myDevice.getClient(myDevice.getClientName(processId));
    if (client == null) {
      getLogger().info("StudioLegacyAllocationTracker unable to find application with process Id: " + processId);
    }

    return client;
  }
}
