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
package com.android.tools.profilers.memory.adapters;

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.android.tools.profilers.memory.MemoryProfilerAspect;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.stacktrace.ThreadId;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.*;
import static com.android.tools.profilers.memory.adapters.CaptureObject.InstanceAttribute.ALLOCATION_TIME;
import static com.android.tools.profilers.memory.adapters.CaptureObject.InstanceAttribute.DEALLOCATION_TIME;

public class LiveAllocationCaptureObject implements CaptureObject {
  private static Logger getLogger() {
    return Logger.getInstance(LiveAllocationCaptureObject.class);
  }

  static final String DEFAULT_HEAP_NAME = "default";
  static final String IMAGE_HEAP_NAME = "image";
  static final String ZYGOTE_HEAP_NAME = "zygote";
  static final String APP_HEAP_NAME = "app";

  @Nullable private MemoryProfilerStage myStage;

  @VisibleForTesting final ExecutorService myExecutorService;
  private final ClassDb myClassDb;
  private final Map<ClassDb.ClassEntry, LiveAllocationInstanceObject> myClassMap;
  private final TIntObjectHashMap<LiveAllocationInstanceObject> myInstanceMap;
  private final TIntObjectHashMap<AllocationStack> myCallstackMap;
  private final TIntObjectHashMap<ThreadId> myThreadIdMap;

  private final MemoryServiceBlockingStub myClient;
  private final Common.Session mySession;
  private final int myProcessId;
  private final long myCaptureStartTime;
  private final List<HeapSet> myHeapSets;
  private final AspectObserver myAspectObserver;

  private long myEventsEndTimeNs;
  private long myContextEndTimeNs;
  private long myPreviousQueryStartTimeNs;
  private long myPreviousQueryEndTimeNs;

  private Range myQueryRange;

  private Future myCurrentTask;

  public LiveAllocationCaptureObject(@NotNull MemoryServiceBlockingStub client,
                                     @Nullable Common.Session session,
                                     int processId,
                                     long captureStartTime,
                                     @Nullable ExecutorService loadService,
                                     @Nullable MemoryProfilerStage stage) {
    if (loadService == null) {
      myExecutorService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("profiler-live-allocation").build());
    }
    else {
      myExecutorService = loadService;
    }

    myClassDb = new ClassDb();
    myClassMap = new HashMap<>();
    myInstanceMap = new TIntObjectHashMap<>();
    myCallstackMap = new TIntObjectHashMap<>();
    myThreadIdMap = new TIntObjectHashMap<>();

    myClient = client;
    mySession = session;
    myProcessId = processId;
    myCaptureStartTime = captureStartTime;
    myAspectObserver = new AspectObserver();
    myStage = stage;

    myHeapSets = Arrays.asList(new HeapSet(this, DEFAULT_HEAP_NAME, 0),  // default
                               new HeapSet(this, IMAGE_HEAP_NAME, 1),  // image
                               new HeapSet(this, ZYGOTE_HEAP_NAME, 2),  // zygote
                               new HeapSet(this, APP_HEAP_NAME, 3)); // app

