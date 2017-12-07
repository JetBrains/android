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
package com.android.tools.idea.profilers.perfd;

import com.android.annotations.Nullable;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.profilers.LegacyAllocationTracker;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.google.profiler.protobuf3jarjar.ByteString;
import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TLongObjectHashMap;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

import static com.android.tools.profiler.proto.MemoryProfiler.AllocationsInfo.Status.*;

public class MemoryServiceProxy extends PerfdProxyService {

  private static Logger getLogger() {
    return Logger.getInstance(MemoryServiceProxy.class);
  }

  static final LegacyAllocationEventsResponse NOT_FOUND_RESPONSE =
    LegacyAllocationEventsResponse.newBuilder().setStatus(LegacyAllocationEventsResponse.Status.NOT_FOUND).build();

  @NotNull private Executor myFetchExecutor;
  @NotNull private final MemoryServiceGrpc.MemoryServiceBlockingStub myServiceStub;
  @NotNull private final IDevice myDevice;
  @NotNull private BiFunction<IDevice, Integer, LegacyAllocationTracker> myTrackerSupplier;

  private boolean myUseLegacyTracking;
  private final Object myUpdatingDataLock;
  // Per-process cache of legacy allocation tracker, AllocationsInfo and GetAllocationsResponse
  @Nullable private TIntObjectHashMap<LegacyAllocationTracker> myLegacyTrackers;
  @Nullable private TIntObjectHashMap<TLongObjectHashMap<AllocationTrackingData>> myTrackingData;
  @Nullable private TIntObjectHashMap<AllocationsInfo> myInProgressTrackingInfo;
  @Nullable private TIntObjectHashMap<TLongObjectHashMap<AllocatedClass>> myAllocatedClasses;
  @Nullable private TIntObjectHashMap<TIntObjectHashMap<AllocationStack>> myAllocationStacks;

  public MemoryServiceProxy(@NotNull IDevice device,
                            @NotNull ManagedChannel channel,
                            @NotNull Executor fetchExecutor,
                            @NotNull BiFunction<IDevice, Integer, LegacyAllocationTracker> legacyTrackerSupplier) {
    super(MemoryServiceGrpc.getServiceDescriptor());

    myServiceStub = MemoryServiceGrpc.newBlockingStub(channel);
    myDevice = device;
    myFetchExecutor = fetchExecutor;
    myTrackerSupplier = legacyTrackerSupplier;
    myUpdatingDataLock = new Object();

    if (!(StudioFlags.PROFILER_USE_JVMTI.get() && StudioFlags.PROFILER_USE_LIVE_ALLOCATIONS.get()) ||
        myDevice.getVersion().getFeatureLevel() < AndroidVersion.VersionCodes.O) {
      myUseLegacyTracking = true;
      myLegacyTrackers = new TIntObjectHashMap<>();
      myTrackingData = new TIntObjectHashMap<>();
      myInProgressTrackingInfo = new TIntObjectHashMap<>();
      myAllocatedClasses = new TIntObjectHashMap<>();
      myAllocationStacks = new TIntObjectHashMap<>();
    }
  }

  public void startMonitoringApp(MemoryProfiler.MemoryStartRequest request,
                                 StreamObserver<MemoryProfiler.MemoryStartResponse> responseObserver) {
    if (myUseLegacyTracking && !myLegacyTrackers.contains(request.getProcessId())) {
      myLegacyTrackers.put(request.getProcessId(), myTrackerSupplier.apply(myDevice, request.getProcessId()));
      myTrackingData.put(request.getProcessId(), new TLongObjectHashMap<>());
      myAllocatedClasses.put(request.getProcessId(), new TLongObjectHashMap<>());
      myAllocationStacks.put(request.getProcessId(), new TIntObjectHashMap<>());
    }

    responseObserver.onNext(myServiceStub.startMonitoringApp(request));
    responseObserver.onCompleted();
  }

  public void stopMonitoringApp(MemoryProfiler.MemoryStopRequest request,
                                StreamObserver<MemoryProfiler.MemoryStopResponse> responseObserver) {
    if (myUseLegacyTracking) {
      // TODO: also stop any unfinished tracking session
      myLegacyTrackers.remove(request.getProcessId());
      myTrackingData.remove(request.getProcessId());
      myInProgressTrackingInfo.remove(request.getProcessId());
      myAllocatedClasses.remove(request.getProcessId());
      myAllocationStacks.remove(request.getProcessId());
    }

    responseObserver.onNext(myServiceStub.stopMonitoringApp(request));
    responseObserver.onCompleted();
  }

