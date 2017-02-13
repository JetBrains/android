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
import com.android.tools.datastore.database.MemoryTable;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.google.protobuf3jarjar.ByteString;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

public class MemoryDataPoller extends PollRunner {

  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;

  private MemoryServiceGrpc.MemoryServiceBlockingStub myPollingService;
  private AllocationsInfo myPendingAllocationSample = null;
  private HeapDumpInfo myPendingHeapDumpSample = null;
  private MemoryTable myMemoryTable;

  private int myProcessId = -1;
  // TODO: Key data off device session.
  private Common.Session mySession;
  private Consumer<Runnable> myFetchExecutor;

  public MemoryDataPoller(int processId,
                          Common.Session session,
                          MemoryTable table,
                          MemoryServiceGrpc.MemoryServiceBlockingStub pollingService,
                          Consumer<Runnable> fetchExecutor) {
    super(POLLING_DELAY_NS);
    myProcessId = processId;
    mySession = session;
    myMemoryTable = table;
    myPollingService = pollingService;
    myFetchExecutor = fetchExecutor;
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
    myMemoryTable.insertMemory(myProcessId, response.getMemSamplesList());
    myMemoryTable.insertVmStats(myProcessId, response.getVmStatsSamplesList());

    List<AllocationsInfo> allocDumpsToFetch = new ArrayList<>();
    for (int i = 0; i < response.getAllocationsInfoCount(); i++) {
      if (myPendingAllocationSample != null) {
        assert i == 0;
        AllocationsInfo info = response.getAllocationsInfo(i);
        assert myPendingAllocationSample.getStartTime() == info.getStartTime();
        // Deduping - ignoring identical ongoing tracking samples.
        if (info.getEndTime() == DurationData.UNSPECIFIED_DURATION) {
          assert response.getAllocationsInfoCount() == 1;
          break;
        }

        myMemoryTable.insertOrReplaceAllocationsInfo(myProcessId, info);
        allocDumpsToFetch.add(info);
        myPendingAllocationSample = null;
      }
      else {
        AllocationsInfo info = response.getAllocationsInfo(i);
        myMemoryTable.insertOrReplaceAllocationsInfo(myProcessId, info);
        if (info.getEndTime() == DurationData.UNSPECIFIED_DURATION) {
          // Note - there should be at most one unfinished allocation tracking info at a time. e.g. the final info from the response.
          assert i == response.getAllocationsInfoCount() - 1;
          myPendingAllocationSample = info;
        }
        else {
          allocDumpsToFetch.add(info);
        }
      }
    }

    List<HeapDumpInfo> heapDumpsToFetch = new ArrayList<>();
    for (int i = 0; i < response.getHeapDumpInfosCount(); i++) {
      if (myPendingHeapDumpSample != null) {
        assert i == 0;
        HeapDumpInfo info = response.getHeapDumpInfos(i);
        assert myPendingHeapDumpSample.getStartTime() == info.getStartTime();
        if (info.getEndTime() == DurationData.UNSPECIFIED_DURATION) {
          throw new RuntimeException("Invalid endTime: " + info.getEndTime() + " for Dump: " + info.getStartTime());
        }
        myMemoryTable.insertOrReplaceHeapInfo(myProcessId, info);
        heapDumpsToFetch.add(info);
        myPendingHeapDumpSample = null;
      }
      else {
        HeapDumpInfo info = response.getHeapDumpInfos(i);
        myMemoryTable.insertOrReplaceHeapInfo(myProcessId, info);
        if (info.getEndTime() == DurationData.UNSPECIFIED_DURATION) {
          // Note - there should be at most one unfinished heap dump request at a time. e.g. the final info from the response.
          assert i == response.getHeapDumpInfosCount() - 1;
          myPendingHeapDumpSample = info;
        }
        else {
          heapDumpsToFetch.add(info);
        }
      }
    }

    fetchAllocDumpData(allocDumpsToFetch);
    fetchHeapDumpData(heapDumpsToFetch);

    if (response.getEndTimestamp() > myDataRequestStartTimestampNs) {
      myDataRequestStartTimestampNs = response.getEndTimestamp();
    }
  }

  private void fetchAllocDumpData(@NotNull List<AllocationsInfo> dumpsToFetch) {
    if (dumpsToFetch.isEmpty()) {
      return;
    }

    Runnable query = () -> {
      HashSet<Integer> classesToFetch = new HashSet<>();
      HashSet<ByteString> stacksToFetch = new HashSet<>();
      HashMap<Long, AllocationEventsResponse> eventsToSave = new HashMap<>();
      for (AllocationsInfo sample : dumpsToFetch) {
        AllocationEventsResponse allocEventsResponse = myPollingService.getAllocationEvents(
          AllocationEventsRequest.newBuilder().setProcessId(myProcessId).setStartTime(sample.getStartTime()).setEndTime(sample.getEndTime())
            .build());
        eventsToSave.put(sample.getStartTime(), allocEventsResponse);

        allocEventsResponse.getEventsList().forEach(event -> {
          classesToFetch.add(event.getAllocatedClassId());
          stacksToFetch.add(event.getAllocationStackId());
        });

        // Also save out raw dump
        DumpDataResponse allocDumpResponse = myPollingService.getAllocationDump(
          DumpDataRequest.newBuilder().setProcessId(myProcessId).setDumpTime(sample.getStartTime()).build());
        myMemoryTable.updateAllocationDump(myProcessId, sample.getStartTime(), allocDumpResponse.getData().toByteArray());
      }

      // Note: the class/stack information are saved first to the table to avoid the events referencing yet-to-exist data
      // in the tables.
      AllocationContextsRequest contextRequest = AllocationContextsRequest.newBuilder()
        .addAllClassIds(classesToFetch).addAllStackIds(stacksToFetch).build();
      AllocationContextsResponse contextsResponse = myPollingService.listAllocationContexts(contextRequest);
      myMemoryTable.insertAllocationContext(contextsResponse.getAllocatedClassesList(), contextsResponse.getAllocationStacksList());
      eventsToSave.forEach((startTime, response) -> myMemoryTable.updateAllocationEvents(myProcessId, startTime, response));
    };
    myFetchExecutor.accept(query);
  }

  private void fetchHeapDumpData(@NotNull List<HeapDumpInfo> dumpsToFetch) {
    if (dumpsToFetch.isEmpty()) {
      return;
    }

    Runnable query = () -> {
      for (HeapDumpInfo sample : dumpsToFetch) {
        DumpDataResponse dumpDataResponse = myPollingService.getHeapDump(
          DumpDataRequest.newBuilder().setProcessId(myProcessId).setDumpTime(sample.getStartTime()).build());
        myMemoryTable.insertHeapDumpData(myProcessId, sample.getStartTime(), dumpDataResponse.getStatus(), dumpDataResponse.getData());
      }
    };
    myFetchExecutor.accept(query);
  }
}