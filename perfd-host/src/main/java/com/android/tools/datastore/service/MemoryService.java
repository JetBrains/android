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
package com.android.tools.datastore.service;

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.model.DurationData;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.LegacyAllocationTrackingService;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.DatastoreTable;
import com.android.tools.datastore.database.MemoryTable;
import com.android.tools.datastore.poller.MemoryDataPoller;
import com.android.tools.datastore.poller.PollRunner;
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

public class MemoryService extends MemoryServiceGrpc.MemoryServiceImplBase implements ServicePassThrough {

  private static Logger getLogger() {
    return Logger.getInstance(MemoryService.class);
  }

  private final LegacyAllocationTrackingService myLegacyAllocationTrackingService;

  private Map<Integer, PollRunner> myRunners = new HashMap<>();

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
  private MemoryTable myMemoryTable = new MemoryTable();
  private Consumer<Runnable> myFetchExecutor;

  @VisibleForTesting
  // TODO Revisit fetch mechanism
  public MemoryService(@NotNull DataStoreService dataStoreService, Consumer<Runnable> fetchExecutor) {
    myLegacyAllocationTrackingService = new LegacyAllocationTrackingService(dataStoreService::getLegacyAllocationTracker);
    myFetchExecutor = fetchExecutor;
  }

  @Override
  public void connectService(ManagedChannel channel) {
    myPollingService = MemoryServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public void startMonitoringApp(MemoryStartRequest request, StreamObserver<MemoryStartResponse> observer) {
    observer.onNext(myPollingService.startMonitoringApp(request));
    observer.onCompleted();
    int processId = request.getProcessId();
    myRunners.put(processId, new MemoryDataPoller(processId, myMemoryTable, myPollingService, myLegacyAllocationsInfoLatches, myHeapDumpDataLatches, myFetchExecutor));
    myFetchExecutor.accept(myRunners.get(processId));
  }

  @Override
  public void stopMonitoringApp(MemoryStopRequest request, StreamObserver<MemoryStopResponse> observer) {
    int processId = request.getProcessId();
    myRunners.remove(processId).stop();
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
    int processId = request.getProcessId();
    TrackAllocationsResponse response = myPollingService.trackAllocations(TrackAllocationsRequest.newBuilder()
                                                                            .setProcessId(processId)
                                                                            .setEnabled(request.getEnabled())
                                                                            .setLegacyTracking(true).build());
    if (response.getStatus() == SUCCESS) {
      int infoId = response.getInfo().getInfoId();
      if (request.getEnabled()) {
        myLegacyAllocationsInfoLatches.put(infoId, new CountDownLatch(1));
        myLegacyAllocationTrackingService.trackAllocations(processId, response.getTimestamp(), request.getEnabled(), null);
      }
      else {
        myLegacyAllocationTrackingService
          .trackAllocations(processId, response.getTimestamp(), request.getEnabled(), (classes, stacks, allocations) -> {
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
  public DatastoreTable getDatastoreTable() {
    return myMemoryTable;
  }
}