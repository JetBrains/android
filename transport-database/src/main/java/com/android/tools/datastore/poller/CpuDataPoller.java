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

import com.android.tools.datastore.LogService;
import com.android.tools.datastore.database.CpuTable;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.idea.io.grpc.StatusRuntimeException;
import com.android.tools.profiler.proto.Trace;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * This class gathers sets up a CPUProfilerService and forward all commands to the connected channel with the exception of getData.
 * The get data command will pull data locally cached from the connected service.
 */
public class CpuDataPoller extends PollRunner {
  // CPU usage data and thread activities are sampled by the same thread in perfd; therefore, the latest timestamp
  // from the two is an appropriate start point for the next sampling. It guarantees no lost of data, but there is a side
  // effect that we may query some time range more than once. For example,
  //      Collection 1 -> Poll 1 -> Collection 2 -> Poll 2 ... Poll 3
  // Poll 1 receives Sample 1 (obtained by Collect 1)
  // Poll 2 asks for data from range (Collect 1, Long.MAX_VALUE] and receives Sample 2
  // Poll 3 asks for data from range (Collect 2, Long.MAX_VALUE]
  //
  // Because trace operations are inserted by different threads on perfd, they need to be queried differently. Otherwise,
  // if there is a trace operation happening between Sample 2 and Poll 2, it will be included by both Poll 2 and 3.
  // Therefore, we keep a different timestamp to query trace operations.
  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;
  private long myTraceInfoRequestStartTimestampNs = Long.MIN_VALUE;

  @NotNull
  private final CpuServiceGrpc.CpuServiceBlockingStub myPollingService;
  @NotNull
  private final CpuTable myCpuTable;
  @NotNull
  private final Common.Session mySession;
  @NotNull
  private final LogService myLogService;

  public CpuDataPoller(@NotNull Common.Session session,
                       @NotNull CpuTable table,
                       @NotNull CpuServiceGrpc.CpuServiceBlockingStub pollingService,
                       @NotNull LogService logService) {
    super(POLLING_DELAY_NS);
    myCpuTable = table;
    myPollingService = pollingService;
    mySession = session;
    myLogService = logService;
  }

  @Override
  public void poll() throws StatusRuntimeException {
    // Poll usage data.
    long getDataStartNs = myDataRequestStartTimestampNs;
    CpuProfiler.CpuDataRequest.Builder request = CpuProfiler.CpuDataRequest
      .newBuilder().setSession(mySession).setStartTimestamp(getDataStartNs).setEndTimestamp(Long.MAX_VALUE);
    CpuProfiler.CpuDataResponse response = myPollingService.getData(request.build());
    for (Cpu.CpuUsageData data : response.getDataList()) {
      getDataStartNs = Math.max(getDataStartNs, data.getEndTimestamp());
      myCpuTable.insert(mySession, data);
    }

    // Poll thread activities.
    long getThreadsStartNs = myDataRequestStartTimestampNs;
    CpuProfiler.GetThreadsRequest.Builder threadsRequest = CpuProfiler.GetThreadsRequest
      .newBuilder().setSession(mySession).setStartTimestamp(getThreadsStartNs).setEndTimestamp(Long.MAX_VALUE);
    CpuProfiler.GetThreadsResponse threadsResponse = myPollingService.getThreads(threadsRequest.build());

    if (myDataRequestStartTimestampNs == Long.MIN_VALUE) {
      // Store the very first snapshot in the database.
      CpuProfiler.GetThreadsResponse.ThreadSnapshot snapshot = threadsResponse.getInitialSnapshot();
      getThreadsStartNs = Math.max(getThreadsStartNs, snapshot.getTimestamp());
      myCpuTable.insertSnapshot(mySession, snapshot.getTimestamp(), snapshot.getThreadsList());
    }

    // Store all the thread activities in the database.
    for (CpuProfiler.GetThreadsResponse.Thread thread : threadsResponse.getThreadsList()) {
      List<CpuProfiler.GetThreadsResponse.ThreadActivity> activities = thread.getActivitiesList();
      int count = thread.getActivitiesCount();
      if (count > 0) {
        CpuProfiler.GetThreadsResponse.ThreadActivity last = activities.get(count - 1);
        getThreadsStartNs = Math.max(getThreadsStartNs, last.getTimestamp());
      }

      myCpuTable.insertActivities(mySession, thread.getTid(), thread.getName(), activities);
    }

    // Poll trace info.
    CpuProfiler.GetTraceInfoRequest.Builder traceInfoRequest = CpuProfiler.GetTraceInfoRequest
      .newBuilder().setSession(mySession).setFromTimestamp(myTraceInfoRequestStartTimestampNs).setToTimestamp(Long.MAX_VALUE);
    CpuProfiler.GetTraceInfoResponse traceInfoResponse = myPollingService.getTraceInfo(traceInfoRequest.build());
    for (Trace.TraceInfo traceInfo : traceInfoResponse.getTraceInfoList()) {
      myCpuTable.insertTraceInfo(mySession, traceInfo);
      myTraceInfoRequestStartTimestampNs =
        Math.max(myTraceInfoRequestStartTimestampNs, Math.max(traceInfo.getFromTimestamp(), traceInfo.getToTimestamp()));
    }

    myDataRequestStartTimestampNs = Math.max(Math.max(myDataRequestStartTimestampNs + 1, getDataStartNs), getThreadsStartNs);
  }
}
