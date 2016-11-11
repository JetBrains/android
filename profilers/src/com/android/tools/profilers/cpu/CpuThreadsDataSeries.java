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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.Range;
import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profilers.ProfilerClient;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 */
public class CpuThreadsDataSeries implements DataSeries<ThreadStatesDataModel> {
  @NotNull
  private ProfilerClient myClient;

  private boolean myCollectOtherCpuUsage;
  private final int myProcessId;

  public CpuThreadsDataSeries(@NotNull ProfilerClient client, boolean collectOtherCpuUsage, int id) {
    myClient = client;
    myCollectOtherCpuUsage = collectOtherCpuUsage;
    myProcessId = id;
  }

  @Override
  public ImmutableList<SeriesData<ThreadStatesDataModel>> getDataForXRange(@NotNull Range timeCurrentRangeUs) {
    CpuServiceGrpc.CpuServiceBlockingStub service = myClient.getCpuClient();
    CpuProfiler.CpuDataRequest.Builder dataRequestBuilder = CpuProfiler.CpuDataRequest.newBuilder()
      .setAppId(myProcessId)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMin()))
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)timeCurrentRangeUs.getMax()));
    CpuProfiler.CpuDataResponse response = service.getData(dataRequestBuilder.build());
    Map<Integer, SeriesData<ThreadStatesDataModel>> threadsStateData = new HashMap<>();
    for (CpuProfiler.CpuProfilerData data : response.getDataList()) {
      if (data.getDataCase() != CpuProfiler.CpuProfilerData.DataCase.THREAD_ACTIVITIES) {
        // No data to be handled.
        continue;
      }
      CpuProfiler.ThreadActivities threadActivities = data.getThreadActivities();
      if (threadActivities == null) {
        continue; // nothing to do
      }

      long dataTimestamp = TimeUnit.NANOSECONDS.toMicros(data.getBasicInfo().getEndTimestamp());
      for (CpuProfiler.ThreadActivity threadActivity : threadActivities.getActivitiesList()) {
        int tid = threadActivity.getTid();
        ThreadStatesDataModel threadData;
        if (!threadsStateData.containsKey(tid)) {
          SeriesData seriesData = new SeriesData(dataTimestamp, new ThreadStatesDataModel(threadActivity.getName(), threadActivity.getTid()));
          threadsStateData.put(tid, seriesData);
        }
        threadData = threadsStateData.get(tid).value;
        assert threadData != null;
        threadData.addState(threadActivity.getNewState(), TimeUnit.NANOSECONDS.toMicros(threadActivity.getTimestamp()));
      }
    }
    return ContainerUtil.immutableList(new ArrayList(threadsStateData.values()));
  }
}
