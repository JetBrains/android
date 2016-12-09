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
package com.android.tools.datastore.poller;

import com.android.tools.adtui.model.DurationData;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.LegacyAllocationTrackingService;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.AllocationEvent;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.AllocationsInfo;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.MemorySample;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.VmStatsSample;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.openapi.application.ApplicationManager;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.RunnableFuture;

import static com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse.Status.SUCCESS;

public class MemoryDataPoller extends MemoryServiceGrpc.MemoryServiceImplBase implements ServicePassThrough, PollRunner.PollingCallback {
  private final LegacyAllocationTrackingService myLegacyAllocationTrackingService;

  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;

  private MemoryServiceGrpc.MemoryServiceBlockingStub myPollingService;

  //TODO: Pull this into a storage container that can read/write this to disk
  //TODO: Rename MemoryData to MemoryProfilerData for consistency
  //TODO: Do these needs to be synchronized?
  protected final List<MemorySample> myMemoryData = new ArrayList<>();
  protected final List<VmStatsSample> myStatsData = new ArrayList<>();
  protected final List<HeapDumpSample> myHeapData = new ArrayList<>();
  protected final List<AllocationEvent> myAllocationEvents = new ArrayList<>();
  protected final List<AllocationsInfo> myAllocationsInfos = new ArrayList<>();
  protected final Map<String, AllocatedClass> myAllocatedClasses = new HashMap<>();
  protected final Map<ByteString, AllocationStack> myAllocationStacks = new HashMap<>();

  private final Object myUpdatingDataLock = new Object();
  private final Object myUpdatingAllocationsLock = new Object();

  private HeapDumpSample myPendingHeapDumpSample = null;

  private int myProcessId = -1;

  public MemoryDataPoller(@NotNull DataStoreService dataStoreService) {
    myLegacyAllocationTrackingService = new LegacyAllocationTrackingService(dataStoreService::getLegacyAllocationTracker);
  }

  @Override
  public RunnableFuture<Void> getRunner() {
    return new PollRunner(this, PollRunner.POLLING_DELAY_NS);
  }

  @Override
  public ServerServiceDefinition getService() {
    return bindService();
  }

