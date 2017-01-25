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

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.model.DurationData;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.LegacyAllocationTrackingService;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.DatastoreTable;
import com.android.tools.datastore.database.MemoryTable;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.TIntObjectHashMap;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RunnableFuture;
import java.util.function.Consumer;

import static com.android.tools.profiler.proto.MemoryProfiler.AllocationsInfo.Status.COMPLETED;
import static com.android.tools.profiler.proto.MemoryProfiler.AllocationsInfo.Status.POST_PROCESS;
import static com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse.Status.SUCCESS;

public class MemoryDataPoller extends MemoryServiceGrpc.MemoryServiceImplBase implements ServicePassThrough, PollRunner.PollingCallback {

  private static Logger getLogger() {
    return Logger.getInstance(MemoryDataPoller.class);
  }

  private final LegacyAllocationTrackingService myLegacyAllocationTrackingService;

  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;

  private MemoryServiceGrpc.MemoryServiceBlockingStub myPollingService;
  /**
   * Legacy allocation tracking needs a post-process stage to wait and convert the jdwp data after stopping allocation tracking.
   * However this step is done in perfd-host instead of perfd, so we take a shortcut to update the statuses in our cached
   * {@link #myAllocationsInfos} directly (The current architecture does not query the same AllocationInfos sample from perfd after it has
   * completed, so even if we update the status in perfd, the poller might not see it again).
   *
   * These latches ensure we see the samples in the cache first before trying to update their statuses.
   */
  private final TIntObjectHashMap<CountDownLatch> myLegacyAllocationsInfoLatches = new TIntObjectHashMap<>();

  /**
   * Latches to track completion of the retrieval of dump data associated with each HeapDumpInfo.
   */
  private final TIntObjectHashMap<CountDownLatch> myHeapDumpDataLatches = new TIntObjectHashMap<>();
  private HeapDumpInfo myPendingHeapDumpSample = null;
  private MemoryTable myMemoryTable = new MemoryTable();

  private int myProcessId = -1;
  private Consumer<Runnable> myFetchExecutor;

  @VisibleForTesting
  // TODO Revisit fetch mechanism
  public MemoryDataPoller(@NotNull DataStoreService dataStoreService, Consumer<Runnable> fetchExecutor) {
    myLegacyAllocationTrackingService = new LegacyAllocationTrackingService(dataStoreService::getLegacyAllocationTracker);
    myFetchExecutor = fetchExecutor;
  }

