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

import com.android.tools.datastore.database.CpuTable;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * This class gathers sets up a CPUProfilerService and forward all commands to the connected channel with the exception of getData.
 * The get data command will pull data locally cached from the connected service.
 */
public class CpuDataPoller extends PollRunner {

  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;

  @NotNull
  private final CpuServiceGrpc.CpuServiceBlockingStub myPollingService;
  @NotNull
  private final CpuTable myCpuTable;
  @NotNull
  private final Common.Session mySession;
  private int myProcessId = -1;

  public CpuDataPoller(int processId,
                       @NotNull Common.Session session,
                       @NotNull CpuTable table,
                       @NotNull CpuServiceGrpc.CpuServiceBlockingStub pollingService) {
    super(POLLING_DELAY_NS);
    myProcessId = processId;
    myCpuTable = table;
    myPollingService = pollingService;
    mySession = session;
  }

  @Override
  public void poll() throws StatusRuntimeException {
    long getDataStartNs = myDataRequestStartTimestampNs;
    CpuProfiler.CpuDataRequest.Builder request = CpuProfiler.CpuDataRequest.newBuilder()
      .setProcessId(myProcessId)
      .setStartTimestamp(getDataStartNs)
      .setEndTimestamp(Long.MAX_VALUE);
    CpuProfiler.CpuDataResponse response = myPollingService.getData(request.build());
    for (CpuProfiler.CpuProfilerData data : response.getDataList()) {
      getDataStartNs = Math.max(getDataStartNs, data.getBasicInfo().getEndTimestamp());
      myCpuTable.insert(mySession, data);
    }

    long getThreadsStartNs = myDataRequestStartTimestampNs;
    CpuProfiler.GetThreadsRequest.Builder threadsRequest = CpuProfiler.GetThreadsRequest.newBuilder()
      .setProcessId(myProcessId)
      .setStartTimestamp(getThreadsStartNs)
      .setEndTimestamp(Long.MAX_VALUE);
    CpuProfiler.GetThreadsResponse threadsResponse = myPollingService.getThreads(threadsRequest.build());

    if (myDataRequestStartTimestampNs == Long.MIN_VALUE) {
      // Store the very first snapshot in the database.
      CpuProfiler.GetThreadsResponse.ThreadSnapshot snapshot = threadsResponse.getInitialSnapshot();
      getThreadsStartNs = Math.max(getThreadsStartNs, snapshot.getTimestamp());
      myCpuTable.insertSnapshot(myProcessId, mySession, snapshot.getTimestamp(), snapshot.getThreadsList());
    }

    // Store all the thread activities in the database.
    for (CpuProfiler.GetThreadsResponse.Thread thread : threadsResponse.getThreadsList()) {
      List<CpuProfiler.GetThreadsResponse.ThreadActivity> activities = thread.getActivitiesList();
      int count = thread.getActivitiesCount();
      if (count > 0) {
        CpuProfiler.GetThreadsResponse.ThreadActivity last = activities.get(count - 1);
        getThreadsStartNs = Math.max(getThreadsStartNs, last.getTimestamp());
      }

      myCpuTable.insertActivities(myProcessId, mySession, thread.getTid(), thread.getName(), activities);
    }
    myDataRequestStartTimestampNs = Math.max(Math.max(myDataRequestStartTimestampNs + 1, getDataStartNs), getThreadsStartNs);
  }
}
