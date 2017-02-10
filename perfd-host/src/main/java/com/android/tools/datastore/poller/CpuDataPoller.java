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
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import io.grpc.StatusRuntimeException;

/**
 * This class gathers sets up a CPUProfilerService and forward all commands to the connected channel with the exception of getData.
 * The get data command will pull data locally cached from the connected service.
 */
public class CpuDataPoller extends PollRunner {

  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;
  private CpuServiceGrpc.CpuServiceBlockingStub myPollingService;

  /**
   * Used to get device time.
   */
  private CpuTable myCpuTable;
  private int myProcessId = -1;

  public CpuDataPoller(int processId, CpuTable table, CpuServiceGrpc.CpuServiceBlockingStub pollingService) {
    super(POLLING_DELAY_NS);
    myProcessId = processId;
    myCpuTable = table;
    myPollingService = pollingService;
  }

  @Override
  public void poll() throws StatusRuntimeException {
    CpuProfiler.CpuDataRequest.Builder dataRequestBuilder = CpuProfiler.CpuDataRequest.newBuilder()
      .setProcessId(myProcessId)
      .setStartTimestamp(myDataRequestStartTimestampNs)
      .setEndTimestamp(Long.MAX_VALUE);
    CpuProfiler.CpuDataResponse response = myPollingService.getData(dataRequestBuilder.build());

    // TODO: Perfd should return all the thread activities data with the StartTime and EndTime already updated
    // to refelect the lifetime of the thread.
    for (CpuProfiler.CpuProfilerData data : response.getDataList()) {
      myDataRequestStartTimestampNs = data.getBasicInfo().getEndTimestamp();
      myCpuTable.insert(data);
      if (data.getDataCase() == CpuProfiler.CpuProfilerData.DataCase.THREAD_ACTIVITIES) {
        CpuProfiler.ThreadActivities activities = data.getThreadActivities();
        if (activities != null) {
          for (CpuProfiler.ThreadActivity activity : activities.getActivitiesList()) {
            int tid = activity.getTid();
            CpuProfiler.GetThreadsResponse.Thread.Builder builder =
              myCpuTable.getThreadResponseByIdOrNull(data.getBasicInfo().getProcessId(), tid);
            if (builder == null) {
              builder = CpuProfiler.GetThreadsResponse.Thread.newBuilder().setName(activity.getName()).setTid(tid);
            }
            CpuProfiler.ThreadActivity.State state = activity.getNewState();
            CpuProfiler.GetThreadsResponse.State converted = CpuProfiler.GetThreadsResponse.State.valueOf(state.toString());
            builder.addActivities(CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
                                    .setTimestamp(activity.getTimestamp())
                                    .setNewState(converted));
            myCpuTable.insertOrReplace(data.getBasicInfo().getProcessId(), tid, builder);
          }
        }
      }
    }
  }
}
