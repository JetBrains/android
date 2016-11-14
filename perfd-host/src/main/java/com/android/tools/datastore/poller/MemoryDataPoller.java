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
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.MemorySample;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.VmStatsSample;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.openapi.application.ApplicationManager;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

public class MemoryDataPoller extends MemoryServiceGrpc.MemoryServiceImplBase implements ServicePassThrough, PollRunner.PollingCallback {
  private final DataStoreService myDataStoreService;

  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;

  private MemoryServiceGrpc.MemoryServiceBlockingStub myPollingService;

  private final ExecutorService myAllocationExecutorService =
    Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("memorypoller").build());

  // DO NOT call .get() on this future when inside synchronize block on myUpdatingDataLock.
  private volatile Future<?> myAllocationDumpFetch = null;

  //TODO: Pull this into a storage container that can read/write this to disk
  //TODO: Rename MemoryData to MemoryProfilerData for consistency
  //TODO: Do these needs to be synchronized?
  protected final List<MemorySample> myMemoryData = new ArrayList<>();
  protected final List<VmStatsSample> myStatsData = new ArrayList<>();
  protected final List<HeapDumpSample> myHeapData = new ArrayList<>();
  protected final List<AllocationDumpSample> myAllocationData = new ArrayList<>();

  private final Object myUpdatingDataLock = new Object();

  private HeapDumpSample myPendingHeapDumpSample = null;

  private int myProcessId = -1;

  public MemoryDataPoller(@NotNull DataStoreService dataStoreService) {
    myDataStoreService = dataStoreService;
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

  // TODO integrate this into getData
  @Override
  public void setAllocationTracking(AllocationTrackingRequest request,
                                    StreamObserver<AllocationTrackingResponse> responseObserver) {
    // TODO ensure only legacy or non-instrumented devices go through this path
    AllocationTrackingResponse.Builder responseBuilder = AllocationTrackingResponse.newBuilder();
    boolean getDump = false;

    if (myDataStoreService.getLegacyAllocationTracker() == null) {
      responseBuilder.setStatus(AllocationTrackingResponse.Status.FAILURE_UNKNOWN);
      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();
      return;
    }

    Future<?> pendingFetch = myAllocationDumpFetch; // TODO fix this stall when the user clicks too fast
    if (pendingFetch != null) {
      try {
        pendingFetch.get();
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        responseBuilder.setStatus(AllocationTrackingResponse.Status.FAILURE_UNKNOWN);
      }
      catch (ExecutionException e) {
        responseBuilder.setStatus(AllocationTrackingResponse.Status.FAILURE_UNKNOWN);
      }
    }

    synchronized (myUpdatingDataLock) {
      AllocationDumpSample sample = myAllocationData.isEmpty() ? null : myAllocationData.get(myAllocationData.size() - 1);
      if (request.getEnabled()) {
        if (!myAllocationData.isEmpty() && sample.myInfo.getEndTime() == DurationData.UNSPECIFIED_DURATION) {
          // Allocation tracking is already turned on.
          responseBuilder.setStatus(AllocationTrackingResponse.Status.IN_PROGRESS);
        }
        else {
          // A new allocation tracking is happening, so add a new entry to track it.
          // TODO remove the time from the request
          AllocationDumpInfo info =
            AllocationDumpInfo.newBuilder().setDumpId(myAllocationData.size()).setStartTime(request.getRequestTime())
              .setEndTime(DurationData.UNSPECIFIED_DURATION).setSuccess(true).build();
          sample = new AllocationDumpSample(info);
          myAllocationData.add(sample);
          responseBuilder.setInfo(info);
          responseBuilder.setStatus(AllocationTrackingResponse.Status.SUCCESS);
        }
      }
      else {
        if (myAllocationData.isEmpty() || sample.myInfo.getEndTime() != DurationData.UNSPECIFIED_DURATION) {
          responseBuilder.setStatus(AllocationTrackingResponse.Status.NOT_ENABLED).build();
        }
        else {
          sample.myInfo = sample.myInfo.toBuilder().setEndTime(request.getRequestTime()).build();
          responseBuilder.setStatus(AllocationTrackingResponse.Status.SUCCESS);
          getDump = true;
        }
      }

      if (responseBuilder.getStatus() == AllocationTrackingResponse.Status.SUCCESS) {
        boolean success =
          myDataStoreService.getLegacyAllocationTracker().setAllocationTrackingEnabled(request.getAppId(), request.getEnabled());
        responseObserver.onNext(
          responseBuilder.setStatus(success ? AllocationTrackingResponse.Status.SUCCESS : AllocationTrackingResponse.Status.FAILURE_UNKNOWN)
            .build());

        assert sample != null;
        final AllocationDumpSample finalSample = sample;
        AllocationDumpInfo.Builder lastInfoCopyBuilder = finalSample.myInfo.toBuilder();
        if (!success) {
          lastInfoCopyBuilder.setEndTime(lastInfoCopyBuilder.getStartTime());
          lastInfoCopyBuilder.setSuccess(false);
        }
        else if (getDump) {
          myAllocationDumpFetch =
            myAllocationExecutorService.submit(() -> {
              byte[] data = myDataStoreService.getLegacyAllocationTracker().getAllocationTrackingDump(request.getAppId());
              if (data == null) {
                lastInfoCopyBuilder.setSuccess(false);
              }
              else {
                finalSample.myData = ByteString.copyFrom(data);
              }
              myAllocationDumpFetch = null;
            });
        }
      }
      else {
        responseObserver.onNext(responseBuilder.build());
      }
      responseObserver.onCompleted();
    }
  }

  @Override
  public void getAllocationDump(AllocationDumpDataRequest request,
                                StreamObserver<DumpDataResponse> responseObserver) {
    DumpDataResponse.Builder responseBuilder = DumpDataResponse.newBuilder();
    AllocationDumpSample sample = null;
    synchronized (myUpdatingDataLock) {
      if (request.getDumpId() < 0 || request.getDumpId() >= myAllocationData.size()) {
        responseBuilder.setStatus(DumpDataResponse.Status.NOT_FOUND);
      }
      else {
        sample = myAllocationData.get(request.getDumpId());
      }
    }

    if (sample != null) {
      assert sample.myInfo.getDumpId() == request.getDumpId();
      Future<?> pendingFetch = myAllocationDumpFetch;
      if (!sample.myInfo.getSuccess()) {
        responseBuilder.setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN);
      }
      else if (sample.myData == null) {
        if (pendingFetch != null) {
          try {
            pendingFetch.get();
            myAllocationDumpFetch = null;
            if (!sample.myInfo.getSuccess()) {
              responseBuilder.setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN);
            }
            else {
              responseBuilder.setStatus(DumpDataResponse.Status.SUCCESS);
            }
          }
          catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseBuilder.setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN);
          }
          catch (ExecutionException e) {
            responseBuilder.setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN);
          }
        }
        else {
          responseBuilder.setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN);
        }
      }
      else {
        responseBuilder.setStatus(DumpDataResponse.Status.SUCCESS);
      }

      if (responseBuilder.getStatus() == DumpDataResponse.Status.SUCCESS) {
        ByteString data = sample.myData;
        assert data != null;
        responseBuilder.setData(data);
      }
    }

    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void listAllocationDumpInfos(ListDumpInfosRequest request,
                                  StreamObserver<ListAllocationDumpInfosResponse> responseObserver) {
    AllocationDumpSample key = new AllocationDumpSample(AllocationDumpInfo.newBuilder().setEndTime(request.getStartTime()).build());
    int index =
      Collections.binarySearch(myAllocationData, key, (left, right) -> compareTimes(left.myInfo.getEndTime(), right.myInfo.getEndTime()));
    index = index < 0 ? -(index + 1) : index;
    ListAllocationDumpInfosResponse.Builder responseBuilder = ListAllocationDumpInfosResponse.newBuilder();
    for (int i = index; i < myAllocationData.size(); i++) {
      AllocationDumpSample sample = myAllocationData.get(i);
      assert sample.myInfo.getEndTime() == DurationData.UNSPECIFIED_DURATION || request.getStartTime() <= sample.myInfo.getEndTime();
      if (sample.myInfo.getStartTime() <= request.getEndTime()) {
        responseBuilder.addInfos(sample.myInfo);
      }
    }
    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getData(MemoryRequest request, StreamObserver<MemoryData> responseObserver) {
    MemoryData.Builder response = MemoryData.newBuilder();
    long startTime = request.getStartTime();
    long endTime = request.getEndTime();

    //TODO: Optimize so we do not need to loop all the data every request, ideally binary search to start time and loop till end.
    synchronized (myUpdatingDataLock) {
      for (MemorySample obj : myMemoryData) {
        long current = obj.getTimestamp();
        if (current > startTime && current <= endTime) {
          response.addMemSamples(obj);
        }
      }
      for (VmStatsSample obj : myStatsData) {
        long current = obj.getTimestamp();
        if (current > startTime && current <= endTime) {
          response.addVmStatsSamples(obj);
        }
      }
      for (HeapDumpSample obj : myHeapData) {
        if (obj.myInfo.getStartTime() > startTime && obj.myInfo.getEndTime() <= endTime) {
          response.addHeapDumpInfos(obj.myInfo);
        }
      }
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
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
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
        });
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

  private static class AllocationDumpSample {
    @NotNull public AllocationDumpInfo myInfo;
    @Nullable public volatile ByteString myData = null;

    private AllocationDumpSample(@NotNull AllocationDumpInfo info) {
      myInfo = info;
    }
  }
}