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

import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.DataStoreService.BackingNamespace;
import com.android.tools.datastore.DeviceId;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.MemoryLiveAllocationTable;
import com.android.tools.datastore.database.MemoryStatsTable;
import com.android.tools.datastore.poller.MemoryDataPoller;
import com.android.tools.datastore.poller.MemoryJvmtiDataPoller;
import com.android.tools.datastore.poller.PollRunner;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.android.tools.datastore.DataStoreDatabase.Characteristic.PERFORMANT;

public class MemoryService extends MemoryServiceGrpc.MemoryServiceImplBase implements ServicePassThrough {
  private static final BackingNamespace LIVE_ALLOCATION_NAMESPACE = new BackingNamespace("LiveAllocations", PERFORMANT);

  private final Map<Integer, PollRunner> myRunners = new HashMap<>();
  private final Map<Integer, PollRunner> myJvmtiRunners = new HashMap<>();
  private final MemoryStatsTable myStatsTable;
  private final MemoryLiveAllocationTable myAllocationsTable;
  private final Consumer<Runnable> myFetchExecutor;
  private final DataStoreService myService;

  // TODO Revisit fetch mechanism
  public MemoryService(@NotNull DataStoreService dataStoreService,
                       Consumer<Runnable> fetchExecutor) {
    myFetchExecutor = fetchExecutor;
    myService = dataStoreService;
    myStatsTable = new MemoryStatsTable();
    myAllocationsTable = new MemoryLiveAllocationTable();
  }

  @Override
  public void startMonitoringApp(MemoryStartRequest request, StreamObserver<MemoryStartResponse> observer) {
    MemoryServiceGrpc.MemoryServiceBlockingStub client =
      myService.getMemoryClient(DeviceId.fromSession(request.getSession()));
    if (client != null) {
      observer.onNext(client.startMonitoringApp(request));
      observer.onCompleted();
      int processId = request.getProcessId();
      Common.Session session = request.getSession();
      myJvmtiRunners.put(processId, new MemoryJvmtiDataPoller(processId, session, myAllocationsTable, client));
      myRunners.put(processId, new MemoryDataPoller(processId, session, myStatsTable, client, myFetchExecutor));
      myFetchExecutor.accept(myJvmtiRunners.get(processId));
      myFetchExecutor.accept(myRunners.get(processId));
    }
    else {
      observer.onNext(MemoryStartResponse.getDefaultInstance());
      observer.onCompleted();
    }
  }

  @Override
  public void stopMonitoringApp(MemoryStopRequest request, StreamObserver<MemoryStopResponse> observer) {
    int processId = request.getProcessId();
    PollRunner runner = myRunners.remove(processId);
    if (runner != null) {
      runner.stop();
    }
    runner = myJvmtiRunners.remove(processId);
    if (runner != null) {
      runner.stop();
    }
    // Our polling service can get shutdown if we unplug the device.
    // This should be the only function that gets called as StudioProfilers attempts
    // to stop monitoring the last app it was monitoring.
    MemoryServiceGrpc.MemoryServiceBlockingStub service =
      myService.getMemoryClient(DeviceId.fromSession(request.getSession()));
    if (service == null) {
      observer.onNext(MemoryStopResponse.getDefaultInstance());
    }
    else {
      observer.onNext(service.stopMonitoringApp(request));
    }
    observer.onCompleted();
  }

