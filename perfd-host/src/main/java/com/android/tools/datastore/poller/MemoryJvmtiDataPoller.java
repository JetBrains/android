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

import com.android.tools.datastore.database.MemoryLiveAllocationTable;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.BatchAllocationSample;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryRequest;
import com.android.tools.profiler.proto.MemoryServiceGrpc;

public class MemoryJvmtiDataPoller extends PollRunner {
  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;

  private final MemoryServiceGrpc.MemoryServiceBlockingStub myPollingService;
  private final MemoryLiveAllocationTable myLiveAllocationTable;

  private int myProcessId = -1;
  private final Common.Session mySession;

  public MemoryJvmtiDataPoller(int processId,
                               Common.Session session,
                               MemoryLiveAllocationTable liveAllocationTable,
                               MemoryServiceGrpc.MemoryServiceBlockingStub pollingService) {
    super(POLLING_DELAY_NS);
    myProcessId = processId;
    mySession = session;
    myLiveAllocationTable = liveAllocationTable;
    myPollingService = pollingService;
  }

  @Override
  public void poll() {
    if (myProcessId == -1) {
      return;
    }

    MemoryRequest.Builder dataRequestBuilder = MemoryRequest.newBuilder()
      .setProcessId(myProcessId)
      .setStartTime(myDataRequestStartTimestampNs)
      .setEndTime(Long.MAX_VALUE);
    MemoryData response = myPollingService.getJvmtiData(dataRequestBuilder.build());

    for (BatchAllocationSample sample : response.getAllocationSamplesList()) {
      myLiveAllocationTable.insertMethodInfo(myProcessId, mySession, sample.getMethodsList());
      myLiveAllocationTable.insertStackInfo(myProcessId, mySession, sample.getStacksList());
      myLiveAllocationTable.insertThreadInfo(myProcessId, mySession, sample.getThreadInfosList());
      myLiveAllocationTable.insertAllocationData(myProcessId, mySession, sample);
    }
    for (MemoryProfiler.BatchJNIGlobalRefEvent batchJniEvent : response.getJniReferenceEventBatchesList()) {
      myLiveAllocationTable.insertJniReferenceData(myProcessId, mySession, batchJniEvent);
    }
    if (response.getEndTimestamp() > myDataRequestStartTimestampNs) {
      myDataRequestStartTimestampNs = response.getEndTimestamp();
    }
  }
}