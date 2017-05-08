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

import com.android.tools.adtui.model.DurationData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationEvent;
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

  private final ExecutorService myExecutorService;
  private final ClassDb myClassDb;
  private final Map<ClassDb.ClassEntry, LiveAllocationInstanceObject> myClassMap;
  private final Map<Long, LiveAllocationInstanceObject> myInstanceMap;

  private final MemoryServiceBlockingStub myClient;
  private final Common.Session mySession;
  private final int myProcessId;
  private final long myCaptureTime;
  private final FeatureTracker myFeatureTracker;
  private final HeapSet myDefaultHeapSet;

  private long myContextEndTimeNs;
  private long myPreviousQueryStartTimeNs;
  private long myPreviousQueryEndTimeNs;

  public LiveAllocationCaptureObject(@NotNull MemoryServiceBlockingStub client,
                                     @Nullable Common.Session session,
                                     int processId,
                                     long captureTime,
                                     @NotNull FeatureTracker featureTracker) {
    myExecutorService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("profiler-live-allocation").build());
    myClassDb = new ClassDb();
    myClassMap = new HashMap<>();
    myInstanceMap = new HashMap<>();

    myClient = client;
    mySession = session;
    myProcessId = processId;
    myCaptureTime = captureTime;
    myFeatureTracker = featureTracker;
    myDefaultHeapSet = new HeapSet(this, DEFAULT_HEAP_ID);

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
    return myCaptureTime;
  }

  @Override
  public long getEndTimeNs() {
    return Long.MAX_VALUE;
  }

  @Override
  public boolean load() {
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
    myExecutorService.shutdownNow();
  }

  @Override
  public void addCaptureChangedListener(@NotNull CaptureChangedListener listener) {
    myListeners.add(listener);
  }

  public void loadTimeRange(long startTimeNs, long endTimeNs, @NotNull Executor joiner) {
    if (startTimeNs == myPreviousQueryStartTimeNs && endTimeNs == myPreviousQueryEndTimeNs && endTimeNs != Long.MAX_VALUE) {
      return;
    }

    long queryTimeStartNs;
    boolean refresh = false;
    if (startTimeNs == myPreviousQueryStartTimeNs && endTimeNs >= myPreviousQueryEndTimeNs) {
      // If we only expanded the range, then do an optimization to only query the delta.
      queryTimeStartNs = myPreviousQueryEndTimeNs;
      myPreviousQueryEndTimeNs = endTimeNs;
    }
    else {
      refresh = true;
      myPreviousQueryStartTimeNs = startTimeNs;
      myPreviousQueryEndTimeNs = endTimeNs;
      queryTimeStartNs = myPreviousQueryStartTimeNs;
    }

    boolean clear = refresh;
    try {
      myExecutorService.submit(() -> {
        int totalChanged = 0; // TODO remove this, as this is a hack (because we get CLASS_DATA back multiple times)
        List<InstanceObject> instancesAdded = new ArrayList<>();
        List<InstanceObject> instancesFreed = new ArrayList<>();


        assert myPreviousQueryEndTimeNs != DurationData.UNSPECIFIED_DURATION;
        long newContextEndTime = Math.max(myContextEndTimeNs, myPreviousQueryEndTimeNs);
        if (newContextEndTime > myContextEndTimeNs) {
          // If we clear, we need to grab all the classes again because we may be in a future range.
          MemoryProfiler.BatchAllocationSample sampleResponse = myClient.getAllocationContexts(
            MemoryProfiler.AllocationSnapshotRequest.newBuilder().setProcessId(myProcessId).setSession(mySession)
              .setStartTime(myContextEndTimeNs).setEndTime(newContextEndTime).build());
          for (AllocationEvent event : sampleResponse.getEventsList()) {
            if (event.getEventCase() == AllocationEvent.EventCase.CLASS_DATA) {
              AllocationEvent.Klass classData = event.getClassData();
              ClassDb.ClassEntry entry = myClassDb.registerClass(DEFAULT_CLASSLOADER_ID, classData.getName(), classData.getTag());
              if (!myClassMap.containsKey(entry)) {
                // TODO remove creation of instance object through the CLASS_DATA path. This should be handled by ALLOC_DATA.
                LiveAllocationInstanceObject instance = new LiveAllocationInstanceObject(entry, null, event.getTimestamp(), MemoryObject.INVALID_VALUE);
                myClassMap.put(entry, instance);
                // TODO figure out what to do with java.lang.Class instance objects
                //instancesAdded.add(instance);
                totalChanged++;
              }
            }
          }
          myContextEndTimeNs = Math.max(myContextEndTimeNs, sampleResponse.getTimestamp());
        }

        if (clear) {
          // We also need to clear the instances map if we're clearing the range.
          myInstanceMap.clear();
        }

        MemoryProfiler.BatchAllocationSample sampleResponse = myClient.getAllocations(
          MemoryProfiler.AllocationSnapshotRequest.newBuilder().setProcessId(myProcessId).setSession(mySession)
            .setCaptureTime(myCaptureTime).setStartTime(queryTimeStartNs).setEndTime(myPreviousQueryEndTimeNs).build());

        for (AllocationEvent event : sampleResponse.getEventsList()) {
          if (event.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
            AllocationEvent.Allocation allocation = event.getAllocData();
            ClassDb.ClassEntry entry = myClassDb.getEntry(allocation.getClassTag());
            assert myClassMap.containsKey(entry);
            LiveAllocationInstanceObject instance = new LiveAllocationInstanceObject(entry, myClassMap.get(entry), event.getTimestamp(), allocation.getSize());
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
              return new LiveAllocationInstanceObject(entry, myClassMap.get(entry), deallocation.getAllocTime(), deallocation.getSize());
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
          myListeners.forEach(listener -> joiner.execute(() -> {
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

            listener.heapChanged(changedNode, clear);
          }));
        }
        return null;
      });
    }
    catch (RejectedExecutionException e) {
      getLogger().debug(e);
    }
  }
}
