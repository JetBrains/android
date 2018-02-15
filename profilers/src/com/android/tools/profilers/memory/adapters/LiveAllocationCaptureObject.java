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
import com.android.tools.profiler.proto.MemoryProfiler;
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
import gnu.trove.TLongObjectHashMap;
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
  static final String JNI_HEAP_NAME = "JNI";
  // ID for JNI pseudo-heap, it should not overlap with real Android heaps
  public static final int JNI_HEAP_ID = 4;

  @Nullable private MemoryProfilerStage myStage;

  @VisibleForTesting final ExecutorService myExecutorService;
  private final ClassDb myClassDb;
  private final Map<ClassDb.ClassEntry, LiveAllocationInstanceObject> myClassMap;
  private final TIntObjectHashMap<LiveAllocationInstanceObject> myInstanceMap;
  private final TIntObjectHashMap<AllocationStack> myCallstackMap;
  private final TIntObjectHashMap<ThreadId> myThreadIdMap;
  private final TLongObjectHashMap<StackFrameInfoResponse> myFrameInfoResponseMap;

  private final MemoryServiceBlockingStub myClient;
  private final Common.Session mySession;
  private final long myCaptureStartTime;
  private final List<HeapSet> myHeapSets;
  private final AspectObserver myAspectObserver;
  private final boolean myEnableJniRefsTracking;

  private long myEventsEndTimeNs;
  private long myContextEndTimeNs;
  private long myPreviousQueryStartTimeNs;
  private long myPreviousQueryEndTimeNs;

  private Range myQueryRange;

  private Future myCurrentTask;

  public LiveAllocationCaptureObject(@NotNull MemoryServiceBlockingStub client,
                                     @NotNull Common.Session session,
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
    myFrameInfoResponseMap = new TLongObjectHashMap<>();

    myClient = client;
    mySession = session;
    myCaptureStartTime = captureStartTime;
    myAspectObserver = new AspectObserver();
    myStage = stage;

    myHeapSets = new ArrayList<>(Arrays.asList(
      new HeapSet(this, DEFAULT_HEAP_NAME, 0),  // default
      new HeapSet(this, IMAGE_HEAP_NAME, 1),  // image
      new HeapSet(this, ZYGOTE_HEAP_NAME, 2),  // zygote
      new HeapSet(this, APP_HEAP_NAME, 3))); // app

    myEnableJniRefsTracking = stage.getStudioProfilers().getIdeServices().getFeatureConfig().isJniReferenceTrackingEnabled();
    if (myEnableJniRefsTracking) {
      myHeapSets.add(new HeapSet(this, JNI_HEAP_NAME, JNI_HEAP_ID));
    }

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
    if (myStage.getStudioProfilers().getIdeServices().getFeatureConfig().isMemorySnapshotEnabled()) {
      return ImmutableList.of(LABEL, ALLOCATIONS, DEALLOCATIONS, TOTAL_COUNT, SHALLOW_SIZE);
    }
    else {
      return ImmutableList.of(LABEL, ALLOCATIONS, DEALLOCATIONS, SHALLOW_SIZE);
    }
  }

  @NotNull
  @Override
  public List<InstanceAttribute> getInstanceAttributes() {
    return ImmutableList.of(InstanceAttribute.LABEL, ALLOCATION_TIME, DEALLOCATION_TIME);
  }

  @NotNull
  @Override
  public Collection<HeapSet> getHeapSets() {
    // Exclude DEFAULT_HEAP since it shouldn't show up in use in devices that support live allocation tracking.
    if (myHeapSets.get(0).getInstancesCount() > 0) {
      // But handle the unexpected, just in case....
      return myHeapSets;
    }
    return myHeapSets.subList(1, myHeapSets.size());
  }

  @Override
  @Nullable
  public HeapSet getHeapSet(int heapId) {
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

  @Nullable
  @Override
  public MemoryProfiler.StackFrameInfoResponse getStackFrameInfoResponse(long methodId) {
    StackFrameInfoResponse frameInfo = myFrameInfoResponseMap.get(methodId);
    if (frameInfo == null) {
      frameInfo = getClient().getStackFrameInfo(StackFrameInfoRequest.newBuilder().setSession(getSession()).setMethodId(methodId).build());
      myFrameInfoResponseMap.put(methodId, frameInfo);
    }

    return frameInfo;
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
      AllocationContextsRequest.newBuilder().setSession(mySession).setStartTime(myContextEndTimeNs).setEndTime(endTimeNs).build());

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
          LatestAllocationTimeResponse timeResponse =
            myClient.getLatestAllocationTime(LatestAllocationTimeRequest.newBuilder().setSession(mySession).build());
          myEventsEndTimeNs = Math.max(myEventsEndTimeNs, timeResponse.getTimestamp());
          if (newEndTimeNs > myEventsEndTimeNs + 1) {
            newEndTimeNs = myEventsEndTimeNs + 1;
            newStartTimeNs = Math.min(newStartTimeNs, newEndTimeNs);
          }
        }

        // Snapshots data
        List<InstanceObject> snapshotList = new ArrayList<>();
        List<InstanceObject> resetSnapshotList = new ArrayList<>();
        // Delta data
        List<InstanceObject> deltaAllocationList = new ArrayList<>();
        List<InstanceObject> resetDeltaAllocationList = new ArrayList<>();
        List<InstanceObject> deltaFreeList = new ArrayList<>();
        List<InstanceObject> resetDeltaFreeList = new ArrayList<>();

        // Clear and recreate the instance/heap sets if previous range does not intersect with the new one
        boolean clear = myPreviousQueryEndTimeNs <= newStartTimeNs || newEndTimeNs <= myPreviousQueryStartTimeNs;
        if (clear) {
          myInstanceMap.clear();
          // If we are resetting, then first establish the object snapshot at the query range's start point.
          queryJavaInstanceSnapshot(newStartTimeNs, snapshotList);
          queryJniReferencesSnapshot(newStartTimeNs, snapshotList);

          // Update the delta allocations and deallocations within the selection range on the snapshot.
          queryJavaInstanceDelta(newStartTimeNs, newEndTimeNs, deltaAllocationList, deltaFreeList, false);
          queryJniReferencesDelta(newStartTimeNs, newEndTimeNs, deltaAllocationList, deltaFreeList, false);
        }
        else {
          // Compute selection left differences.
          List<InstanceObject> leftAllocations = new ArrayList<>();
          List<InstanceObject> leftDeallocations = new ArrayList<>();
          if (newStartTimeNs < myPreviousQueryStartTimeNs) {
            // Selection's min shifts left
            queryJavaInstanceDelta(newStartTimeNs, myPreviousQueryStartTimeNs, leftAllocations, leftDeallocations, false);
            queryJniReferencesDelta(newStartTimeNs, myPreviousQueryStartTimeNs, leftAllocations, leftDeallocations, false);
            // add data within this range to the deltas
            deltaAllocationList.addAll(leftAllocations);
            deltaFreeList.addAll(leftDeallocations);
            // Allocations happen after selection min: remove instance from snapshot
            resetSnapshotList.addAll(leftAllocations);
            // Deallocations happen after selection min: add instance to snapshot
            snapshotList.addAll(leftDeallocations);
          }
          else if (newStartTimeNs > myPreviousQueryStartTimeNs) {
            // Selection's min shifts right
            queryJavaInstanceDelta(myPreviousQueryStartTimeNs, newStartTimeNs, leftAllocations, leftDeallocations, true);
            queryJniReferencesDelta(myPreviousQueryStartTimeNs, newStartTimeNs, leftAllocations, leftDeallocations, true);
            // Remove data within this range from the deltas
            resetDeltaAllocationList.addAll(leftAllocations);
            resetDeltaFreeList.addAll(leftDeallocations);
            // Allocations happen before the selection's min: add instance to snapshot
            snapshotList.addAll(leftAllocations);
            // Deallocations before the selection's min: remove instance from snapshot
            resetSnapshotList.addAll(leftDeallocations);
          }

          // Compute selection right differences.
          List<InstanceObject> rightAllocations = new ArrayList<>();
          List<InstanceObject> rightDeallocations = new ArrayList<>();
          if (newEndTimeNs < myPreviousQueryEndTimeNs) {
            // Selection's max shifts left: remove data within this range from the deltas
            queryJavaInstanceDelta(newEndTimeNs, myPreviousQueryEndTimeNs, rightAllocations, rightDeallocations, true);
            queryJniReferencesDelta(newEndTimeNs, myPreviousQueryEndTimeNs, rightAllocations, rightDeallocations, true);
            resetDeltaAllocationList.addAll(rightAllocations);
            resetDeltaFreeList.addAll(rightDeallocations);
          }
          else if (newEndTimeNs > myPreviousQueryEndTimeNs) {
            // Selection's max shifts right: add data within this range to the deltas
            queryJavaInstanceDelta(myPreviousQueryEndTimeNs, newEndTimeNs, rightAllocations, rightDeallocations, false);
            queryJniReferencesDelta(myPreviousQueryEndTimeNs, newEndTimeNs, rightAllocations, rightDeallocations, false);
            deltaAllocationList.addAll(rightAllocations);
            deltaFreeList.addAll(rightDeallocations);
          }
        }

        myPreviousQueryStartTimeNs = newStartTimeNs;
        myPreviousQueryEndTimeNs = newEndTimeNs;

        joiner.execute(() -> {
          myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_HEAP_UPDATED);
          if (clear ||
              deltaAllocationList.size() + deltaFreeList.size() + resetDeltaAllocationList.size() + resetDeltaFreeList.size() > 0) {
            if (clear) {
              myHeapSets.forEach(heap -> heap.clearClassifierSets());
              if (myStage.getSelectedClassSet() != null) {
                myStage.selectClassSet(ClassSet.EMPTY_SET);
              }
            }
            if (myStage.getStudioProfilers().getIdeServices().getFeatureConfig().isMemorySnapshotEnabled()) {
              snapshotList.forEach(instance -> myHeapSets.get(instance.getHeapId()).addSnapshotInstanceObject(instance));
              resetSnapshotList.forEach(instance -> myHeapSets.get(instance.getHeapId()).removeSnapshotInstanceObject(instance));
            }
            deltaAllocationList.forEach(instance -> myHeapSets.get(instance.getHeapId()).addDeltaInstanceObject(instance));
            deltaFreeList.forEach(instance -> myHeapSets.get(instance.getHeapId()).freeDeltaInstanceObject(instance));
            resetDeltaAllocationList.forEach(instance -> myHeapSets.get(instance.getHeapId()).removeAddedDeltaInstanceObject(instance));
            resetDeltaFreeList.forEach(instance -> myHeapSets.get(instance.getHeapId()).removeFreedDeltaInstanceObject(instance));
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

  @Nullable
  private JniReferenceInstanceObject getOrCreateJniRefObject(int tag, long refValue, int threadId) {
    LiveAllocationInstanceObject referencedObject = myInstanceMap.get(tag);
    if (referencedObject == null) {
      // If a Java object can't be found by a given tag, nothing is known about the JNI reference and we can't track it.
      return null;
    }
    ThreadId thread = null;
    if (threadId != 0) {
      assert myThreadIdMap.containsKey(threadId);
      thread = myThreadIdMap.get(threadId);
    }

    JniReferenceInstanceObject result = referencedObject.getJniRefByValue(refValue);
    if (result == null) {
      result = new JniReferenceInstanceObject(this, referencedObject, thread, tag, refValue);
      referencedObject.addJniRef(result);
    }
    return result;
  }

  private void queryJavaInstanceSnapshot(long newTimeNs, @NotNull List<InstanceObject> setAllocationList) {
    if (!myStage.getStudioProfilers().getIdeServices().getFeatureConfig().isMemorySnapshotEnabled()) {
      return;
    }

    BatchAllocationSample sampleResponse = myClient.getAllocations(AllocationSnapshotRequest.newBuilder().setSession(mySession)
                                                                     .setEndTime(newTimeNs).setLiveObjectsOnly(true).build());

    for (AllocationEvent event : sampleResponse.getEventsList()) {
      if (event.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
        AllocationEvent.Allocation allocation = event.getAllocData();
        LiveAllocationInstanceObject instance =
          getOrCreateInstanceObject(allocation.getTag(), allocation.getClassTag(), allocation.getStackId(), allocation.getThreadId(),
                                    allocation.getSize(), allocation.getHeapId());
        instance.setAllocationTime(event.getTimestamp());
        setAllocationList.add(instance);
      }
      else {
        assert false;
      }
    }
  }

  private void queryJniReferencesSnapshot(long newTimeNs, @NotNull List<InstanceObject> setAllocationList) {
    if (!myEnableJniRefsTracking) {
      return;
    }
    JNIGlobalRefsEventsRequest request = JNIGlobalRefsEventsRequest.newBuilder().setSession(mySession)
      .setLiveObjectsOnly(true).setEndTime(newTimeNs).build();
    BatchJNIGlobalRefEvent jniBatch = myClient.getJNIGlobalRefsEvents(request);

    for (JNIGlobalReferenceEvent event : jniBatch.getEventsList()) {
      if (event.getEventType() != JNIGlobalReferenceEvent.Type.CREATE_GLOBAL_REF) {
        continue;
      }
      JniReferenceInstanceObject refObject = getOrCreateJniRefObject(event.getObjectTag(), event.getRefValue(), event.getThreadId());
      if (refObject == null) {
        // JNI reference object can't be constructed, most likely allocation for underlying java object was not
        // reported. We don't have anything to show and ignore this reference.
        continue;
      }
      if (event.hasBacktrace()) {
        refObject.setAllocationBacktrace(event.getBacktrace());
      }
      refObject.setAllocationTime(event.getTimestamp());
      setAllocationList.add(refObject);
    }
  }

  /**
   * @param startTimeNs      start time to query data for.
   * @param endTimeNs        end time to query data for.
   * @param allocationList   Instances that were allocated within the query range will be added here.
   * @param deallocatoinList Instances that were deallocated within the query range will be added here.
   * @param resetInstance    Whether the InstanceObject's alloc/dealloc time information should reset if a corresponding allocation or
   *                         deallocation event has occurred. The {@link ClassifierSet} rely on the presence (or absence) of these time data
   *                         to determine whether the InstanceObject should be added (or removed) from the ClassifierSet. Also see {@link
   *                         ClassifierSet#removeDeltaInstanceInformation(InstanceObject, boolean)}.
   */
  private void queryJavaInstanceDelta(long startTimeNs,
                                      long endTimeNs,
                                      @NotNull List<InstanceObject> allocationList,
                                      @NotNull List<InstanceObject> deallocatoinList,
                                      boolean resetInstance) {
    if (startTimeNs == endTimeNs) {
      return;
    }

    BatchAllocationSample sampleResponse = myClient.getAllocations(
      AllocationSnapshotRequest.newBuilder().setSession(mySession).setStartTime(startTimeNs).setEndTime(endTimeNs).build());

    for (AllocationEvent event : sampleResponse.getEventsList()) {
      if (event.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
        AllocationEvent.Allocation allocation = event.getAllocData();
        LiveAllocationInstanceObject instance =
          getOrCreateInstanceObject(allocation.getTag(), allocation.getClassTag(), allocation.getStackId(), allocation.getThreadId(),
                                    allocation.getSize(), allocation.getHeapId());
        instance.setAllocationTime(resetInstance ? Long.MIN_VALUE : event.getTimestamp());
        allocationList.add(instance);
      }
      else if (event.getEventCase() == AllocationEvent.EventCase.FREE_DATA) {
        AllocationEvent.Deallocation deallocation = event.getFreeData();
        LiveAllocationInstanceObject instance =
          getOrCreateInstanceObject(deallocation.getTag(), deallocation.getClassTag(), deallocation.getStackId(),
                                    deallocation.getThreadId(), deallocation.getSize(), deallocation.getHeapId());
        instance.setDeallocTime(resetInstance ? Long.MAX_VALUE : event.getTimestamp());
        deallocatoinList.add(instance);
      }
      else {
        assert false;
      }
    }
  }

  private void queryJniReferencesDelta(long startTimeNs,
                                       long endTimeNs,
                                       @NotNull List<InstanceObject> allocationList,
                                       @NotNull List<InstanceObject> deallocatoinList,
                                       boolean resetInstance) {
    if (!myEnableJniRefsTracking || startTimeNs == endTimeNs) {
      return;
    }
    JNIGlobalRefsEventsRequest request =
      JNIGlobalRefsEventsRequest.newBuilder().setSession(mySession).setStartTime(startTimeNs).setEndTime(endTimeNs).build();
    BatchJNIGlobalRefEvent jniBatch = myClient.getJNIGlobalRefsEvents(request);

    for (JNIGlobalReferenceEvent event : jniBatch.getEventsList()) {
      JniReferenceInstanceObject refObject = getOrCreateJniRefObject(event.getObjectTag(), event.getRefValue(), event.getThreadId());
      if (refObject == null) {
        // JNI reference object can't be constructed, most likely allocation for underlying java object was not
        // reported. We don't have anything to show and ignore this reference.
        continue;
      }
      switch (event.getEventType()) {
        case CREATE_GLOBAL_REF:
          if (resetInstance) {
            refObject.setAllocationTime(Long.MIN_VALUE);
          } else {
            refObject.setAllocationTime(event.getTimestamp());
            if (event.hasBacktrace()) {
              refObject.setAllocationBacktrace(event.getBacktrace());
            }
          }
          allocationList.add(refObject);
          break;
        case DELETE_GLOBAL_REF:
          if (resetInstance) {
            refObject.setAllocationTime(Long.MAX_VALUE);
          } else {
            refObject.setDeallocTime(event.getTimestamp());
            if (event.hasBacktrace()) {
              refObject.setDeallocationBacktrace(event.getBacktrace());
            }
          }
          deallocatoinList.add(refObject);
          break;
        default:
          assert false;
      }
    }
  }
}
