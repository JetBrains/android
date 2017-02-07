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
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.DatastoreTable;
import com.android.tools.datastore.database.MemoryTable;
import com.android.tools.datastore.poller.MemoryDataPoller;
import com.android.tools.datastore.poller.PollRunner;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.google.protobuf3jarjar.ByteString;
import gnu.trove.TIntObjectHashMap;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

public class MemoryService extends MemoryServiceGrpc.MemoryServiceImplBase implements ServicePassThrough {

  private Map<Integer, PollRunner> myRunners = new HashMap<>();

  private MemoryServiceGrpc.MemoryServiceBlockingStub myPollingService;

  /**
   * Latches to track completion of the retrieval of dump data associated with each HeapDumpInfo.
   */
  private final TIntObjectHashMap<CountDownLatch> myHeapDumpDataLatches = new TIntObjectHashMap<>();
  private MemoryTable myMemoryTable = new MemoryTable();
  private Consumer<Runnable> myFetchExecutor;

  @VisibleForTesting
  // TODO Revisit fetch mechanism
  public MemoryService(Consumer<Runnable> fetchExecutor) {
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
    myRunners.put(processId, new MemoryDataPoller(processId, myMemoryTable, myPollingService, myFetchExecutor));
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
  public void getHeapDump(DumpDataRequest request, StreamObserver<DumpDataResponse> responseObserver) {
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
    responseObserver.onNext(myPollingService.trackAllocations(request));
    responseObserver.onCompleted();
  }

  @Override
  public void listAllocationContexts(AllocationContextsRequest request,
                                     StreamObserver<AllocationContextsResponse> responseObserver) {
    responseObserver.onNext(myMemoryTable.listAllocationContexts((request)));
    responseObserver.onCompleted();
  }

  @Override
  public void getAllocationDump(DumpDataRequest request, StreamObserver<DumpDataResponse> responseObserver) {
    DumpDataResponse.Builder responseBuilder = DumpDataResponse.newBuilder();

    AllocationsInfo response = myMemoryTable.getAllocationsInfo(request.getDumpId());
    if (response == null) {
      responseBuilder.setStatus(DumpDataResponse.Status.NOT_FOUND);
    }
    else if (response.getStatus() == AllocationsInfo.Status.FAILURE_UNKNOWN) {
      responseBuilder.setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN);
    }
    else {
      byte[] data = myMemoryTable.getAllocationDumpData(request.getDumpId());
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
  public void getAllocationEvents(AllocationEventsRequest request,
                                  StreamObserver<AllocationEventsResponse> responseObserver) {
    AllocationEventsResponse.Builder builder = AllocationEventsResponse.newBuilder();

    AllocationsInfo response = myMemoryTable.getAllocationsInfo(request.getInfoId());
    if (response == null) {
      builder.setStatus(AllocationEventsResponse.Status.NOT_FOUND);
    }
    else if (response.getStatus() == AllocationsInfo.Status.FAILURE_UNKNOWN) {
      builder.setStatus(AllocationEventsResponse.Status.FAILURE_UNKNOWN);
    }
    else {
      AllocationEventsResponse events = myMemoryTable.getAllocationData(request.getInfoId());
      if (events == null) {
        builder.setStatus(AllocationEventsResponse.Status.NOT_READY);
      }
      else {
        builder.mergeFrom(events);
      }
    }
    responseObserver.onNext(builder.build());
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