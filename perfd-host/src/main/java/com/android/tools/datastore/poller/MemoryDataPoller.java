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

import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.RunnableFuture;

public class MemoryDataPoller extends MemoryServiceGrpc.MemoryServiceImplBase implements ServicePassThrough, PollRunner.PollingCallback {

  private static final int UNFINISHED_TIMESTAMP = -1;
  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;
  private MemoryServiceGrpc.MemoryServiceBlockingStub myPollingService;

  //TODO: Pull this into a storage container that can read/write this to disk
  //TODO: Rename MemoryData to MemoryProfilerData for consistency
  protected final List<MemoryProfiler.MemoryData.MemorySample> myMemoryData = new ArrayList<>();
  protected final List<MemoryProfiler.MemoryData.VmStatsSample> myStatsData = new ArrayList<>();
  protected final List<MemoryProfiler.MemoryData.HeapDumpSample> myHeapData = new ArrayList<>();
  private final Object myUpdatingDataLock = new Object();
  private MemoryProfiler.MemoryData.HeapDumpSample myPendingHeapDumpSample = null;
  private int myProcessId = -1;

  public MemoryDataPoller() {
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
  public void startMonitoringApp(MemoryProfiler.MemoryStartRequest request, StreamObserver<MemoryProfiler.MemoryStartResponse> observer) {
    myProcessId = request.getAppId();
    observer.onNext(myPollingService.startMonitoringApp(request));
    observer.onCompleted();
  }

  @Override
  public void stopMonitoringApp(MemoryProfiler.MemoryStopRequest request, StreamObserver<MemoryProfiler.MemoryStopResponse> observer) {
    myProcessId = -1;
    observer.onNext(myPollingService.stopMonitoringApp(request));
    observer.onCompleted();
  }

  @Override
  public void triggerHeapDump(MemoryProfiler.HeapDumpRequest request, StreamObserver<MemoryProfiler.HeapDumpResponse> responseObserver) {
    responseObserver.onNext(myPollingService.triggerHeapDump(request));
    responseObserver.onCompleted();
  }

  @Override
  public void getData(MemoryProfiler.MemoryRequest request, StreamObserver<MemoryProfiler.MemoryData> responseObserver) {
    MemoryProfiler.MemoryData.Builder response = MemoryProfiler.MemoryData.newBuilder();
    long startTime = request.getStartTime();
    long endTime = request.getEndTime();

    //TODO: Optimize so we do not need to loop all the data every request, ideally binary search to start time and loop till end.
    synchronized (myUpdatingDataLock) {
      for (MemoryProfiler.MemoryData.MemorySample obj : myMemoryData) {
        long current = obj.getTimestamp();
        if (current > startTime && current <= endTime) {
          response.addMemSamples(obj);
        }
      }
      for (MemoryProfiler.MemoryData.VmStatsSample obj : myStatsData) {
        long current = obj.getTimestamp();
        if (current > startTime && current <= endTime) {
          response.addVmStatsSamples(obj);
        }
      }
      for (MemoryProfiler.MemoryData.HeapDumpSample obj : myHeapData) {
        if (obj.getStartTime() > startTime && obj.getEndTime() <= endTime) {
          response.addHeapDumpSamples(obj);
        }
      }
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void poll() {
    MemoryProfiler.MemoryRequest.Builder dataRequestBuilder = MemoryProfiler.MemoryRequest.newBuilder()
      .setAppId(myProcessId)
      .setStartTime(myDataRequestStartTimestampNs)
      .setEndTime(Long.MAX_VALUE);
    MemoryProfiler.MemoryData response = myPollingService.getData(dataRequestBuilder.build());

    synchronized (myUpdatingDataLock) {
      myMemoryData.addAll(response.getMemSamplesList());
      myStatsData.addAll(response.getVmStatsSamplesList());
      List<MemoryProfiler.MemoryData.HeapDumpSample> pendingPulls = new ArrayList<>();
      for (int i = 0; i < response.getHeapDumpSamplesCount(); i++) {
        MemoryProfiler.MemoryData.HeapDumpSample sample = response.getHeapDumpSamples(i);
        if (myPendingHeapDumpSample != null) {
          // Note - if there is an existing pending heap dump, the first sample from the response should represent the same sample
          assert myPendingHeapDumpSample.getDumpId() == sample.getDumpId();
          pendingPulls.add(myPendingHeapDumpSample);
          myPendingHeapDumpSample = null;
        }
        else {
          myHeapData.add(sample);

          if (sample.getEndTime() == UNFINISHED_TIMESTAMP) {
            // Note - there should be at most one unfinished heap dump request at a time. e.g. the final sample from the response.
            assert i == response.getHeapDumpSamplesCount() - 1;
            myPendingHeapDumpSample = sample;
          }
          else {
            pendingPulls.add(sample);
          }
        }
      }

      //TODO: Fetch the file from downstream.
      //if (!pendingPulls.isEmpty()) {
      //  ApplicationManager.getApplication().executeOnPooledThread(() -> {
      //    for (MemoryProfiler.MemoryData.HeapDumpSample sample : pendingPulls) {
      //      File heapDumpFile = pullHeapDumpFile(sample);
      //      if (heapDumpFile != null) {
      //        myDataCache.addPulledHeapDumpFile(sample, heapDumpFile);
      //      }
      //    }
      //  });
      //}
    }
    if (response.getEndTimestamp() > myDataRequestStartTimestampNs) {
      myDataRequestStartTimestampNs = response.getEndTimestamp();
    }
  }
}