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
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.memory.adapters.CaptureObject.CaptureChangedListener.ChangedNode;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.ALLOC_COUNT;
import static com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.DEALLOC_COUNT;
import static com.android.tools.profilers.memory.adapters.CaptureObject.InstanceAttribute.ALLOCATION_TIME;
import static com.android.tools.profilers.memory.adapters.CaptureObject.InstanceAttribute.DEALLOCATION_TIME;

public class LiveAllocationCaptureObject implements CaptureObject {
  private static Logger getLogger() {
    return Logger.getInstance(LiveAllocationCaptureObject.class);
  }

  static final int DEFAULT_HEAP_ID = 0;
  static final String DEFAULT_HEAP_NAME = "default";
  static final long DEFAULT_CLASSLOADER_ID = -1;

  private final List<CaptureChangedListener> myListeners = new ArrayList<>(1);

  @VisibleForTesting
  final ExecutorService myExecutorService;
  private final ClassDb myClassDb;
  private final Map<ClassDb.ClassEntry, LiveAllocationInstanceObject> myClassMap;
  private final Map<Long, LiveAllocationInstanceObject> myInstanceMap;
  private final Map<Integer, AllocationStack> myCallstackMap;

  private final MemoryServiceBlockingStub myClient;
  private final Common.Session mySession;
  private final int myProcessId;
  private final long myCaptureStartTime;
  private final FeatureTracker myFeatureTracker;
  private final HeapSet myDefaultHeapSet;
  private final AspectObserver myAspectObserver;

  private long myContextEndTimeNs;
  private long myPreviousQueryStartTimeNs;
  private long myPreviousQueryEndTimeNs;

  private Range myQueryRange;

  private Future myCurrentTask;

  public LiveAllocationCaptureObject(@NotNull MemoryServiceBlockingStub client,
                                     @Nullable Common.Session session,
                                     int processId,
                                     long captureStartTime,
                                     @NotNull FeatureTracker featureTracker) {
    myExecutorService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("profiler-live-allocation").build());
    myClassDb = new ClassDb();
    myClassMap = new HashMap<>();
    myInstanceMap = new HashMap<>();
    myCallstackMap = new HashMap<>();

    myClient = client;
    mySession = session;
    myProcessId = processId;
    myCaptureStartTime = captureStartTime;
    myFeatureTracker = featureTracker;
    myDefaultHeapSet = new HeapSet(this, DEFAULT_HEAP_ID);
    myAspectObserver = new AspectObserver();

