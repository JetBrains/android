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
import com.android.ddmlib.IDevice;
import com.android.tools.adtui.model.DurationData;
import com.android.tools.idea.profilers.LegacyAllocationTracker;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.TIntObjectHashMap;
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

  static final AllocationEventsResponse NOT_FOUND_RESPONSE =
    AllocationEventsResponse.newBuilder().setStatus(AllocationEventsResponse.Status.NOT_FOUND).build();

  @NotNull private Executor myFetchExecutor;
  @NotNull private final MemoryServiceGrpc.MemoryServiceBlockingStub myServiceStub;
  @NotNull private final IDevice myDevice;
  @NotNull private BiFunction<IDevice, Integer, LegacyAllocationTracker> myTrackerSupplier;

  private boolean myUseLegacyTracking;
  private final Object myUpdatingDataLock;
  // Per-process cache of legacy allocation tracker, AllocationsInfo and GetAllocationsResponse
  @Nullable private TIntObjectHashMap<LegacyAllocationTracker> myLegacyTrackers;
  @Nullable private TIntObjectHashMap<List<AllocationsInfo>> myAllocationsInfos;
  @Nullable private TIntObjectHashMap<TIntObjectHashMap<AllocationEventsResponse>> myAllocationEvents;
  @Nullable private TIntObjectHashMap<TIntObjectHashMap<DumpDataResponse>> myAllocationDump;
  @Nullable private TIntObjectHashMap<TIntObjectHashMap<CountDownLatch>> myDataParsingLatches;
  @Nullable private TIntObjectHashMap<AllocatedClass> myAllocatedClasses;
  @Nullable private Map<ByteString, AllocationStack> myAllocationStacks;

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

    if (myDevice.getVersion().getFeatureLevel() < 26) {
      myUseLegacyTracking = true;
      myAllocationsInfos = new TIntObjectHashMap<>();
      myLegacyTrackers = new TIntObjectHashMap<>();
      myAllocationEvents = new TIntObjectHashMap<>();
      myAllocationDump = new TIntObjectHashMap<>();
      myAllocatedClasses = new TIntObjectHashMap<>();
      myAllocationStacks = new HashMap<>();
      myDataParsingLatches = new TIntObjectHashMap<>();
    }
  }

  public void startMonitoringApp(MemoryProfiler.MemoryStartRequest request,
                                 StreamObserver<MemoryProfiler.MemoryStartResponse> responseObserver) {
    if (myUseLegacyTracking && !myLegacyTrackers.contains(request.getProcessId())) {
      myLegacyTrackers.put(request.getProcessId(), myTrackerSupplier.apply(myDevice, request.getProcessId()));
      myAllocationsInfos.put(request.getProcessId(), new ArrayList<>());
      myAllocationEvents.put(request.getProcessId(), new TIntObjectHashMap());
      myAllocationDump.put(request.getProcessId(), new TIntObjectHashMap());
      myDataParsingLatches.put(request.getProcessId(), new TIntObjectHashMap());
    }

    responseObserver.onNext(myServiceStub.startMonitoringApp(request));
    responseObserver.onCompleted();
  }

  public void stopMonitoringApp(MemoryProfiler.MemoryStopRequest request,
                                StreamObserver<MemoryProfiler.MemoryStopResponse> responseObserver) {
    if (myUseLegacyTracking) {
      // TODO: also stop any unfinished tracking session
      myLegacyTrackers.remove(request.getProcessId());
      myAllocationsInfos.remove(request.getProcessId());
      myAllocationEvents.remove(request.getProcessId());
      myAllocationDump.remove(request.getProcessId());
      myDataParsingLatches.remove(request.getProcessId());
    }

    responseObserver.onNext(myServiceStub.stopMonitoringApp(request));
    responseObserver.onCompleted();
  }

  public void getData(MemoryProfiler.MemoryRequest request, StreamObserver<MemoryProfiler.MemoryData> responseObserver) {
    MemoryProfiler.MemoryData data = myServiceStub.getData(request);

    if (myUseLegacyTracking) {
      synchronized (myUpdatingDataLock) {
        List<AllocationsInfo> infos = myAllocationsInfos.get(request.getProcessId());
        MemoryProfiler.MemoryData.Builder rebuilder = data.toBuilder();
        long requestStartTime = request.getStartTime();
        long requestEndTime = request.getEndTime();

        // Note - the following is going to continuously return any unfinished whose start times are before the request's end time.
        // Dedeup is handled in MemoryDataPoller.
        AllocationsInfo key = AllocationsInfo.newBuilder().setEndTime(requestStartTime).build();
        int index =
          Collections.binarySearch(infos, key, (left, right) -> compareTimes(left.getEndTime(), right.getEndTime()));

        // If there is an exact match, move on to the next index as start time is treated as exclusive.
        index = index < 0 ? -(index + 1) : index + 1;
        for (int i = index; i < infos.size(); i++) {
          AllocationsInfo info = infos.get(i);
          if (info.getStartTime() > requestEndTime) {
            // The list is sorted by the info's start time in ascending order. Break as soon as we can.
            break;
          }

          rebuilder.addAllocationsInfo(info);
          rebuilder.setEndTimestamp(Math.max(rebuilder.getEndTimestamp(), Math.max(info.getStartTime(), info.getEndTime())));
        }

        data = rebuilder.build();
      }
    }

    responseObserver.onNext(data);
    responseObserver.onCompleted();
  }

  public void trackAllocations(TrackAllocationsRequest request,
                               StreamObserver<TrackAllocationsResponse> responseObserver) {
    int processId = request.getProcessId();
    long timestamp = request.getTimestamp();
    TrackAllocationsResponse response;
    if (myUseLegacyTracking) {
      response = request.getEnabled() ? enableAllocations(timestamp, processId) : disableAllocations(timestamp, processId);
    }
    else {
      // Post-O tracking - goes straight to perfd.
      response = myServiceStub.trackAllocations(TrackAllocationsRequest.newBuilder()
                                                  .setProcessId(request.getProcessId())
                                                  .setEnabled(request.getEnabled())
                                                  .build());
    }

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  /**
   * Note: this call is blocking if there is an existing completed AllocationsInfo sample that is in the parsing stage.
   */
  public void getAllocationEvents(AllocationEventsRequest request,
                                  StreamObserver<AllocationEventsResponse> responseObserver) {
    if (myUseLegacyTracking) {
      TIntObjectHashMap<CountDownLatch> latches = myDataParsingLatches.get(request.getProcessId());
      if (latches == null || request.getInfoId() >= latches.size()) {
        responseObserver.onNext(NOT_FOUND_RESPONSE);
      }
      else {
        try {
          latches.get(request.getInfoId()).await();
          synchronized (myUpdatingDataLock) {
            TIntObjectHashMap<AllocationEventsResponse> data = myAllocationEvents.get(request.getProcessId());
            assert data != null && data.size() > request.getInfoId();
            responseObserver.onNext(data.get(request.getInfoId()));
          }
        }
        catch (InterruptedException e) {
          getLogger().error("Exception while waiting for Allocation Tracking parsing results: " + e);
        }
      }
    }
    else {
      // Post-O tracking - goes straight to perfd.
      responseObserver.onNext(myServiceStub.getAllocationEvents(request));
    }
    responseObserver.onCompleted();
  }

  /**
   * Note: this call is blocking if there is an existing completed AllocationsInfo sample that is in the parsing stage.
   */
  public void getAllocationDump(DumpDataRequest request, StreamObserver<DumpDataResponse> responseObserver) {
    if (myUseLegacyTracking) {
      TIntObjectHashMap<CountDownLatch> latches = myDataParsingLatches.get(request.getProcessId());
      if (latches == null || request.getDumpId() >= latches.size()) {
        responseObserver.onNext(DumpDataResponse.newBuilder().setStatus(DumpDataResponse.Status.NOT_FOUND).build());
      }
      else {
        try {
          latches.get(request.getDumpId()).await();
          synchronized (myUpdatingDataLock) {
            TIntObjectHashMap<DumpDataResponse> data = myAllocationDump.get(request.getProcessId());
            assert data != null && data.size() > request.getDumpId();
            responseObserver.onNext(data.get(request.getDumpId()));
          }
        }
        catch (InterruptedException e) {
          getLogger().error("Exception while waiting for Allocation Tracking parsing results: " + e);
        }
      }
    }
    else {
      // Post-O tracking - goes straight to perfd.
      responseObserver.onNext(myServiceStub.getAllocationDump(request));
    }
    responseObserver.onCompleted();
  }

  public void listAllocationContexts(AllocationContextsRequest request,
                                     StreamObserver<AllocationContextsResponse> responseObserver) {
    if (myUseLegacyTracking) {
      AllocationContextsResponse.Builder builder = AllocationContextsResponse.newBuilder();
      request.getClassIdsList().forEach(id -> {
        if (myAllocatedClasses.contains(id)) {
          builder.addAllocatedClasses(myAllocatedClasses.get(id));
        }
        else {
          getLogger().debug("Class data cannot be found for id: " + id);
        }
      });
      request.getStackIdsList().forEach(id -> {
        if (myAllocationStacks.containsKey(id)) {
          builder.addAllocationStacks(myAllocationStacks.get(id));
        }
        else {
          getLogger().debug("Stack data cannot be found for id: " + id);
        }
      });
      responseObserver.onNext(builder.build());
    }
    else {
      // Post-O tracking - goes straight to perfd.
      responseObserver.onNext(myServiceStub.listAllocationContexts(request));
    }
    responseObserver.onCompleted();
  }

  private TrackAllocationsResponse enableAllocations(long startTimeNs, int processId) {
    synchronized (myUpdatingDataLock) {
      List<AllocationsInfo> infos = myAllocationsInfos.get(processId);
      AllocationsInfo lastInfo = infos.size() > 0 ? infos.get(infos.size() - 1) : null;
      if (lastInfo != null && lastInfo.getStatus() == IN_PROGRESS) {
        // A previous tracking is still in-progress, cannot enable a new one.
        return TrackAllocationsResponse.newBuilder().setStatus(TrackAllocationsResponse.Status.IN_PROGRESS).build();
      }

      TrackAllocationsResponse.Builder responseBuilder = TrackAllocationsResponse.newBuilder();
      LegacyAllocationTracker tracker = myLegacyTrackers.get(processId);
      boolean success = tracker.trackAllocations(infos.size(), startTimeNs, true, null, null);
      if (success) {
        int newInfoId = infos.size();
        AllocationsInfo newInfo = AllocationsInfo.newBuilder()
          .setInfoId(newInfoId)
          .setStartTime(startTimeNs)
          .setEndTime(DurationData.UNSPECIFIED_DURATION)
          .setStatus(IN_PROGRESS)
          .setLegacy(true)
          .build();
        responseBuilder.setInfo(newInfo);
        responseBuilder.setStatus(TrackAllocationsResponse.Status.SUCCESS);
        infos.add(newInfo);
      }
      else {
        responseBuilder.setStatus(TrackAllocationsResponse.Status.FAILURE_UNKNOWN);
      }

      return responseBuilder.build();
    }
  }

  private TrackAllocationsResponse disableAllocations(long endtimeNs, int processId) {
    synchronized (myUpdatingDataLock) {
      List<AllocationsInfo> infos = myAllocationsInfos.get(processId);
      TIntObjectHashMap<CountDownLatch> latches = myDataParsingLatches.get(processId);
      AllocationsInfo lastInfo = infos.size() > 0 ? infos.get(infos.size() - 1) : null;
      if (lastInfo == null || lastInfo.getStatus() != IN_PROGRESS) {
        // No in-progress tracking, cannot disable one.
        return TrackAllocationsResponse.newBuilder().setStatus(TrackAllocationsResponse.Status.NOT_ENABLED).build();
      }

      LegacyAllocationTracker tracker = myLegacyTrackers.get(processId);
      TrackAllocationsResponse.Builder responseBuilder = TrackAllocationsResponse.newBuilder();
      boolean success = tracker.trackAllocations(lastInfo.getInfoId(), endtimeNs, false, myFetchExecutor,
                                                 (bytes, classes, stacks, allocations) -> saveAllocationData(processId,
                                                                                                             lastInfo.getInfoId(),
                                                                                                             bytes, classes,
                                                                                                             stacks,
                                                                                                             allocations));
      AllocationsInfo.Builder lastInfoBuilder = lastInfo.toBuilder();
      lastInfoBuilder.setEndTime(endtimeNs);
      if (success) {
        lastInfoBuilder.setStatus(COMPLETED);
        responseBuilder.setStatus(TrackAllocationsResponse.Status.SUCCESS);
        latches.put(lastInfo.getInfoId(), new CountDownLatch(1));
      }
      else {
        lastInfoBuilder.setStatus(FAILURE_UNKNOWN);
        responseBuilder.setStatus(TrackAllocationsResponse.Status.FAILURE_UNKNOWN);
      }

      AllocationsInfo updatedInfo = lastInfoBuilder.build();
      responseBuilder.setInfo(updatedInfo);
      infos.set(infos.size() - 1, updatedInfo);
      return responseBuilder.build();
    }
  }

  private void saveAllocationData(int processId, int infoId,
                                  @Nullable byte[] rawBytes,
                                  @NotNull List<MemoryProfiler.AllocatedClass> classes,
                                  @NotNull List<MemoryProfiler.AllocationStack> stacks,
                                  @NotNull List<MemoryProfiler.AllocationEvent> events) {
    synchronized (myUpdatingDataLock) {
      TIntObjectHashMap<AllocationEventsResponse> responses = myAllocationEvents.get(processId);
      TIntObjectHashMap<DumpDataResponse> rawData = myAllocationDump.get(processId);
      TIntObjectHashMap<CountDownLatch> latches = myDataParsingLatches.get(processId);
      assert !responses.contains(infoId) && !rawData.contains(infoId) && latches.contains(infoId);

      AllocationEventsResponse.Builder eventResponseBuilder = AllocationEventsResponse.newBuilder();
      DumpDataResponse.Builder dumpResponseBuilder = DumpDataResponse.newBuilder();
      try {
        if (rawBytes == null) {
          eventResponseBuilder.setStatus(AllocationEventsResponse.Status.FAILURE_UNKNOWN);
          dumpResponseBuilder.setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN);
        }
        else {
          eventResponseBuilder.addAllEvents(events).setStatus(AllocationEventsResponse.Status.SUCCESS);
          dumpResponseBuilder.setData(ByteString.copyFrom(rawBytes)).setStatus(DumpDataResponse.Status.SUCCESS);
        }

        responses.put(infoId, eventResponseBuilder.build());
        rawData.put(infoId, dumpResponseBuilder.build());

        classes.forEach(klass -> myAllocatedClasses.put(klass.getClassId(), klass));
        stacks.forEach(stack -> myAllocationStacks.put(stack.getStackId(), stack));
      }
      finally {
        latches.get(infoId).countDown();
      }
    }
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
    overrides.put(MemoryServiceGrpc.METHOD_GET_ALLOCATION_EVENTS,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    getAllocationEvents((AllocationEventsRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(MemoryServiceGrpc.METHOD_LIST_ALLOCATION_CONTEXTS,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    listAllocationContexts((AllocationContextsRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(MemoryServiceGrpc.METHOD_GET_ALLOCATION_DUMP,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    getAllocationDump((DumpDataRequest)request, (StreamObserver)observer);
                  }));

    return generatePassThroughDefinitions(overrides, myServiceStub);
  }

  private static int compareTimes(long left, long right) {
    if (left == DurationData.UNSPECIFIED_DURATION) {
      return 1;
    }
    else if (right == DurationData.UNSPECIFIED_DURATION) {
      return -1;
    }
    else {
      return Long.compare(left, right);
    }
  }
}