  public void getData(MemoryProfiler.MemoryRequest request, StreamObserver<MemoryProfiler.MemoryData> responseObserver) {
    try {
      MemoryProfiler.MemoryData data = myServiceStub.getData(request);

      if (myUseLegacyTracking) {
        synchronized (myUpdatingDataLock) {
          TLongObjectHashMap<AllocationTrackingData> infos = myTrackingData.get(request.getProcessId());
          MemoryProfiler.MemoryData.Builder rebuilder = data.toBuilder();
          long requestStartTime = request.getStartTime();
          long requestEndTime = request.getEndTime();

          // Note - the following is going to continuously return any unfinished whose start times are before the request's end time.
          // Dedeup is handled in MemoryDataPoller.
          List<AllocationsInfo> infosToReturn = new ArrayList<>();
          infos.forEachValue(trackingData -> {
            if (trackingData.myInfo.getStartTime() <= requestEndTime && trackingData.myInfo.getEndTime() > requestStartTime) {
              infosToReturn.add(trackingData.myInfo);
            }

            return true;
          });

          infosToReturn.sort(Comparator.comparingLong(AllocationsInfo::getStartTime));
          for (int i = 0; i < infosToReturn.size(); i++) {
            AllocationsInfo info = infosToReturn.get(i);
            rebuilder.addAllocationsInfo(info);
            long infoMax = info.getEndTime() == Long.MAX_VALUE ? info.getStartTime() : info.getEndTime();
            rebuilder.setEndTimestamp(Math.max(rebuilder.getEndTimestamp(), infoMax));
          }

          data = rebuilder.build();
        }
      }

      responseObserver.onNext(data);
      responseObserver.onCompleted();
    }
    catch (RuntimeException e) {
      responseObserver.onError(e);
    }
  }