  public MemoryDataPoller(@NotNull DataStoreService dataStoreService) {
    this(dataStoreService, r -> ApplicationManager.getApplication().executeOnPooledThread(r));
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
    myProcessId = request.getProcessId();
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
    TriggerHeapDumpResponse response = myPollingService.triggerHeapDump(request);
    if (response.getStatus() == TriggerHeapDumpResponse.Status.SUCCESS) {
      assert response.getInfo() != null;
      myHeapDumpDataLatches.put(response.getInfo().getDumpId(), new CountDownLatch(1));
    }

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getHeapDump(HeapDumpDataRequest request, StreamObserver<DumpDataResponse> responseObserver) {
    DumpDataResponse.Builder responseBuilder = DumpDataResponse.newBuilder();

    HeapDumpInfo.Builder infoBuilder = HeapDumpInfo.newBuilder();
    //TODO: Query for heapdump status before query for heap dump data.
    byte[] data = myMemoryTable.getHeapDumpData(request.getDumpId(), infoBuilder);
    HeapDumpInfo info = infoBuilder.build();
    if (info.equals(HeapDumpInfo.getDefaultInstance()) && data == null) {
      responseBuilder.setStatus(DumpDataResponse.Status.NOT_FOUND);
    }
    else if (myMemoryTable.getHeapDumpStatus(request.getDumpId()) == DumpDataResponse.Status.FAILURE_UNKNOWN) {
      responseBuilder.setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN);
    }
    else {
      if (data == null) {
        responseBuilder.setStatus(DumpDataResponse.Status.NOT_READY);
      }
      else {
        responseBuilder.setStatus(DumpDataResponse.Status.SUCCESS);
        responseBuilder.setData(ByteString.copyFrom(data));
      }
    }
    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void listHeapDumpInfos(ListDumpInfosRequest request,
                                StreamObserver<ListHeapDumpInfosResponse> responseObserver) {
    ListHeapDumpInfosResponse.Builder responseBuilder = ListHeapDumpInfosResponse.newBuilder();
    List<HeapDumpInfo> dump = myMemoryTable.getHeapDumpInfoByRequest(request);
    responseBuilder.addAllInfos(dump);
    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void trackAllocations(TrackAllocationsRequest request,
                               StreamObserver<TrackAllocationsResponse> responseObserver) {

    TrackAllocationsResponse response = myPollingService.trackAllocations(TrackAllocationsRequest.newBuilder()
                                                                            .setProcessId(myProcessId)
                                                                            .setEnabled(request.getEnabled())
                                                                            .setLegacyTracking(true).build());
    if (response.getStatus() == SUCCESS) {
      int infoId = response.getInfo().getInfoId();
      if (request.getEnabled()) {
        myLegacyAllocationsInfoLatches.put(infoId, new CountDownLatch(1));
        myLegacyAllocationTrackingService.trackAllocations(myProcessId, response.getTimestamp(), request.getEnabled(), null);
      }
      else {
        myLegacyAllocationTrackingService
          .trackAllocations(myProcessId, response.getTimestamp(), request.getEnabled(), (classes, stacks, allocations) -> {
            classes.forEach(allocatedClass -> myMemoryTable.insertIfNotExist(allocatedClass.getClassName(), allocatedClass));
            stacks.forEach(allocationStack -> myMemoryTable.insertIfNotExist(allocationStack.getStackId(), allocationStack));
            allocations.forEach(allocationEvent -> myMemoryTable.insert(allocationEvent));
            try {
              // Wait until the AllocationsInfo sample is already in the cache.
              assert myLegacyAllocationsInfoLatches.containsKey(infoId);
              myLegacyAllocationsInfoLatches.get(infoId).await();
              myLegacyAllocationsInfoLatches.remove(infoId);
              myMemoryTable.updateAllocationInfo(infoId, COMPLETED);
            }
            catch (InterruptedException e) {
              getLogger().debug("Exception while waiting on AllocationsInfo data.", e);
            }
          });
      }
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  /**
   * perfd-host only. This attempts to return the current status of an AllocationsInfo sample that is already in the cache.
   * If it is not already in the cache, a default instance of an AllocationsInfoStatusResponse is returned.
   */
  @Override
  public void getAllocationsInfoStatus(GetAllocationsInfoStatusRequest request,
                                       StreamObserver<GetAllocationsInfoStatusResponse> responseObserver) {
    GetAllocationsInfoStatusResponse response = myMemoryTable.getAllocationInfoStatus(request.getInfoId());
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void listAllocationContexts(AllocationContextsRequest request,
                                     StreamObserver<AllocationContextsResponse> responseObserver) {
    AllocationContextsResponse.Builder responseBuilder = AllocationContextsResponse.newBuilder();
    List<AllocationStack> stacks = myMemoryTable.getAllocationStacksForRequest(request);
    List<AllocatedClass> classes = myMemoryTable.getAllocatedClassesForRequest(request);
    responseBuilder.addAllAllocationStacks(stacks);
    responseBuilder.addAllAllocatedClasses(classes);
    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getData(MemoryRequest request, StreamObserver<MemoryData> responseObserver) {
    MemoryData response = myMemoryTable.getData(request);
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void poll() {
    MemoryRequest.Builder dataRequestBuilder = MemoryRequest.newBuilder()
      .setProcessId(myProcessId)
      .setStartTime(myDataRequestStartTimestampNs)
      .setEndTime(Long.MAX_VALUE);
    MemoryData response = myPollingService.getData(dataRequestBuilder.build());

    // TODO: A UI request may come in while mid way through the poll, this can cause us to have partial data
    // returned to the UI. This can be solved using transactions in the DB when this class is moved fully over.
    myMemoryTable.insertMemory(response.getMemSamplesList());
    myMemoryTable.insertVmStats(response.getVmStatsSamplesList());
    myMemoryTable.insertAllocation(response.getAllocationEventsList());

    if (response.getAllocationsInfoCount() > 0) {
      myMemoryTable.insertAndUpdateAllocationInfo(response.getAllocationsInfoList());
      for (AllocationsInfo info : response.getAllocationsInfoList()) {
        if (info.getStatus() == POST_PROCESS) {
          assert info.getLegacyTracking();
          assert myLegacyAllocationsInfoLatches.containsKey(info.getInfoId());
          myLegacyAllocationsInfoLatches.get(info.getInfoId()).countDown();
        }
      }
    }

    List<HeapDumpInfo> dumpsToFetch = new ArrayList<>();
    for (int i = 0; i < response.getHeapDumpInfosCount(); i++) {
      if (myPendingHeapDumpSample != null) {
        assert i == 0;
        HeapDumpInfo info = response.getHeapDumpInfos(i);
        assert myPendingHeapDumpSample.getDumpId() == info.getDumpId();
        if (info.getEndTime() == DurationData.UNSPECIFIED_DURATION) {
          throw new RuntimeException("Invalid endTime: " + +info.getEndTime() + " for DumpID: " + info.getDumpId());
        }
        myPendingHeapDumpSample = myPendingHeapDumpSample.toBuilder().setEndTime(info.getEndTime()).build();
        dumpsToFetch.add(myPendingHeapDumpSample);
        myPendingHeapDumpSample = null;
      }
      else {
        HeapDumpInfo info = response.getHeapDumpInfos(i);
        myMemoryTable.insert(info);
        if (info.getEndTime() == DurationData.UNSPECIFIED_DURATION) {
          // Note - there should be at most one unfinished heap dump request at a time. e.g. the final info from the response.
          assert i == response.getHeapDumpInfosCount() - 1;
          myPendingHeapDumpSample = info;
        }
        else {
          dumpsToFetch.add(info);
        }
      }
    }
    if (!dumpsToFetch.isEmpty()) {
      Runnable query = () -> {
        for (HeapDumpInfo sample : dumpsToFetch) {
          DumpDataResponse dumpDataResponse = myPollingService.getHeapDump(
            HeapDumpDataRequest.newBuilder().setProcessId(myProcessId).setDumpId(sample.getDumpId()).build());
          myMemoryTable.insertDumpData(dumpDataResponse.getStatus(), sample, dumpDataResponse.getData());
          myHeapDumpDataLatches.get(sample.getDumpId()).countDown();
        }
      };
      myFetchExecutor.accept(query);
    }

    if (response.getEndTimestamp() > myDataRequestStartTimestampNs) {
      myDataRequestStartTimestampNs = response.getEndTimestamp();
    }
  }


  private void updateAllocationsInfoLatches(@NotNull AllocationsInfo info) {
    if (info.getStatus() != POST_PROCESS) {
      return;
    }

    assert info.getLegacyTracking();
    assert myLegacyAllocationsInfoLatches.containsKey(info.getInfoId());
    myLegacyAllocationsInfoLatches.get(info.getInfoId()).countDown();
  }

  @Override
  public DatastoreTable getDatastoreTable() {
    return myMemoryTable;
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
}