  @Override
  public void connectService(ManagedChannel channel) {
    myPollingService = MemoryServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public void startMonitoringApp(MemoryStartRequest request, StreamObserver<MemoryStartResponse> observer) {
    synchronized (myUpdatingDataLock) {
      myMemoryData.clear();
      myStatsData.clear();
      myHeapData.clear();
    }
    myProcessId = request.getAppId();
    observer.onNext(myPollingService.startMonitoringApp(request));
    observer.onCompleted();
  }

  @Override
  public void stopMonitoringApp(MemoryStopRequest request, StreamObserver<MemoryStopResponse> observer) {
    myProcessId = -1;
    observer.onNext(myPollingService.stopMonitoringApp(request));
    observer.onCompleted();
  }

  @Override
  public void triggerHeapDump(TriggerHeapDumpRequest request, StreamObserver<TriggerHeapDumpResponse> responseObserver) {
    responseObserver.onNext(myPollingService.triggerHeapDump(request));
    responseObserver.onCompleted();
  }

  @Override
  public void getHeapDump(HeapDumpDataRequest request, StreamObserver<DumpDataResponse> responseObserver) {
    DumpDataResponse.Builder responseBuilder = DumpDataResponse.newBuilder();
    synchronized (myUpdatingDataLock) {
      int index = Collections
        .binarySearch(myHeapData, new HeapDumpSample(request.getDumpId()), (o1, o2) -> o1.myInfo.getDumpId() - o2.myInfo.getDumpId());
      if (index < 0) {
        responseBuilder.setStatus(DumpDataResponse.Status.NOT_FOUND);
      }
      else {
        HeapDumpSample dump = myHeapData.get(index);
        if (dump.isError) {
          responseBuilder.setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN);
        }
        else {
          ByteString data = dump.myData;
          if (data == null) {
            responseBuilder.setStatus(DumpDataResponse.Status.NOT_READY);
          }
          else {
            responseBuilder.setStatus(DumpDataResponse.Status.SUCCESS);
            responseBuilder.setData(data);
          }
        }
      }
    }
    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void listHeapDumpInfos(ListDumpInfosRequest request,
                                StreamObserver<ListHeapDumpInfosResponse> responseObserver) {
    HeapDumpSample key = new HeapDumpSample(HeapDumpInfo.newBuilder().setEndTime(request.getStartTime()).build());
    int index =
      Collections.binarySearch(myHeapData, key, (left, right) -> compareTimes(left.myInfo.getEndTime(), right.myInfo.getEndTime()));
    index = index < 0 ? -(index + 1) : index;
    ListHeapDumpInfosResponse.Builder responseBuilder = ListHeapDumpInfosResponse.newBuilder();
    for (int i = index; i < myHeapData.size(); i++) {
      HeapDumpSample sample = myHeapData.get(i);
      assert sample.myInfo.getEndTime() == DurationData.UNSPECIFIED_DURATION || request.getStartTime() <= sample.myInfo.getEndTime();
      if (sample.myInfo.getStartTime() <= request.getEndTime()) {
        responseBuilder.addInfos(sample.myInfo);
      }
    }
    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void trackAllocations(TrackAllocationsRequest request,
                               StreamObserver<TrackAllocationsResponse> responseObserver) {
    synchronized (myUpdatingAllocationsLock) {
      TrackAllocationsResponse response = myPollingService
        .trackAllocations(TrackAllocationsRequest.newBuilder().setAppId(myProcessId).setEnabled(request.getEnabled()).build());
      if (response.getStatus() == SUCCESS) {
        myLegacyAllocationTrackingService
          .trackAllocations(myProcessId, response.getTimestamp(), request.getEnabled(), (classes, stacks, allocations) -> {
            synchronized (myUpdatingDataLock) {
              classes.forEach(allocatedClass -> myAllocatedClasses.putIfAbsent(allocatedClass.getClassName(), allocatedClass));
              stacks.forEach(allocationStack -> myAllocationStacks.putIfAbsent(allocationStack.getStackId(), allocationStack));
              allocations.forEach(myAllocationEvents::add);
            }
          });
      }
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }
  }

  @Override
  public void listAllocationContexts(AllocationContextsRequest request,
                                     StreamObserver<AllocationContextsResponse> responseObserver) {
    AllocationContextsResponse.Builder responseBuilder = AllocationContextsResponse.newBuilder();
    myAllocationStacks.values().forEach(responseBuilder::addAllocationStacks);
    myAllocatedClasses.values().forEach(responseBuilder::addAllocatedClasses);
    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getData(MemoryRequest request, StreamObserver<MemoryData> responseObserver) {
    MemoryData.Builder response = MemoryData.newBuilder();
    long startTime = request.getStartTime();
    long endTime = request.getEndTime();

    //TODO: Optimize so we do not need to loop over all the data every request, ideally binary search to start time and loop till end.
    synchronized (myUpdatingDataLock) {
      myMemoryData.stream().filter(obj -> obj.getTimestamp() > startTime && obj.getTimestamp() <= endTime).forEach(response::addMemSamples);
      myStatsData.stream().filter(obj -> obj.getTimestamp() > startTime && obj.getTimestamp() <= endTime)
        .forEach(response::addVmStatsSamples);
      myHeapData.stream().filter(obj -> (obj.myInfo.getStartTime() > startTime && obj.myInfo.getStartTime() <= endTime) ||
                                        (obj.myInfo.getEndTime() > startTime && obj.myInfo.getEndTime() <= endTime))
        .forEach(obj -> response.addHeapDumpInfos(obj.myInfo));
      myAllocationsInfos.stream().filter(info -> (info.getStartTime() > startTime && info.getStartTime() <= endTime) ||
                                                 (info.getEndTime() > startTime && info.getEndTime() <= endTime))
        .forEach(response::addAllocationsInfo);
      myAllocationEvents.stream().filter(event -> event.getTimestamp() > startTime && event.getTimestamp() <= endTime)
        .forEach(response::addAllocationEvents);
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void poll() {
    MemoryRequest.Builder dataRequestBuilder = MemoryRequest.newBuilder()
      .setAppId(myProcessId)
      .setStartTime(myDataRequestStartTimestampNs)
      .setEndTime(Long.MAX_VALUE);
    MemoryData response = myPollingService.getData(dataRequestBuilder.build());

    synchronized (myUpdatingDataLock) {
      myMemoryData.addAll(response.getMemSamplesList());
      myStatsData.addAll(response.getVmStatsSamplesList());
      myAllocationEvents.addAll(response.getAllocationEventsList());

      if (response.getAllocationsInfoCount() > 0) {
        int startAppendIndex = 0;
        int lastEntryIndex = myAllocationsInfos.size() - 1;
        if (lastEntryIndex >= 0 && myAllocationsInfos.get(lastEntryIndex).getEndTime() == DurationData.UNSPECIFIED_DURATION) {
          AllocationsInfo lastOriginalEntry = myAllocationsInfos.get(lastEntryIndex);
          AllocationsInfo firstIncomingEntry = response.getAllocationsInfo(0);
          assert response.getAllocationsInfo(0).getStartTime() == lastOriginalEntry.getStartTime();
          myAllocationsInfos.set(lastEntryIndex, firstIncomingEntry);
          startAppendIndex = 1;
        }
        for (int i = startAppendIndex; i < response.getAllocationsInfoCount(); i++) {
          myAllocationsInfos.add(response.getAllocationsInfo(i));
        }
      }

      List<HeapDumpSample> dumpsToFetch = new ArrayList<>();
      for (int i = 0; i < response.getHeapDumpInfosCount(); i++) {
        if (myPendingHeapDumpSample != null) {
          assert i == 0;
          HeapDumpInfo info = response.getHeapDumpInfos(i);
          assert myPendingHeapDumpSample.myInfo.getDumpId() == info.getDumpId();
          assert info.getEndTime() != DurationData.UNSPECIFIED_DURATION;
          myPendingHeapDumpSample.myInfo = myPendingHeapDumpSample.myInfo.toBuilder().setEndTime(info.getEndTime()).build();
          dumpsToFetch.add(myPendingHeapDumpSample);
          myPendingHeapDumpSample = null;
        }
        else {
          HeapDumpInfo info = response.getHeapDumpInfos(i);
          HeapDumpSample sample = new HeapDumpSample(info);
          myHeapData.add(sample);

          if (info.getEndTime() == DurationData.UNSPECIFIED_DURATION) {
            // Note - there should be at most one unfinished heap dump request at a time. e.g. the final info from the response.
            assert i == response.getHeapDumpInfosCount() - 1;
            myPendingHeapDumpSample = sample;
          }
          else {
            dumpsToFetch.add(sample);
          }
        }
      }

      if (!dumpsToFetch.isEmpty()) {
        Runnable query = () -> {
          for (HeapDumpSample sample : dumpsToFetch) {
            DumpDataResponse dumpDataResponse = myPollingService.getHeapDump(
              HeapDumpDataRequest.newBuilder().setAppId(myProcessId).setDumpId(sample.myInfo.getDumpId()).build());
            synchronized (myUpdatingDataLock) {
              if (dumpDataResponse.getStatus() == DumpDataResponse.Status.SUCCESS) {
                sample.myData = dumpDataResponse.getData();
              }
              else {
                sample.isError = true;
              }
            }
          }
        };
        if (ApplicationManager.getApplication() != null ) {
          ApplicationManager.getApplication().executeOnPooledThread(query);
        } else { //Test Framework
          query.run();
        }
      }
    }
    if (response.getEndTimestamp() > myDataRequestStartTimestampNs) {
      myDataRequestStartTimestampNs = response.getEndTimestamp();
    }
  }

  private static int compareTimes(long left, long right) {
    if (left == DurationData.UNSPECIFIED_DURATION) {
      return 1;
    }
    else if (right == DurationData.UNSPECIFIED_DURATION) {
      return -1;
    }
    else {
      long diff = left - right;
      return diff == 0 ? 0 : (diff < 0 ? -1 : 1); // diff >> 63 sign extends value into a mask, the bit-or deals with 0+ case
    }
  }

  private static class HeapDumpSample {
    @NotNull public HeapDumpInfo myInfo;
    @Nullable public volatile ByteString myData = null;
    public volatile boolean isError = false;

    private HeapDumpSample(@NotNull HeapDumpInfo info) {
      myInfo = info;
    }

    public HeapDumpSample(int id) {
      myInfo = HeapDumpInfo.newBuilder().setDumpId(id).build();
    }
  }
}