  public void trackAllocations(TrackAllocationsRequest request,
                               StreamObserver<TrackAllocationsResponse> responseObserver) {
    int processId = request.getProcessId();
    long timestamp = request.getRequestTime();
    TrackAllocationsResponse response;
    if (myUseLegacyTracking) {
      response = request.getEnabled() ? enableAllocations(timestamp, processId) : disableAllocations(timestamp, processId);
    }
    else {
      // Post-O tracking - goes straight to perfd.
      response = myServiceStub.trackAllocations(request);
    }

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  /**
   * Note: this call is blocking if there is an existing completed AllocationsInfo sample that is in the parsing stage.
   */
  public void getLegacyAllocationEvents(LegacyAllocationEventsRequest request,
                                        StreamObserver<LegacyAllocationEventsResponse> responseObserver) {
    assert myUseLegacyTracking;

    TLongObjectHashMap<AllocationTrackingData> datas = myTrackingData.get(request.getProcessId());
    if (datas == null || !datas.containsKey(request.getStartTime()) || datas.get(request.getStartTime()).myDataParsingLatch == null) {
      responseObserver.onNext(NOT_FOUND_RESPONSE);
    }
    else {
      AllocationTrackingData data = datas.get(request.getStartTime());
      try {
        assert data.myDataParsingLatch != null;
        data.myDataParsingLatch.await();
        synchronized (myUpdatingDataLock) {
          assert data.myEventsResponse != null;
          responseObserver.onNext(data.myEventsResponse);
        }
      }
      catch (InterruptedException e) {
        getLogger().error("Exception while waiting for Allocation Tracking parsing results: " + e);
      }
    }

    responseObserver.onCompleted();
  }

  /**
   * Note: this call is blocking if there is an existing completed AllocationsInfo sample that is in the parsing stage.
   */
  public void getLegacyAllocationDump(DumpDataRequest request, StreamObserver<DumpDataResponse> responseObserver) {
    assert myUseLegacyTracking;

    TLongObjectHashMap<AllocationTrackingData> datas = myTrackingData.get(request.getProcessId());
    if (datas == null || !datas.containsKey(request.getDumpTime()) || datas.get(request.getDumpTime()).myDataParsingLatch == null) {
      responseObserver.onNext(DumpDataResponse.newBuilder().setStatus(DumpDataResponse.Status.NOT_FOUND).build());
    }
    else {
      AllocationTrackingData data = datas.get(request.getDumpTime());
      try {
        assert data.myDataParsingLatch != null;
        data.myDataParsingLatch.await();
        synchronized (myUpdatingDataLock) {
          assert data.myDumpDataResponse != null;
          responseObserver.onNext(data.myDumpDataResponse);
        }
      }
      catch (InterruptedException e) {
        getLogger().error("Exception while waiting for Allocation Tracking parsing results: " + e);
      }
    }

    responseObserver.onCompleted();
  }

  public void getLegacyAllocationContexts(LegacyAllocationContextsRequest request,
                                          StreamObserver<AllocationContextsResponse> responseObserver) {
    assert myUseLegacyTracking;


    AllocationContextsResponse.Builder builder = AllocationContextsResponse.newBuilder();
    if (myAllocatedClasses.containsKey(request.getProcessId())) {
      TLongObjectHashMap<AllocatedClass> klasses = myAllocatedClasses.get(request.getProcessId());
      request.getClassIdsList().forEach(id -> {
        if (klasses.contains(id)) {
          builder.addAllocatedClasses(klasses.get(id));
        }
        else {
          getLogger().debug("Class data cannot be found for id: " + id);
        }
      });
    }

    if (myAllocationStacks.containsKey(request.getProcessId())) {
      TIntObjectHashMap<AllocationStack> stacks = myAllocationStacks.get(request.getProcessId());
      request.getStackIdsList().forEach(id -> {
        if (stacks.contains(id)) {
          builder.addAllocationStacks(stacks.get(id));
        }
        else {
          getLogger().debug("Stack data cannot be found for id: " + id);
        }
      });
    }

    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  private TrackAllocationsResponse enableAllocations(long startTimeNs, int processId) {
    synchronized (myUpdatingDataLock) {
      if (myInProgressTrackingInfo.get(processId) != null) {
        // A previous tracking is still in-progress, cannot enable a new one.
        return TrackAllocationsResponse.newBuilder().setStatus(TrackAllocationsResponse.Status.IN_PROGRESS).build();
      }

      TLongObjectHashMap<AllocationTrackingData> datas = myTrackingData.get(processId);
      TrackAllocationsResponse.Builder responseBuilder = TrackAllocationsResponse.newBuilder();
      LegacyAllocationTracker tracker = myLegacyTrackers.get(processId);
      boolean success = tracker.trackAllocations(startTimeNs, startTimeNs, true, null, null);
      if (success) {
        AllocationsInfo newInfo = AllocationsInfo.newBuilder()
          .setStartTime(startTimeNs)
          .setEndTime(Long.MAX_VALUE)
          .setStatus(IN_PROGRESS)
          .setLegacy(true)
          .build();
        responseBuilder.setInfo(newInfo);
        responseBuilder.setStatus(TrackAllocationsResponse.Status.SUCCESS);
        AllocationTrackingData newData = new AllocationTrackingData();
        newData.myInfo = newInfo;
        datas.put(startTimeNs, newData);
        myInProgressTrackingInfo.put(processId, newInfo);
      }
      else {
        responseBuilder.setStatus(TrackAllocationsResponse.Status.FAILURE_UNKNOWN);
      }

      return responseBuilder.build();
    }
  }

  private TrackAllocationsResponse disableAllocations(long endtimeNs, int processId) {
    synchronized (myUpdatingDataLock) {
      AllocationsInfo lastInfo = myInProgressTrackingInfo.get(processId);
      if (lastInfo == null) {
        // No in-progress tracking, cannot disable one.
        return TrackAllocationsResponse.newBuilder().setStatus(TrackAllocationsResponse.Status.NOT_ENABLED).build();
      }

      LegacyAllocationTracker tracker = myLegacyTrackers.get(processId);
      TLongObjectHashMap<AllocationTrackingData> datas = myTrackingData.get(processId);
      TrackAllocationsResponse.Builder responseBuilder = TrackAllocationsResponse.newBuilder();
      boolean success = tracker.trackAllocations(lastInfo.getStartTime(), endtimeNs, false, myFetchExecutor,
                                                 (bytes, classes, stacks, allocations) -> saveAllocationData(processId,
                                                                                                             lastInfo.getStartTime(),
                                                                                                             bytes, classes,
                                                                                                             stacks,
                                                                                                             allocations));
      AllocationsInfo.Builder lastInfoBuilder = lastInfo.toBuilder();
      lastInfoBuilder.setEndTime(endtimeNs);
      if (success) {
        lastInfoBuilder.setStatus(COMPLETED);
        responseBuilder.setStatus(TrackAllocationsResponse.Status.SUCCESS);
        datas.get(lastInfo.getStartTime()).myDataParsingLatch = new CountDownLatch(1);
      }
      else {
        lastInfoBuilder.setStatus(FAILURE_UNKNOWN);
        responseBuilder.setStatus(TrackAllocationsResponse.Status.FAILURE_UNKNOWN);
      }

      AllocationsInfo updatedInfo = lastInfoBuilder.build();
      responseBuilder.setInfo(updatedInfo);
      datas.get(lastInfo.getStartTime()).myInfo = updatedInfo;
      return responseBuilder.build();
    }
  }

  private void saveAllocationData(int processId, long infoId,
                                  @Nullable byte[] rawBytes,
                                  @NotNull List<MemoryProfiler.AllocatedClass> classes,
                                  @NotNull List<MemoryProfiler.AllocationStack> stacks,
                                  @NotNull List<MemoryProfiler.LegacyAllocationEvent> events) {
    synchronized (myUpdatingDataLock) {
      TLongObjectHashMap<AllocationTrackingData> datas = myTrackingData.get(processId);
      assert datas.contains(infoId);
      AllocationTrackingData trackingData = datas.get(infoId);
      LegacyAllocationEventsResponse.Builder eventResponseBuilder = LegacyAllocationEventsResponse.newBuilder();
      DumpDataResponse.Builder dumpResponseBuilder = DumpDataResponse.newBuilder();
      try {
        if (rawBytes == null) {
          eventResponseBuilder.setStatus(LegacyAllocationEventsResponse.Status.FAILURE_UNKNOWN);
          dumpResponseBuilder.setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN);
        }
        else {
          eventResponseBuilder.addAllEvents(events).setStatus(LegacyAllocationEventsResponse.Status.SUCCESS);
          dumpResponseBuilder.setData(ByteString.copyFrom(rawBytes)).setStatus(DumpDataResponse.Status.SUCCESS);
        }

        trackingData.myEventsResponse = eventResponseBuilder.build();
        trackingData.myDumpDataResponse = dumpResponseBuilder.build();

        classes.forEach(klass -> myAllocatedClasses.get(processId).put(klass.getClassId(), klass));
        stacks.forEach(stack -> myAllocationStacks.get(processId).put(stack.getStackId(), stack));
      }
      finally {
        datas.get(infoId).myDataParsingLatch.countDown();
        myInProgressTrackingInfo.remove(processId);
      }
    }
  }

  public void forceGarbageCollection(ForceGarbageCollectionRequest request, StreamObserver<ForceGarbageCollectionResponse> observer) {
    if (myDevice.isOnline()) {
      int processId = request.getProcessId();
      for (Client client : myDevice.getClients()) {
        if (processId == client.getClientData().getPid()) {
          client.executeGarbageCollector();
          break;
        }
      }
    }
    observer.onNext(ForceGarbageCollectionResponse.newBuilder().build());
    observer.onCompleted();
  }

  @Override
  public ServerServiceDefinition getServiceDefinition() {
    Map<MethodDescriptor, ServerCallHandler> overrides = new HashMap<>();
    overrides.put(MemoryServiceGrpc.METHOD_START_MONITORING_APP,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    startMonitoringApp((MemoryStartRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(MemoryServiceGrpc.METHOD_STOP_MONITORING_APP,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    stopMonitoringApp((MemoryStopRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(MemoryServiceGrpc.METHOD_GET_DATA,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    getData((MemoryRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(MemoryServiceGrpc.METHOD_TRACK_ALLOCATIONS,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    trackAllocations((TrackAllocationsRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(MemoryServiceGrpc.METHOD_GET_LEGACY_ALLOCATION_EVENTS,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    getLegacyAllocationEvents((LegacyAllocationEventsRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(MemoryServiceGrpc.METHOD_GET_LEGACY_ALLOCATION_CONTEXTS,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    getLegacyAllocationContexts((LegacyAllocationContextsRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(MemoryServiceGrpc.METHOD_GET_LEGACY_ALLOCATION_DUMP,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    getLegacyAllocationDump((DumpDataRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(MemoryServiceGrpc.METHOD_FORCE_GARBAGE_COLLECTION,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    forceGarbageCollection((ForceGarbageCollectionRequest)request, (StreamObserver)observer);
                  }));

    return generatePassThroughDefinitions(overrides, myServiceStub);
  }

  private static class AllocationTrackingData {
    @NotNull AllocationsInfo myInfo;
    LegacyAllocationEventsResponse myEventsResponse;
    DumpDataResponse myDumpDataResponse;
    CountDownLatch myDataParsingLatch;
  }
}