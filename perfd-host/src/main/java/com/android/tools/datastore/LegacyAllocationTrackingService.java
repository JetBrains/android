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
package com.android.tools.datastore;

import com.android.tools.profiler.proto.MemoryProfiler.AllocatedClass;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.AllocationEvent;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

// TODO find a better place for Legacy* classes.
public class LegacyAllocationTrackingService {
  public interface LegacyAllocationTrackingCallback {
    void accept(List<AllocatedClass> classes, List<AllocationStack> stacks, List<AllocationEvent> events);
  }

  @NotNull
  private Supplier<LegacyAllocationTracker> myTrackerSupplier;

  private boolean myOngoingTracking = false;

  public LegacyAllocationTrackingService(@NotNull Supplier<LegacyAllocationTracker> trackerSupplier) {
    myTrackerSupplier = trackerSupplier;
  }

  private final ExecutorService myAllocationExecutorService =
    Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("LegacyAllocationTrackingService").build());

  public boolean trackAllocations(int processId,
                                  long time,
                                  boolean enabled,
                                  @NotNull LegacyAllocationTrackingCallback allocationConsumer) {
    // TODO ensure only legacy or non-instrumented devices go through this path
    LegacyAllocationTracker tracker = myTrackerSupplier.get();
    if (tracker == null) {
      return false;
    }

    if (enabled == myOngoingTracking) {
      return true;
    }

    myOngoingTracking = enabled;
    if (!enabled) {
      // TODO fix this so this method is reentrant/locks
      tracker.getAllocationTrackingDump(processId, myAllocationExecutorService, data -> {
        if (data != null) {
          LegacyAllocationConverter converter = tracker.parseDump(data);
          // timestamp of allocations is set to the end of allocation tracking
          allocationConsumer.accept(converter.getClassNames(), converter.getAllocationStacks(), converter.getAllocationEvents(time));
        }
      });
    }

    if (!tracker.setAllocationTrackingEnabled(processId, enabled)) {
      return false;
    }

    return true;
  }
}
