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

import static com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.ALLOCATIONS;
import static com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.DEALLOCATIONS;
import static com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.LABEL;
import static com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.SHALLOW_SIZE;
import static com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.TOTAL_COUNT;
import static com.android.tools.profilers.memory.adapters.CaptureObject.InstanceAttribute.ALLOCATION_TIME;
import static com.android.tools.profilers.memory.adapters.CaptureObject.InstanceAttribute.DEALLOCATION_TIME;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.AllocatedClass;
import com.android.tools.profiler.proto.Memory.AllocationEvent;
import com.android.tools.profiler.proto.Memory.AllocationStack;
import com.android.tools.profiler.proto.Memory.BatchJNIGlobalRefEvent;
import com.android.tools.profiler.proto.Memory.JNIGlobalReferenceEvent;
import com.android.tools.profiler.proto.Memory.NativeBacktrace;
import com.android.tools.profiler.proto.Memory.NativeCallStack;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationContextsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationContextsResponse;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationEventsResponse;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationSnapshotRequest;
import com.android.tools.profiler.proto.MemoryProfiler.JNIGlobalRefsEventsRequest;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.memory.MemoryProfiler;
import com.android.tools.profilers.memory.MemoryProfilerAspect;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.stacktrace.ThreadId;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TLongObjectHashMap;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class LiveAllocationCaptureObject implements CaptureObject {
  private static Logger getLogger() {
    return Logger.getInstance(LiveAllocationCaptureObject.class);
  }

  // Buffer to ensure the queries include the before/after batched sample(s) that fall just outside of the query range, as those samples
  // can contain events with timestamps that satisfy the query.
  // In perfa, the batched samples are sent in 500ms but can take time to arrive. 5 seconds should be more than enough as a buffer.
  private static final long QUERY_BUFFER_NS = TimeUnit.SECONDS.toNanos(5);

  @VisibleForTesting static final String SAMPLING_INFO_MESSAGE = "Selected region does not have full tracking. Data may be inaccurate.";

  @Nullable private MemoryProfilerStage myStage;

  @VisibleForTesting final ExecutorService myExecutorService;
  private final ClassDb myClassDb;
  private final TIntObjectHashMap<LiveAllocationInstanceObject> myInstanceMap;
  private final TIntObjectHashMap<Memory.AllocationStack> myCallstackMap;
  // Mapping from unsymbolized addresses to symbolized native frames
  @NotNull private final TLongObjectHashMap<NativeCallStack.NativeFrame> myNativeFrameMap;
  private final TLongObjectHashMap<AllocationStack.StackFrame> myMethodIdMap;
  private final TIntObjectHashMap<ThreadId> myThreadIdMap;
  private final TreeMap<Long, Memory.MemoryMap.MemoryRegion> myJniMemoryRegionMap;

  private final ProfilerClient myClient;
  private final Common.Session mySession;
  private final long myCaptureStartTime;
  private final List<HeapSet> myHeapSets;
  private final AspectObserver myAspectObserver;
  private final boolean myEnableJniRefsTracking;

  private long myContextEndTimeNs = Long.MIN_VALUE;
  private long myPreviousQueryStartTimeNs = Long.MIN_VALUE;
  private long myPreviousQueryEndTimeNs = Long.MIN_VALUE;
  // Keeps track of the latest sample's timestamp we have queried thus far.
  private long myLastSeenTimestampNs = Long.MIN_VALUE;

  private Range myQueryRange;

  private Future myCurrentTask;
  @Nullable private String myInfoMessage;

  public LiveAllocationCaptureObject(@NotNull ProfilerClient client,
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
    myInstanceMap = new TIntObjectHashMap<>();
    myCallstackMap = new TIntObjectHashMap<>();
    myNativeFrameMap = new TLongObjectHashMap<>();
    myMethodIdMap = new TLongObjectHashMap<>();
    myThreadIdMap = new TIntObjectHashMap<>();
    myJniMemoryRegionMap = new TreeMap<>();

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
  }

  @Override
  @NotNull
  public Common.Session getSession() {
    return mySession;
  }

  @Override
  @NotNull
  public MemoryServiceGrpc.MemoryServiceBlockingStub getClient() {
    return myClient.getMemoryClient();
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

  @Nullable
  @Override
  public String getInfoMessage() {
    return myInfoMessage;
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

  @NotNull
  @Override
  public ClassDb getClassDatabase() {
    return myClassDb;
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
  public AllocationStack.StackFrame getStackFrame(long methodId) {
    return myMethodIdMap.get(methodId);
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
    List<Memory.BatchAllocationContexts> contextsList = getAllocationContexts(myContextEndTimeNs, endTimeNs);
    for (Memory.BatchAllocationContexts contexts : contextsList) {
      for (AllocatedClass klass : contexts.getClassesList()) {
        // We don't have super class information at the moment so just assign invalid id as the super class id.
        myClassDb.registerClass(klass.getClassId(), klass.getClassName());
      }
      contexts.getMethodsList().forEach(method -> {
        if (!myMethodIdMap.containsKey(method.getMethodId())) {
          myMethodIdMap.put(method.getMethodId(), method);
        }
      });
      contexts.getEncodedStacksList().forEach(callStack -> {
        if (!myCallstackMap.contains(callStack.getStackId())) {
          myCallstackMap.put(callStack.getStackId(), callStack);
        }
      });
      contexts.getThreadInfosList().forEach(thread -> {
        if (!myThreadIdMap.contains(thread.getThreadId())) {
          myThreadIdMap.put(thread.getThreadId(), new ThreadId(thread.getThreadName()));
        }
      });
      contexts.getMemoryMap().getRegionsList().forEach(region -> {
        myJniMemoryRegionMap.put(region.getStartAddress(), region);
      });
      myContextEndTimeNs = Math.max(myContextEndTimeNs, contexts.getTimestamp());
    }
  }

  /**
   * Load allocation data corresponding to the input time range. Note that load operation is expensive and happens on a different thread
   * (via myExecutorService). When loading is done, it informs the listener (e.g. UI) to update via the input joiner.
   */
  private void loadTimeRange(@NotNull Range queryRange, @NotNull Executor joiner) {
    try {
      // Ignore invalid range. This can happen when a selection range is cleared during the process of a new range being selected.
      if (queryRange.isEmpty()) {
        return;
      }

      if (myCurrentTask != null) {
        myCurrentTask.cancel(false);
      }
      myCurrentTask = myExecutorService.submit(() -> {
        long newStartTimeNs = TimeUnit.MICROSECONDS.toNanos((long)queryRange.getMin());
        long newEndTimeNs = TimeUnit.MICROSECONDS.toNanos((long)queryRange.getMax());
        if (newStartTimeNs == myPreviousQueryStartTimeNs && newEndTimeNs == myPreviousQueryEndTimeNs) {
          return null;
        }

        boolean hasNonFullTrackingRegion = !MemoryProfiler.hasOnlyFullAllocationTrackingWithinRegion(
          myStage.getStudioProfilers(), mySession, TimeUnit.NANOSECONDS.toMicros(newStartTimeNs),
          TimeUnit.NANOSECONDS.toMicros(newEndTimeNs));

        joiner.execute(() -> myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_HEAP_UPDATING));
        updateAllocationContexts(newEndTimeNs);

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
        // Samples that are within the query range may not have arrived from the daemon yet. If the query range is greater than the
        // last sample we have seen. Set the last query timestamp to the last sample's timestmap, so that next time we will requery
        // the range between (last-seen sample, newEndTimeNs).
        myPreviousQueryEndTimeNs = Math.min(newEndTimeNs, myLastSeenTimestampNs);

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

            myInfoMessage = hasNonFullTrackingRegion ? SAMPLING_INFO_MESSAGE : null;
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
      instance = new LiveAllocationInstanceObject(this, entry, thread, callstack, size, heapId);
      myInstanceMap.put(tag, instance);
    }

    return instance;
  }

  @Nullable
  private JniReferenceInstanceObject getOrCreateJniRefObject(int tag, long refValue) {
    LiveAllocationInstanceObject referencedObject = myInstanceMap.get(tag);
    if (referencedObject == null) {
      // If a Java object can't be found by a given tag, nothing is known about the JNI reference and we can't track it.
      return null;
    }
    JniReferenceInstanceObject result = referencedObject.getJniRefByValue(refValue);
    if (result == null) {
      result = new JniReferenceInstanceObject(this, referencedObject, tag, refValue);
      referencedObject.addJniRef(result);
    }
    return result;
  }

  /**
   * Populates the input list with all instance objects that are alive at |snapshotTimeNs|.
   */
  private void queryJavaInstanceSnapshot(long snapshotTimeNs, @NotNull List<InstanceObject> snapshotList) {
    // Retrieve all the event samples from the start of the session until the snapshot time.
    long sessionStartNs = mySession.getStartTimestamp();
    List<Memory.BatchAllocationEvents> eventsList = getAllocationEvents(sessionStartNs, snapshotTimeNs);
    Map<Integer, LiveAllocationInstanceObject> liveInstanceMap = new LinkedHashMap<>();
    for (Memory.BatchAllocationEvents events : eventsList) {
      // Only consider events up to but excluding the snapshot time.
      Iterator<AllocationEvent> itr = events.getEventsList().stream().filter(evt -> evt.getTimestamp() < snapshotTimeNs)
        .sorted(Comparator.comparingLong(AllocationEvent::getTimestamp)).iterator();
      while (itr.hasNext()) {
        AllocationEvent event = itr.next();
        LiveAllocationInstanceObject instance;
        switch (event.getEventCase()) {
          case ALLOC_DATA:
            // Allocation - create an InstanceObject. This might be removed later if there is a corresponding FREE_DATA event.
            AllocationEvent.Allocation allocation = event.getAllocData();
            instance = getOrCreateInstanceObject(allocation.getTag(), allocation.getClassTag(), allocation.getStackId(),
                                                 allocation.getThreadId(), allocation.getSize(), allocation.getHeapId());
            instance.setAllocationTime(event.getTimestamp());
            liveInstanceMap.put(allocation.getTag(), instance);
            break;
          case FREE_DATA:
            // Deallocation - there should be a matching InstanceObject.
            AllocationEvent.Deallocation deallocation = event.getFreeData();
            liveInstanceMap.remove(deallocation.getTag());
            // Don't keep deallocated objects around in the cache to avoid bloating memory.
            myInstanceMap.remove(deallocation.getTag());
            break;
          case CLASS_DATA:
            // ignore CLASS_DATA as they are handled via context updates.
            break;
        }
      }
    }

    snapshotList.addAll(liveInstanceMap.values());
  }

  private void queryJniReferencesSnapshot(long snapshotTimeNs, @NotNull List<InstanceObject> snapshotList) {
    if (!myEnableJniRefsTracking) {
      return;
    }

    long sessionStartNs = mySession.getStartTimestamp();
    List<Memory.BatchJNIGlobalRefEvent> eventsList = getJniRefEvents(sessionStartNs, snapshotTimeNs);
    Map<Long, JniReferenceInstanceObject> instanceMap = new LinkedHashMap<>();
    for (Memory.BatchJNIGlobalRefEvent events : eventsList) {
      // Only consider events up to but excluding the snapshot time.
      Iterator<JNIGlobalReferenceEvent> itr = events.getEventsList().stream().filter(evt -> evt.getTimestamp() < snapshotTimeNs)
        .sorted(Comparator.comparingLong(JNIGlobalReferenceEvent::getTimestamp)).iterator();
      while (itr.hasNext()) {
        JNIGlobalReferenceEvent event = itr.next();

        JniReferenceInstanceObject refObject;
        switch (event.getEventType()) {
          case CREATE_GLOBAL_REF:
            // New global ref - create an InstanceObject. This might be removed later if there is a corresponding DELETE_GLOBAL_REF event.
            refObject = getOrCreateJniRefObject(event.getObjectTag(), event.getRefValue());
            if (refObject == null) {
              // JNI reference object can't be constructed, most likely allocation for underlying java object was not
              // reported. We don't have anything to show and ignore this reference.
              continue;
            }
            if (event.hasBacktrace()) {
              refObject.setAllocationBacktrace(event.getBacktrace());
            }
            int threadId = event.getThreadId();
            ThreadId thread = ThreadId.INVALID_THREAD_ID;
            if (threadId != 0) {
              assert myThreadIdMap.containsKey(threadId);
              thread = myThreadIdMap.get(threadId);
            }
            refObject.setAllocThreadId(thread);
            refObject.setAllocationTime(event.getTimestamp());
            instanceMap.put(refObject.getRefValue(), refObject);
            break;
          case DELETE_GLOBAL_REF:
            refObject = instanceMap.remove(event.getRefValue());
            // If the referencing instance object is still around, remove the added JNI ref.
            if (refObject != null && myInstanceMap.containsKey(event.getObjectTag())) {
              myInstanceMap.get(event.getObjectTag()).removeJniRef(refObject);
            }
            break;
        }
      }
    }

    snapshotList.addAll(instanceMap.values());
  }

  /**
   * @param startTimeNs      start time to query data for.
   * @param endTimeNs        end time to query data for.
   * @param allocationList   Instances that were allocated within the query range will be added here.
   * @param deallocationList Instances that were deallocated within the query range will be added here.
   * @param resetInstance    Whether the InstanceObject's alloc/dealloc time information should reset if a corresponding allocation or
   *                         deallocation event has occurred. The {@link ClassifierSet} rely on the presence (or absence) of these time data
   *                         to determine whether the InstanceObject should be added (or removed) from the ClassifierSet. Also see {@link
   *                         ClassifierSet#removeDeltaInstanceInformation(InstanceObject, boolean)}.
   */
  private void queryJavaInstanceDelta(long startTimeNs,
                                      long endTimeNs,
                                      @NotNull List<InstanceObject> allocationList,
                                      @NotNull List<InstanceObject> deallocationList,
                                      boolean resetInstance) {
    // Case for point-snapshot - we don't need to further query deltas.
    if (startTimeNs == endTimeNs) {
      return;
    }


    List<Memory.BatchAllocationEvents> eventsList = getAllocationEvents(startTimeNs, endTimeNs);
    for (Memory.BatchAllocationEvents events : eventsList) {
      // Only consider events between the delta range [start time, end time)
      Iterator<AllocationEvent> itr =
        events.getEventsList().stream()
          .filter(evt -> evt.getTimestamp() >= startTimeNs && evt.getTimestamp() < endTimeNs)
          .sorted(Comparator.comparingLong(AllocationEvent::getTimestamp))
          .iterator();
      while (itr.hasNext()) {
        AllocationEvent event = itr.next();
        LiveAllocationInstanceObject instance;
        switch (event.getEventCase()) {
          case ALLOC_DATA:
            // New allocation - create an InstanceObject.
            AllocationEvent.Allocation allocation = event.getAllocData();
            instance = getOrCreateInstanceObject(allocation.getTag(), allocation.getClassTag(), allocation.getStackId(),
                                                 allocation.getThreadId(), allocation.getSize(), allocation.getHeapId());
            instance.setAllocationTime(resetInstance ? Long.MIN_VALUE : event.getTimestamp());
            allocationList.add(instance);
            break;
          case FREE_DATA:
            // New deallocation - there should be a matching InstanceObject.
            AllocationEvent.Deallocation deallocation = event.getFreeData();
            assert myInstanceMap.containsKey(deallocation.getTag());
            instance = myInstanceMap.get(deallocation.getTag());
            instance.setDeallocTime(resetInstance ? Long.MAX_VALUE : event.getTimestamp());
            deallocationList.add(instance);
            break;
          case CLASS_DATA:
            // ignore CLASS_DATA as they are handled via context updates.
            break;
        }
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

    List<Memory.BatchJNIGlobalRefEvent> eventsList = getJniRefEvents(startTimeNs, endTimeNs);
    for (BatchJNIGlobalRefEvent events : eventsList) {
      // Only consider events between the delta range [start time, end time)
      Iterator<JNIGlobalReferenceEvent> itr =
        events.getEventsList().stream().filter(evt -> evt.getTimestamp() >= startTimeNs && evt.getTimestamp() < endTimeNs)
          .sorted(Comparator.comparingLong(JNIGlobalReferenceEvent::getTimestamp))
          .iterator();
      while (itr.hasNext()) {
        JNIGlobalReferenceEvent event = itr.next();
        JniReferenceInstanceObject refObject = getOrCreateJniRefObject(event.getObjectTag(), event.getRefValue());
        if (refObject == null) {
          // JNI reference object can't be constructed, most likely allocation for underlying java object was not
          // reported. We don't have anything to show and ignore this reference.
          continue;
        }
        int threadId = event.getThreadId();
        ThreadId thread = ThreadId.INVALID_THREAD_ID;
        if (threadId != 0) {
          assert myThreadIdMap.containsKey(threadId);
          thread = myThreadIdMap.get(threadId);
        }

        switch (event.getEventType()) {
          case CREATE_GLOBAL_REF:
            if (resetInstance) {
              refObject.setAllocationTime(Long.MIN_VALUE);
            }
            else {
              refObject.setAllocationTime(event.getTimestamp());
              if (event.hasBacktrace()) {
                refObject.setAllocationBacktrace(event.getBacktrace());
              }
              refObject.setAllocThreadId(thread);
            }
            allocationList.add(refObject);
            break;
          case DELETE_GLOBAL_REF:
            if (resetInstance) {
              refObject.setAllocationTime(Long.MAX_VALUE);
            }
            else {
              refObject.setDeallocTime(event.getTimestamp());
              if (event.hasBacktrace()) {
                refObject.setDeallocationBacktrace(event.getBacktrace());
              }
              refObject.setDeallocThreadId(thread);
            }
            deallocatoinList.add(refObject);
            break;
          default:
            assert false;
        }
      }
    }
  }

  @NotNull
  NativeCallStack resolveNativeBacktrace(@Nullable NativeBacktrace backtrace) {
    if (backtrace == null || backtrace.getAddressesCount() == 0) {
      return NativeCallStack.getDefaultInstance();
    }


    NativeCallStack.Builder builder = NativeCallStack.newBuilder();
    for (long address : backtrace.getAddressesList()) {
      if (!myNativeFrameMap.containsKey(address)) {
        String module = "";
        long offset = 0;
        Memory.MemoryMap.MemoryRegion region = getRegionByAddress(address);
        if (region != null) {
          module = region.getName();
          // Adjust address to represent module offset.
          offset = region.getFileOffset() + (address - region.getStartAddress());
        }

        NativeCallStack.NativeFrame unsymbolizedFrame = NativeCallStack.NativeFrame.newBuilder()
          .setAddress(address).setModuleName(module).setModuleOffset(offset).build();
        NativeCallStack.NativeFrame symbolizedFrame = myStage.getStudioProfilers().getIdeServices().getNativeFrameSymbolizer()
          .symbolize(myStage.getStudioProfilers().getSessionsManager().getSelectedSessionMetaData().getProcessAbi(), unsymbolizedFrame);
        myNativeFrameMap.put(address, symbolizedFrame);
      }

      builder.addFrames(myNativeFrameMap.get(address));
    }
    return builder.build();
  }

  @Nullable
  private Memory.MemoryMap.MemoryRegion getRegionByAddress(long address) {
    Map.Entry<Long, Memory.MemoryMap.MemoryRegion> entry = myJniMemoryRegionMap.floorEntry(address);
    if (entry == null) {
      return null;
    }
    Memory.MemoryMap.MemoryRegion region = entry.getValue();
    if (address >= region.getStartAddress() && address < region.getEndAddress()) {
      return region;
    }
    return null;
  }

  @NotNull
  private List<Memory.BatchAllocationContexts> getAllocationContexts(long startTimeNs, long endTimeNs) {
    if (myStage.getStudioProfilers().getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      Transport.GetEventGroupsResponse response = myClient.getTransportClient().getEventGroups(
        buildEventGroupRequest(Common.Event.Kind.MEMORY_ALLOC_CONTEXTS, startTimeNs, endTimeNs + QUERY_BUFFER_NS));

      assert response.getGroupsCount() <= 1;
      return response.getGroupsCount() == 1 ?
             response.getGroups(0).getEventsList().stream().map(event -> event.getMemoryAllocContexts().getContexts())
               .collect(Collectors.toList()) :
             Collections.emptyList();
    }
    else {
      AllocationContextsResponse response = getClient().getAllocationContexts(AllocationContextsRequest.newBuilder()
                                                                                .setSession(mySession)
                                                                                .setStartTime(startTimeNs)
                                                                                .setEndTime(endTimeNs + QUERY_BUFFER_NS)
                                                                                .build());
      return response.getContextsList();
    }
  }

  @NotNull
  private List<Memory.BatchAllocationEvents> getAllocationEvents(long startTimeNs, long endTimeNs) {
    if (myStage.getStudioProfilers().getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      Transport.GetEventGroupsResponse response = myClient.getTransportClient().getEventGroups(
        buildEventGroupRequest(Common.Event.Kind.MEMORY_ALLOC_EVENTS, startTimeNs - QUERY_BUFFER_NS, endTimeNs + QUERY_BUFFER_NS));

      assert response.getGroupsCount() <= 1;
      return response.getGroupsCount() == 1 ?
             getEventsAndUpdateSeenTimestamp(
               response.getGroups(0).getEventsList().stream().map(event -> event.getMemoryAllocEvents().getEvents())
                 .collect(Collectors.toList()),
               Memory.BatchAllocationEvents::getTimestamp) :
             Collections.emptyList();
    }
    else {
      AllocationEventsResponse response = getClient().getAllocationEvents(AllocationSnapshotRequest.newBuilder()
                                                                            .setSession(mySession)
                                                                            .setStartTime(startTimeNs - QUERY_BUFFER_NS)
                                                                            .setEndTime(endTimeNs + QUERY_BUFFER_NS)
                                                                            .build());
      List<Memory.BatchAllocationEvents> eventsList = response.getEventsList();
      return getEventsAndUpdateSeenTimestamp(eventsList, Memory.BatchAllocationEvents::getTimestamp);
    }
  }

  @NotNull
  private List<Memory.BatchJNIGlobalRefEvent> getJniRefEvents(long startTimeNs, long endTimeNs) {
    if (myStage.getStudioProfilers().getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      Transport.GetEventGroupsResponse response = myClient.getTransportClient().getEventGroups(
        buildEventGroupRequest(Common.Event.Kind.MEMORY_JNI_REF_EVENTS, startTimeNs - QUERY_BUFFER_NS, endTimeNs + QUERY_BUFFER_NS));

      assert response.getGroupsCount() <= 1;
      return response.getGroupsCount() == 1 ?
             getEventsAndUpdateSeenTimestamp(
               response.getGroups(0).getEventsList().stream().map(event -> event.getMemoryJniRefEvents().getEvents())
                 .collect(Collectors.toList()),
               BatchJNIGlobalRefEvent::getTimestamp) :
             Collections.emptyList();
    }
    else {
      JNIGlobalRefsEventsRequest request = JNIGlobalRefsEventsRequest.newBuilder()
        .setSession(mySession)
        .setStartTime(startTimeNs - QUERY_BUFFER_NS)
        .setEndTime(endTimeNs + QUERY_BUFFER_NS)
        .build();
      List<Memory.BatchJNIGlobalRefEvent> eventsList = getClient().getJNIGlobalRefsEvents(request).getEventsList();
      return getEventsAndUpdateSeenTimestamp(eventsList, BatchJNIGlobalRefEvent::getTimestamp);
    }
  }

  @NotNull
  private Transport.GetEventGroupsRequest buildEventGroupRequest(Common.Event.Kind kind,
                                                                 long startTimeNs,
                                                                 long endTimeNs) {
    return Transport.GetEventGroupsRequest.newBuilder()
      .setStreamId(mySession.getStreamId())
      .setPid(mySession.getPid())
      .setKind(kind)
      .setFromTimestamp(startTimeNs)
      .setToTimestamp(endTimeNs)
      .build();
  }

  private <T> List<T> getEventsAndUpdateSeenTimestamp(List<T> eventList, Function<T, Long> timestampFunc) {
    for (T event : eventList) {
      myLastSeenTimestampNs = Math.max(timestampFunc.apply(event), myLastSeenTimestampNs);
    }
    return eventList;
  }
}
