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
import com.android.ddmlib.*;
import com.android.tools.datastore.poller.MemoryDataPoller;
import com.android.tools.profilers.memory.LegacyAllocationConverter;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Since older releases of Android and uninstrumented apps will not have JVMTI allocation tracking, we therefore need to support the older
 * JDWP allocation tracking functionality.
 */
public class StudioLegacyAllocationTracker implements LegacyAllocationTracker {

  private static Logger getLogger() {
    return Logger.getInstance(MemoryDataPoller.class);
  }

  private IDevice myDevice;
  private Client myClient;
  private final LegacyAllocationConverter myConverter = new LegacyAllocationConverter();
  private boolean myOngoingTracking;

  public StudioLegacyAllocationTracker(@NotNull IDevice device, int processId) {
    myDevice = device;
    myClient = getClient(processId);
    if (myClient == null) {
      getLogger().info("StudioLegacyAllocationTracker unable to find application with process Id: " + processId);
    }
  }

  /**
   * @return true if the AllocationTracking state has successfully changed to the specified state. False otherwise.
   */
  @Override
  public boolean trackAllocations(long startTime,
                                  long endTime,
                                  boolean enabled,
                                  @Nullable Executor executor,
                                  @Nullable LegacyAllocationTrackingCallback allocationConsumer) {
    if (myClient == null) {
      return false;
    }

    if (enabled == myOngoingTracking) {
      return false;
    }

    myOngoingTracking = enabled;
    if (!enabled) {
      assert executor != null;

      // TODO fix this so this method is reentrant/locks
      getAllocationTrackingDump(executor, data -> {
        assert allocationConsumer != null;
        if (data == null) {
          allocationConsumer.accept(null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
        else {
          myConverter.parseDump(data);
          // timestamp of allocations is set to the end of allocation tracking
          allocationConsumer.accept(
            data, myConverter.getClassNames(), myConverter.getAllocationStacks(), myConverter.getAllocationEvents(startTime, endTime));
        }
      });
    }

    myClient.enableAllocationTracker(enabled);
    return true;
  }

  private void getAllocationTrackingDump(@NotNull Executor executor, @NotNull Consumer<byte[]> consumer) {
    AndroidDebugBridge.addClientChangeListener(new AndroidDebugBridge.IClientChangeListener() {
      @Override
      public void clientChanged(@NonNull Client client, int changeMask) {
        if (myClient == client && (changeMask & Client.CHANGE_HEAP_ALLOCATIONS) != 0) {
          final byte[] data = client.getClientData().getAllocationsData();
          executor.execute(() -> consumer.accept(data));
          AndroidDebugBridge.removeClientChangeListener(this);
        }
      }
    });
    myClient.requestAllocationDetails();
  }

  @Nullable
  private Client getClient(int processId) {
    if (!myDevice.isOnline()) {
      return null;
    }

    return myDevice.getClient(myDevice.getClientName(processId));
  }
}
