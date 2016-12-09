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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.TestGrpcChannel;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.intellij.util.containers.ImmutableList;
import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.*;

public class CpuMonitorTest {
  @Rule
  public TestGrpcChannel myGrpcChannel = new TestGrpcChannel<>("CpuMonitorTestChannel", new CpuServiceMock());

  private CpuMonitor myMonitor;

  @Before
  public void setUp() throws Exception {
    myMonitor = new CpuMonitor(myGrpcChannel.getProfilers());
  }

  @Test
  public void testThisProcessCpuUsage() throws IOException {
    CpuUsageDataSeries series = myMonitor.getThisProcessCpuUsage();
    ImmutableList<SeriesData<Long>> seriesDataList = series.getDataForXRange(new Range());
    assertEquals(1, seriesDataList.size()); // Only current process information.
    SeriesData<Long> seriesData = seriesDataList.get(0);
    assertNotNull(seriesData);
    assertEquals(20, (long)seriesData.value);
  }

  @Test
  public void testOtherProcessesCpuUsage() throws IOException {
    CpuUsageDataSeries series = myMonitor.getOtherProcessesCpuUsage();
    ImmutableList<SeriesData<Long>> seriesDataList = series.getDataForXRange(new Range());
    assertEquals(1, seriesDataList.size()); // Only other processes information.
    SeriesData<Long> seriesData = seriesDataList.get(0);
    assertNotNull(seriesData);
    assertEquals(40, (long)seriesData.value);
  }

  @Test
  public void testGetThreadsCount() throws IOException {
    CpuThreadCountDataSeries series = myMonitor.getThreadsCount();
    ImmutableList<SeriesData<Long>> seriesDataList = series.getDataForXRange(new Range());
    assertEquals(1, seriesDataList.size());
    SeriesData<Long> seriesData = seriesDataList.get(0);
    assertNotNull(seriesData);
    assertEquals(0, (long)seriesData.value); // No active threads
  }

  @Test
  public void testName() {
    assertEquals("CPU", myMonitor.getName());
  }

  @Test
  public void testExpand() {
    StudioProfilers profilers = myGrpcChannel.getProfilers();
    assertNull(profilers.getStage());
    myMonitor.expand();
    assertThat(profilers.getStage(), instanceOf(CpuProfilerStage.class));
  }

  private static class CpuServiceMock extends CpuServiceGrpc.CpuServiceImplBase {
    @Override
    public void getData(CpuProfiler.CpuDataRequest request, StreamObserver<CpuProfiler.CpuDataResponse> responseObserver) {
      CpuProfiler.CpuDataResponse.Builder response = CpuProfiler.CpuDataResponse.newBuilder();
      // Add first usage data
      CpuProfiler.CpuUsageData.Builder cpuUsageData = CpuProfiler.CpuUsageData.newBuilder();
      cpuUsageData.setElapsedTimeInMillisec(0);
      cpuUsageData.setSystemCpuTimeInMillisec(0);
      cpuUsageData.setAppCpuTimeInMillisec(0);
      CpuProfiler.CpuProfilerData.Builder data = CpuProfiler.CpuProfilerData.newBuilder().setCpuUsage(cpuUsageData);
      response.addData(data);

      // Add second usage data.
      cpuUsageData = CpuProfiler.CpuUsageData.newBuilder();
      cpuUsageData.setElapsedTimeInMillisec(100);
      // Total usage = 60% of elapsed time
      cpuUsageData.setSystemCpuTimeInMillisec(60);
      // Current app usage = 20% of elapsed time (i.e. other processes usage is 40%)
      cpuUsageData.setAppCpuTimeInMillisec(20);
      data = CpuProfiler.CpuProfilerData.newBuilder().setCpuUsage(cpuUsageData);
      response.addData(data);

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getThreads(CpuProfiler.GetThreadsRequest request, StreamObserver<CpuProfiler.GetThreadsResponse> responseObserver) {
      responseObserver.onNext(CpuProfiler.GetThreadsResponse.newBuilder().build());
      responseObserver.onCompleted();
    }
  }
}
