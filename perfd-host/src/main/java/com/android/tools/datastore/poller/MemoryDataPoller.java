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
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import gnu.trove.TIntObjectHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static com.android.tools.profiler.proto.MemoryProfiler.AllocationsInfo.Status.POST_PROCESS;

public class MemoryDataPoller extends PollRunner {

  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;

  private MemoryServiceGrpc.MemoryServiceBlockingStub myPollingService;

  private final TIntObjectHashMap<CountDownLatch> myLegacyAllocationsInfoLatches;

  /**
   * Latches to track completion of the retrieval of dump data associated with each HeapDumpInfo.
   */
  private final TIntObjectHashMap<CountDownLatch> myHeapDumpDataLatches;
  private HeapDumpInfo myPendingHeapDumpSample = null;
  private MemoryTable myMemoryTable;

  private int myProcessId = -1;
  private Consumer<Runnable> myFetchExecutor;

  public MemoryDataPoller(int processId,
                          MemoryTable table,
                          MemoryServiceGrpc.MemoryServiceBlockingStub pollingService,
                          TIntObjectHashMap<CountDownLatch> allocationLatches,
                          TIntObjectHashMap<CountDownLatch> heapDumpLatches,
                          Consumer<Runnable> fetchExecutor) {
    super(POLLING_DELAY_NS);
    myProcessId = processId;
    myMemoryTable = table;
    myPollingService = pollingService;
    myLegacyAllocationsInfoLatches = allocationLatches;
    myHeapDumpDataLatches = heapDumpLatches;
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
            DumpDataRequest.newBuilder().setProcessId(myProcessId).setDumpId(sample.getDumpId()).build());
          myMemoryTable.insertHeapDumpData(dumpDataResponse.getStatus(), sample, dumpDataResponse.getData());
          myHeapDumpDataLatches.get(sample.getDumpId()).countDown();
        }
      };
      myFetchExecutor.accept(query);
    }

    if (response.getEndTimestamp() > myDataRequestStartTimestampNs) {
      myDataRequestStartTimestampNs = response.getEndTimestamp();
    }
  }
}