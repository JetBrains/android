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

  public CpuDataPoller(@NotNull Common.Session session,
                       @NotNull CpuTable table,
                       @NotNull CpuServiceGrpc.CpuServiceBlockingStub pollingService) {
    super(POLLING_DELAY_NS);
    myCpuTable = table;
    myPollingService = pollingService;
    mySession = session;
  }

  @Override
  public void poll() throws StatusRuntimeException {
    // Poll usage data.
    long getDataStartNs = myDataRequestStartTimestampNs;
    CpuProfiler.CpuDataRequest.Builder request = CpuProfiler.CpuDataRequest.newBuilder()
      .setSession(mySession)
      .setStartTimestamp(getDataStartNs)
      .setEndTimestamp(Long.MAX_VALUE);
    CpuProfiler.CpuDataResponse response = myPollingService.getData(request.build());
    for (CpuProfiler.CpuUsageData data : response.getDataList()) {
      getDataStartNs = Math.max(getDataStartNs, data.getEndTimestamp());
      myCpuTable.insert(mySession, data);
    }

    // Poll thread activities.
    long getThreadsStartNs = myDataRequestStartTimestampNs;
    CpuProfiler.GetThreadsRequest.Builder threadsRequest = CpuProfiler.GetThreadsRequest.newBuilder()
      .setSession(mySession)
      .setStartTimestamp(getThreadsStartNs)
      .setEndTimestamp(Long.MAX_VALUE);
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

    // Poll profiling state.
    CpuProfiler.ProfilingStateRequest profilingStateRequest = CpuProfiler.ProfilingStateRequest.newBuilder().setSession(mySession).build();
    myCpuTable.insertProfilingStateData(mySession, myPollingService.checkAppProfilingState(profilingStateRequest));

    // Poll trace info.
    CpuProfiler.GetTraceInfoRequest.Builder traceInfoRequest = CpuProfiler.GetTraceInfoRequest.newBuilder()
      .setSession(mySession)
      .setFromTimestamp(myTraceInfoRequestStartTimestampNs)
      .setToTimestamp(Long.MAX_VALUE);
    CpuProfiler.GetTraceInfoResponse traceInfoResponse = myPollingService.getTraceInfo(traceInfoRequest.build());
    for (CpuProfiler.TraceInfo traceInfo : traceInfoResponse.getTraceInfoList()) {
      if (traceInfo.getInitiationType().equals(CpuProfiler.TraceInitiationType.INITIATED_BY_API)) {
        // Insert trace content before inserting trace info. Because once the consumer of datastore (CpuProfilerStage) sees a
        // trace info, it may decide to automatically set and select it which requires the content is in the datastore.
        CpuProfiler.GetTraceRequest.Builder traceRequest =
          CpuProfiler.GetTraceRequest.newBuilder().setSession(mySession).setTraceId(traceInfo.getTraceId());
        CpuProfiler.GetTraceResponse traceResponse = myPollingService.getTrace(traceRequest.build());
        myCpuTable.insertTrace(mySession, traceInfo.getTraceId(), traceResponse.getProfilerType(), traceResponse.getData());
        // TODO(b/74358723): Revisit the logic to insert data into datastore.
        // Note the traceInfo returned by perfd is preliminary. For example, the start and end timestamps
        // are set when those events are perceived by perfd including the time spent by perfa waiting for the trace to
        // complete. They may be visibly different from the range inferred from trace content. When we work on b/74358723,
        // the trace will be automatically selected, and we will parse the trace right away. In that case, we should insert
        // the accurate traceInfo.
        myCpuTable.insertTraceInfo(mySession, traceInfo);
      }
    }
    myTraceInfoRequestStartTimestampNs = traceInfoResponse.getResponseTimestamp();

    myDataRequestStartTimestampNs = Math.max(Math.max(myDataRequestStartTimestampNs + 1, getDataStartNs), getThreadsStartNs);
  }

  @Override
  public void stop() {
    // Before stopping, we need to handle the case where we were profiling. That's done by inserting a ProfilingStateResponse with
    // being_profiled = false and max timestamp to the corresponding table. This way, we'll make sure that checkAppProfilingState will
    // return false next time we check if we're profiling in this session.
    CpuProfiler.ProfilingStateResponse response = CpuProfiler.ProfilingStateResponse.newBuilder()
      .setBeingProfiled(false)
      .setCheckTimestamp(Long.MAX_VALUE)
      .build();
    myCpuTable.insertProfilingStateData(mySession, response);
    super.stop();
  }
}