  @Override
  public void triggerHeapDump(TriggerHeapDumpRequest request, StreamObserver<TriggerHeapDumpResponse> responseObserver) {
    MemoryServiceGrpc.MemoryServiceBlockingStub client =
      myService.getMemoryClient(DeviceId.fromSession(request.getSession()));
    TriggerHeapDumpResponse response = TriggerHeapDumpResponse.getDefaultInstance();
    if (client != null) {
      response = client.triggerHeapDump(request);
      // Saves off the HeapDumpInfo immediately instead of waiting for the MemoryDataPoller to pull it through, which can be delayed
      // and results in a NOT_FOUND status when the profiler tries to pull the dump's data in quick successions.
      if (response.getStatus() == TriggerHeapDumpResponse.Status.SUCCESS) {
        assert response.getInfo() != null;
        myStatsTable.insertOrReplaceHeapInfo(request.getProcessId(), request.getSession(), response.getInfo());
      }
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getHeapDump(DumpDataRequest request, StreamObserver<DumpDataResponse> responseObserver) {
    DumpDataResponse.Builder responseBuilder = DumpDataResponse.newBuilder();

    DumpDataResponse.Status status = myStatsTable.getHeapDumpStatus(request.getProcessId(), request.getSession(), request.getDumpTime());
    switch (status) {
      case SUCCESS:
        byte[] data = myStatsTable.getHeapDumpData(request.getProcessId(), request.getSession(), request.getDumpTime());
        assert data != null;
        responseBuilder.setData(ByteString.copyFrom(data));
        responseBuilder.setStatus(status);
        break;
      case NOT_READY:
      case FAILURE_UNKNOWN:
      case NOT_FOUND:
        responseBuilder.setStatus(status);
        break;
      default:
        responseBuilder.setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN);
        break;
    }

    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void listHeapDumpInfos(ListDumpInfosRequest request,
                                StreamObserver<ListHeapDumpInfosResponse> responseObserver) {
    ListHeapDumpInfosResponse.Builder responseBuilder = ListHeapDumpInfosResponse.newBuilder();
    List<HeapDumpInfo> dump = myStatsTable.getHeapDumpInfoByRequest(request.getProcessId(), request.getSession(), request);
    responseBuilder.addAllInfos(dump);
    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void trackAllocations(TrackAllocationsRequest request,
                               StreamObserver<TrackAllocationsResponse> responseObserver) {
    MemoryServiceGrpc.MemoryServiceBlockingStub client =
      myService.getMemoryClient(DeviceId.fromSession(request.getSession()));
    TrackAllocationsResponse response = TrackAllocationsResponse.getDefaultInstance();
    if (client != null) {
      response = client.trackAllocations(request);
      // Saves off the AllocationsInfo immediately instead of waiting for the MemoryDataPoller to pull it through, which can be delayed
      // and results in a NOT_FOUND status when the profiler tries to pull the info's data in quick successions.
      if (request.getEnabled() && response.getStatus() == TrackAllocationsResponse.Status.SUCCESS) {
        assert response.getInfo() != null;
        myStatsTable.insertOrReplaceAllocationsInfo(request.getProcessId(), request.getSession(), response.getInfo());
      }
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void suspendTrackAllocations(SuspendTrackAllocationsRequest request,
                                      StreamObserver<SuspendTrackAllocationsResponse> responseObserver) {
    MemoryServiceGrpc.MemoryServiceBlockingStub client =
      myService.getMemoryClient(DeviceId.fromSession(request.getSession()));
    SuspendTrackAllocationsResponse response = SuspendTrackAllocationsResponse.getDefaultInstance();
    if (client != null) {
      response = client.suspendTrackAllocations(request);
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void resumeTrackAllocations(ResumeTrackAllocationsRequest request,
                                     StreamObserver<ResumeTrackAllocationsResponse> responseObserver) {
    MemoryServiceGrpc.MemoryServiceBlockingStub client =
      myService.getMemoryClient(DeviceId.fromSession(request.getSession()));
    ResumeTrackAllocationsResponse response = ResumeTrackAllocationsResponse.getDefaultInstance();
    if (client != null) {
      response = client.resumeTrackAllocations(request);
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getLegacyAllocationContexts(LegacyAllocationContextsRequest request,
                                          StreamObserver<AllocationContextsResponse> responseObserver) {
    responseObserver.onNext(myStatsTable.getLegacyAllocationContexts(request));
    responseObserver.onCompleted();
  }

  @Override
  public void getLegacyAllocationDump(DumpDataRequest request, StreamObserver<DumpDataResponse> responseObserver) {
    DumpDataResponse.Builder responseBuilder = DumpDataResponse.newBuilder();

    AllocationsInfo response = myStatsTable.getAllocationsInfo(request.getProcessId(), request.getSession(), request.getDumpTime());
    if (response == null) {
      responseBuilder.setStatus(DumpDataResponse.Status.NOT_FOUND);
    }
    else if (response.getStatus() == AllocationsInfo.Status.FAILURE_UNKNOWN) {
      responseBuilder.setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN);
    }
    else {
      if (response.getLegacy()) {
        byte[] data = myStatsTable.getLegacyAllocationDumpData(request.getProcessId(), request.getSession(), request.getDumpTime());
        if (data == null) {
          responseBuilder.setStatus(DumpDataResponse.Status.NOT_READY);
        }
        else {
          responseBuilder.setStatus(DumpDataResponse.Status.SUCCESS);
          responseBuilder.setData(ByteString.copyFrom(data));
        }
      }
      else {
        // O+ allocation does not have legacy data.
        responseBuilder.setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN);
      }
    }
    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getStackFrameInfo(StackFrameInfoRequest request,
                                StreamObserver<StackFrameInfoResponse> responseObserver) {
    responseObserver.onNext(myAllocationsTable.getStackFrameInfo(request.getProcessId(), request.getSession(), request.getMethodId()));
    responseObserver.onCompleted();
  }

  @Override
  public void getLegacyAllocationEvents(LegacyAllocationEventsRequest request,
                                        StreamObserver<LegacyAllocationEventsResponse> responseObserver) {
    LegacyAllocationEventsResponse.Builder builder = LegacyAllocationEventsResponse.newBuilder();

    AllocationsInfo response = myStatsTable.getAllocationsInfo(request.getProcessId(), request.getSession(), request.getStartTime());
    if (response == null) {
      builder.setStatus(LegacyAllocationEventsResponse.Status.NOT_FOUND);
    }
    else if (response.getStatus() == AllocationsInfo.Status.FAILURE_UNKNOWN) {
      builder.setStatus(LegacyAllocationEventsResponse.Status.FAILURE_UNKNOWN);
    }
    else {
      if (response.getLegacy()) {
        LegacyAllocationEventsResponse events =
          myStatsTable.getLegacyAllocationData(request.getProcessId(), request.getSession(), request.getStartTime());
        if (events == null) {
          builder.setStatus(LegacyAllocationEventsResponse.Status.NOT_READY);
        }
        else {
          builder.mergeFrom(events);
        }
      }
      else {
        // O+ allocation does not have legacy data.
        builder.setStatus(LegacyAllocationEventsResponse.Status.FAILURE_UNKNOWN);
      }
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getData(MemoryRequest request, StreamObserver<MemoryData> responseObserver) {
    MemoryData response = myStatsTable.getData(request);
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getAllocations(AllocationSnapshotRequest request, StreamObserver<BatchAllocationSample> responseObserver) {
    BatchAllocationSample response;
    if (request.getLiveObjectsOnly()) {
      response = myAllocationsTable.getSnapshot(request.getProcessId(), request.getSession(), request.getEndTime());
    }
    else {
      response =
        myAllocationsTable.getAllocations(request.getProcessId(), request.getSession(), request.getStartTime(), request.getEndTime());
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getLatestAllocationTime(LatestAllocationTimeRequest request,
                                      StreamObserver<LatestAllocationTimeResponse> responseObserver) {
    LatestAllocationTimeResponse response =
      myAllocationsTable.getLatestDataTimestamp(request.getProcessId(), request.getSession());
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getAllocationContexts(AllocationContextsRequest request, StreamObserver<AllocationContextsResponse> responseObserver) {
    AllocationContextsResponse response =
      myAllocationsTable.getAllocationContexts(request.getProcessId(), request.getSession(), request.getStartTime(), request.getEndTime());
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void forceGarbageCollection(ForceGarbageCollectionRequest request, StreamObserver<ForceGarbageCollectionResponse> observer) {
    MemoryServiceGrpc.MemoryServiceBlockingStub client =
      myService.getMemoryClient(DeviceId.fromSession(request.getSession()));
    if (client != null) {
      observer.onNext(client.forceGarbageCollection(request));
    }
    else {
      observer.onNext(ForceGarbageCollectionResponse.getDefaultInstance());
    }
    observer.onCompleted();
  }

  @NotNull
  @Override
  public List<BackingNamespace> getBackingNamespaces() {
    return Arrays.asList(BackingNamespace.DEFAULT_SHARED_NAMESPACE, LIVE_ALLOCATION_NAMESPACE);
  }

  @Override
  public void setBackingStore(@NotNull BackingNamespace namespace, @NotNull Connection connection) {
    assert getBackingNamespaces().contains(namespace);
    if (namespace.equals(BackingNamespace.DEFAULT_SHARED_NAMESPACE)) {
      myStatsTable.initialize(connection);
    }
    else {
      myAllocationsTable.initialize(connection);
    }
  }
}