    myEventsEndTimeNs = Long.MIN_VALUE;
    myContextEndTimeNs = Long.MIN_VALUE;
    myPreviousQueryStartTimeNs = Long.MIN_VALUE;
    myPreviousQueryEndTimeNs = Long.MIN_VALUE;
  }

  @Override
  @NotNull
  public Common.Session getSession() {
    return mySession;
  }

  @Override
  public int getProcessId() {
    return myProcessId;
  }

  @Override
  @NotNull
  public MemoryServiceGrpc.MemoryServiceBlockingStub getClient() {
    return myClient;
  }

  @NotNull
  @Override
  public String getName() {
    return "Live Allocation";
  }

  @Nullable
  @Override
  public String getExportableExtension() {
    return null;
  }

  @Override
  public void saveToFile(@NotNull OutputStream outputStream) throws IOException {
    throw new NotImplementedException();
  }

  @NotNull
  @Override
  public List<ClassifierAttribute> getClassifierAttributes() {
    return ImmutableList.of(ClassifierAttribute.LABEL, ALLOC_COUNT, DEALLOC_COUNT, SHALLOW_SIZE);
  }

  @NotNull
  @Override
  public List<InstanceAttribute> getInstanceAttributes() {
    return ImmutableList.of(InstanceAttribute.LABEL, ALLOCATION_TIME, DEALLOCATION_TIME);
  }

  @NotNull
  @Override
  public Collection<HeapSet> getHeapSets() {
    return myHeapSets;
  }

  @Override
  @Nullable
  public HeapSet getHeapSet(int heapId) {
    // Android Runtime guarantees continuity of heap IDs starting from 0. There are only four at the moment (default, image, zygote, app)
    // and are unlikely to change.
    assert heapId >= 0 && heapId <= 3;
    return myHeapSets.get(heapId);
  }

  @NotNull
  @Override
  public Stream<InstanceObject> getInstances() {
    return getHeapSets().stream().map(ClassifierSet::getInstancesStream).flatMap(Function.identity());
  }

  @Override
  public long getStartTimeNs() {
    return myCaptureStartTime;
  }

  @Override
  public long getEndTimeNs() {
    return Long.MAX_VALUE;
  }

  @Override
  public boolean load(@Nullable Range queryRange, @Nullable Executor queryJoiner) {
    assert queryRange != null;
    assert queryJoiner != null;
    myQueryRange = queryRange;
    // TODO There's a problem with this, as the datastore is effectively a real-time system.
    // TODO In other words, when we query for some range, we may not get back entries that are still being inserted, and we don't re-query.
    myQueryRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, () -> loadTimeRange(myQueryRange, queryJoiner));

    // Load the initial data within queryRange.
    loadTimeRange(myQueryRange, queryJoiner);

    return true;
  }

  @Override
  public boolean isDoneLoading() {
    return true;
  }

  @Override
  public boolean isError() {
    return false;
  }

  @Override
  public void unload() {
    myQueryRange.removeDependencies(myAspectObserver);
    myExecutorService.shutdownNow();
  }

  // Update myContextEndTimeNs and Callstack information
  private void updateAllocationContexts(long endTimeNs) {
    if (myContextEndTimeNs >= endTimeNs) {
      return;
    }
    AllocationContextsResponse contextsResponse = myClient.getAllocationContexts(
      AllocationContextsRequest.newBuilder().setProcessId(myProcessId).setSession(mySession)
        .setStartTime(myContextEndTimeNs).setEndTime(endTimeNs).build());

    for (AllocatedClass klass : contextsResponse.getAllocatedClassesList()) {
      ClassDb.ClassEntry entry = myClassDb.registerClass(DEFAULT_CLASSLOADER_ID, klass.getClassName(), klass.getClassId());
      if (!myClassMap.containsKey(entry)) {
        // TODO remove creation of instance object through the CLASS_DATA path. This should be handled by ALLOC_DATA.
        // TODO pass in proper allocation time once this is handled via ALLOC_DATA.
        LiveAllocationInstanceObject instance =
          new LiveAllocationInstanceObject(this, entry, null, null, null, MemoryObject.INVALID_VALUE, MemoryObject.INVALID_VALUE);
        instance.setAllocationTime(myCaptureStartTime);
        myClassMap.put(entry, instance);
        // TODO figure out what to do with java.lang.Class instance objects
      }
    }
    contextsResponse.getAllocationStacksList().forEach(callStack -> {
      if (!myCallstackMap.contains(callStack.getStackId())) {
        myCallstackMap.put(callStack.getStackId(), callStack);
      }
    });
    contextsResponse.getAllocationThreadsList().forEach(thread -> {
      if (!myThreadIdMap.contains(thread.getThreadId())) {
        myThreadIdMap.put(thread.getThreadId(), new ThreadId(thread.getThreadName()));
      }
    });
    myContextEndTimeNs = Math.max(myContextEndTimeNs, contextsResponse.getTimestamp());
  }

  /**
   * Load allocation data corresponding to the input time range. Note that load operation is expensive and happens on a different thread
   * (via myExecutorService). When loading is done, it informs the listener (e.g. UI) to update via the input joiner.
   */
  private void loadTimeRange(@NotNull Range queryRange, @NotNull Executor joiner) {
    try {
      if (myCurrentTask != null) {
        myCurrentTask.cancel(false);
      }
      myCurrentTask = myExecutorService.submit(() -> {
        long newStartTimeNs = TimeUnit.MICROSECONDS.toNanos((long)queryRange.getMin());
        long newEndTimeNs = TimeUnit.MICROSECONDS.toNanos((long)queryRange.getMax());
        // Special case for max-value newEndTimeNs, as that indicates querying the latest events.
        if (newStartTimeNs == myPreviousQueryStartTimeNs && newEndTimeNs == myPreviousQueryEndTimeNs && newEndTimeNs != Long.MAX_VALUE) {
          return null;
        }

        joiner.execute(() -> myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_HEAP_UPDATING));
        updateAllocationContexts(newEndTimeNs);

        // myEventEndTimeNs represents latest timestamp we have event data
        // If newEndTimeNs > myEventEndTimeNs + 1, we set newEndTimeNs as myEventEndTimeNs + 1
        // We +1 because current range is left close and right open
        if (newEndTimeNs > myEventsEndTimeNs + 1) {
          BatchAllocationSample sampleResponse =
            myClient.getAllocations(AllocationSnapshotRequest.newBuilder().setProcessId(myProcessId).setSession(mySession)
                                      .setStartTime(myEventsEndTimeNs + 1).setEndTime(newEndTimeNs).build());

          myEventsEndTimeNs = Math.max(myEventsEndTimeNs, sampleResponse.getTimestamp());
          if (newEndTimeNs > myEventsEndTimeNs + 1) {
            newEndTimeNs = myEventsEndTimeNs + 1;
            newStartTimeNs = Math.min(newStartTimeNs, newEndTimeNs);
          }
        }

        // Split the two ranges into three segments by sorting their end points and analyzing segments with adjacent points
        long[] timestamps = {myPreviousQueryStartTimeNs, myPreviousQueryEndTimeNs, newStartTimeNs, newEndTimeNs};
        // Clear myDefaultHeapSet If previous range does not intersect with the new one
        boolean clear = myPreviousQueryEndTimeNs <= newStartTimeNs || newEndTimeNs <= myPreviousQueryStartTimeNs;
        if (clear) {
          long[] newTimeStamps = {newStartTimeNs, newEndTimeNs};
          timestamps = newTimeStamps;
          myInstanceMap.clear();
        }

        Arrays.sort(timestamps);
        List<InstanceObject> setAllocationList = new ArrayList<>();
        List<InstanceObject> resetAllocationList = new ArrayList<>();
        List<InstanceObject> setDeallocationList = new ArrayList<>();
        List<InstanceObject> resetDeallocationList = new ArrayList<>();

        // For each segment, if it is only within previous range, we remove events in this segment
        // If it is only within current range, we add events in this segments
        // Otherwise, we do nothing since we already processed events in this segment
        for (int stampNumber = 0; stampNumber + 1 < timestamps.length; ++stampNumber) {
          long startTimeNs = timestamps[stampNumber];
          long endTimeNs = timestamps[stampNumber + 1];
          if (startTimeNs == endTimeNs) {
            continue;
          }

          boolean insidePreviousRange = startTimeNs >= myPreviousQueryStartTimeNs && endTimeNs <= myPreviousQueryEndTimeNs;
          boolean insideCurrentRange = startTimeNs >= newStartTimeNs && endTimeNs <= newEndTimeNs;

          if (insidePreviousRange == insideCurrentRange) {
            continue;
          }

          BatchAllocationSample sampleResponse =
            myClient.getAllocations(AllocationSnapshotRequest.newBuilder().setProcessId(myProcessId).setSession(mySession)
                                      .setStartTime(startTimeNs).setEndTime(endTimeNs).build());

          for (AllocationEvent event : sampleResponse.getEventsList()) {
            if (event.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
              AllocationEvent.Allocation allocation = event.getAllocData();
              LiveAllocationInstanceObject instance =
                getOrCreateInstanceObject(allocation.getTag(), allocation.getClassTag(), allocation.getStackId(), allocation.getThreadId(),
                                          allocation.getSize(), allocation.getHeapId());
              if (insideCurrentRange) {
                instance.setAllocationTime(event.getTimestamp());
                setAllocationList.add(instance);
              }
              else {
                instance.setAllocationTime(Long.MIN_VALUE);
                resetAllocationList.add(instance);
              }
            }
            else if (event.getEventCase() == AllocationEvent.EventCase.FREE_DATA) {
              AllocationEvent.Deallocation deallocation = event.getFreeData();
              LiveAllocationInstanceObject instance =
                getOrCreateInstanceObject(deallocation.getTag(), deallocation.getClassTag(), deallocation.getStackId(),
                                          deallocation.getThreadId(), deallocation.getSize(), deallocation.getHeapId());
              if (insideCurrentRange) {
                instance.setDeallocTime(event.getTimestamp());
                setDeallocationList.add(instance);
              }
              else {
                instance.setDeallocTime(Long.MAX_VALUE);
                resetDeallocationList.add(instance);
              }
            }
            else {
              assert false;
            }
          }
        }

        myPreviousQueryStartTimeNs = newStartTimeNs;
        myPreviousQueryEndTimeNs = newEndTimeNs;

        joiner.execute(() -> {
          myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_HEAP_UPDATED);
          if (clear ||
              setAllocationList.size() + setDeallocationList.size() + resetAllocationList.size() + resetDeallocationList.size() > 0) {
            if (clear) {
              myHeapSets.forEach(heap -> heap.clearClassifierSets());
              if (myStage.getSelectedClassSet() != null) {
                myStage.selectClassSet(ClassSet.EMPTY_SET);
              }
            }
            setAllocationList.forEach(instance -> {
              myHeapSets.get(instance.getHeapId()).addInstanceObject(instance);
            });
            setDeallocationList.forEach(instance -> {
              myHeapSets.get(instance.getHeapId()).freeInstanceObject(instance);
            });
            resetAllocationList.forEach(instance -> {
              myHeapSets.get(instance.getHeapId()).removeAddingInstanceObject(instance);
            });
            resetDeallocationList.forEach(instance -> {
              myHeapSets.get(instance.getHeapId()).removeFreeingInstanceObject(instance);
            });
            myStage.refreshSelectedHeap();
          }
        });
        return null;
      });
    }
    catch (RejectedExecutionException e) {
      getLogger().debug(e);
    }
  }

  @NotNull
  private LiveAllocationInstanceObject getOrCreateInstanceObject(int tag, int classTag, int stackId, int threadId, long size, int heapId) {
    LiveAllocationInstanceObject instance = myInstanceMap.get(tag);
    if (instance == null) {
      ClassDb.ClassEntry entry = myClassDb.getEntry(classTag);
      assert myClassMap.containsKey(entry);
      AllocationStack callstack = null;
      if (stackId != 0) {
        assert myCallstackMap.containsKey(stackId);
        callstack = myCallstackMap.get(stackId);
      }
      ThreadId thread = null;
      if (threadId != 0) {
        assert myThreadIdMap.containsKey(threadId);
        thread = myThreadIdMap.get(threadId);
      }
      instance = new LiveAllocationInstanceObject(this, entry, myClassMap.get(entry), thread, callstack, size, heapId);
      myInstanceMap.put(tag, instance);
    }

    return instance;
  }
}