    myContextEndTimeNs = Long.MIN_VALUE;
    myPreviousQueryStartTimeNs = Long.MAX_VALUE;
    myPreviousQueryEndTimeNs = Long.MIN_VALUE;
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
    return ImmutableList.of(ClassifierAttribute.LABEL, ALLOC_COUNT, DEALLOC_COUNT);
  }

  @NotNull
  @Override
  public List<InstanceAttribute> getInstanceAttributes() {
    return ImmutableList.of(InstanceAttribute.LABEL, ALLOCATION_TIME, DEALLOCATION_TIME);
  }

  @NotNull
  @Override
  public Collection<HeapSet> getHeapSets() {
    return ImmutableList.of(myDefaultHeapSet);
  }

  @NotNull
  @Override
  public String getHeapName(int heapId) {
    return DEFAULT_HEAP_NAME;
  }

  @Nullable
  @Override
  public HeapSet getHeapSet(int heapId) {
    assert heapId == DEFAULT_HEAP_ID;
    return myDefaultHeapSet;
  }

  @NotNull
  @Override
  public Stream<InstanceObject> getInstances() {
    return myDefaultHeapSet.getInstancesStream();
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

  @Override
  public void addCaptureChangedListener(@NotNull CaptureChangedListener listener) {
    myListeners.add(listener);
  }

  /**
   * Load allocation data corresponding to the input time range. Note that load operation is expensive and happens on a different thread
   * (via myExecutorService). When loading is done, it informs the listener (e.g. UI) to update via the input joiner.
   */
  private void loadTimeRange(@NotNull Range queryRange, @NotNull Executor joiner) {
    long startTimeNs = TimeUnit.MICROSECONDS.toNanos((long)queryRange.getMin());
    long endTimeNs = TimeUnit.MICROSECONDS.toNanos((long)queryRange.getMax());
    // Special case for max-value endTimeNs, as that indicates querying the latest events.
    if (startTimeNs == myPreviousQueryStartTimeNs && endTimeNs == myPreviousQueryEndTimeNs && endTimeNs != Long.MAX_VALUE) {
      return;
    }

    try {
      if (myCurrentTask != null) {
        myCurrentTask.cancel(false);
      }
      myCurrentTask = myExecutorService.submit(() -> {
        long queryTimeStartNs;
        boolean refresh = false;
        if (startTimeNs == myPreviousQueryStartTimeNs && endTimeNs >= myPreviousQueryEndTimeNs) {
          // If we only expanded the range to the right, then do an optimization to only query the delta.
          // TODO add optimizations for range expansion/shrink
          queryTimeStartNs = myPreviousQueryEndTimeNs;
        }
        else {
          refresh = true;
          myPreviousQueryStartTimeNs = startTimeNs;
          queryTimeStartNs = myPreviousQueryStartTimeNs;
        }

        boolean clear = refresh;

        int totalChanged = 0;
        List<InstanceObject> instancesAdded = new ArrayList<>();
        List<InstanceObject> instancesFreed = new ArrayList<>();

        long newContextEndTime = Math.max(myContextEndTimeNs, endTimeNs);
        if (newContextEndTime > myContextEndTimeNs) {
          // If we clear, we need to grab all the classes again because we may be in a future range.
          AllocationContextsResponse contextsResponse = myClient.getAllocationContexts(
            AllocationContextsRequest.newBuilder().setProcessId(myProcessId).setSession(mySession)
              .setStartTime(myContextEndTimeNs).setEndTime(newContextEndTime).build());
          for (AllocatedClass klass : contextsResponse.getAllocatedClassesList()) {
            ClassDb.ClassEntry entry = myClassDb.registerClass(DEFAULT_CLASSLOADER_ID, klass.getClassName(), klass.getClassId());
            if (!myClassMap.containsKey(entry)) {
              // TODO remove creation of instance object through the CLASS_DATA path. This should be handled by ALLOC_DATA.
              // TODO pass in proper allocation time once this is handled via ALLOC_DATA.
              LiveAllocationInstanceObject instance =
                new LiveAllocationInstanceObject(entry, null, myCaptureStartTime, MemoryObject.INVALID_VALUE, null);
              myClassMap.put(entry, instance);
              // TODO figure out what to do with java.lang.Class instance objects
              //instancesAdded.add(instance);
              totalChanged++;
            }
          }

          contextsResponse.getAllocationStacksList().forEach(callStack -> myCallstackMap.putIfAbsent(callStack.getStackId(), callStack));

          myContextEndTimeNs = Math.max(myContextEndTimeNs, contextsResponse.getTimestamp());
        }

        if (clear) {
          // We also need to clear the instances map if we're clearing the range.
          myInstanceMap.clear();
        }

        BatchAllocationSample sampleResponse =
          myClient.getAllocations(AllocationSnapshotRequest.newBuilder().setProcessId(myProcessId).setSession(mySession)
                                    .setStartTime(queryTimeStartNs).setEndTime(endTimeNs).build());

        myPreviousQueryEndTimeNs = Math.max(myPreviousQueryEndTimeNs, sampleResponse.getTimestamp());
        for (AllocationEvent event : sampleResponse.getEventsList()) {
          if (event.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
            AllocationEvent.Allocation allocation = event.getAllocData();
            ClassDb.ClassEntry entry = myClassDb.getEntry(allocation.getClassTag());
            assert myClassMap.containsKey(entry);

            AllocationStack callstack = null;
            if (allocation.getStackId() != 0) {
              assert myCallstackMap.containsKey(allocation.getStackId());
              callstack = myCallstackMap.get(allocation.getStackId());
            }

            LiveAllocationInstanceObject instance =
              new LiveAllocationInstanceObject(entry, myClassMap.get(entry), event.getTimestamp(), allocation.getSize(), callstack);
            assert !myInstanceMap.containsKey(allocation.getTag());
            myInstanceMap.put(allocation.getTag(), instance);
            instancesAdded.add(instance);
            totalChanged++;
          }
          else if (event.getEventCase() == AllocationEvent.EventCase.FREE_DATA) {
            AllocationEvent.Deallocation deallocation = event.getFreeData();
            LiveAllocationInstanceObject instance = myInstanceMap.computeIfAbsent(deallocation.getTag(), tag -> {
              ClassDb.ClassEntry entry = myClassDb.getEntry(deallocation.getClassTag());
              assert myClassMap.containsKey(entry);
              return new LiveAllocationInstanceObject(entry, myClassMap.get(entry), deallocation.getAllocTime(), deallocation.getSize(),
                                                      null);
            });
            instance.setDeallocTime(event.getTimestamp());
            instancesFreed.add(instance);
            totalChanged++;
          }
          else {
            assert false;
          }
        }

        if (!myListeners.isEmpty() && (totalChanged > 0 || clear)) {
          joiner.execute(() -> {
            if (clear) {
              myDefaultHeapSet.clearClassifierSets();
            }

            ChangedNode changedNode = new ChangedNode(myDefaultHeapSet);
            List<ClassifierSet> path = new ArrayList<>();
            instancesAdded.forEach(instance -> {
              path.clear();
              myDefaultHeapSet.addInstanceObject(instance, path);
              changedNode.addPath(path);
            });
            instancesFreed.forEach(instance -> {
              path.clear();
              myDefaultHeapSet.freeInstanceObject(instance, path);
              changedNode.addPath(path);
            });
            myListeners.forEach(listener -> listener.heapChanged(changedNode, clear));
          });
        }
        return null;
      });
    }
    catch (RejectedExecutionException e) {
      getLogger().debug(e);
    }
  }
}
