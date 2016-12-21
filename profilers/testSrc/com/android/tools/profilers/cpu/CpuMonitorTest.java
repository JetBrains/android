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

import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.FakeGrpcChannel;
import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.*;

public class CpuMonitorTest {
  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("CpuMonitorTestChannel", new FakeCpuService());

  private StudioProfilers myProfilers;
  private CpuMonitor myMonitor;

  @Before
  public void setUp() throws Exception {
    myProfilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices());
    myMonitor = new CpuMonitor(myProfilers);
  }

  @Test
  public void testName() {
    assertEquals("CPU", myMonitor.getName());
  }

  @Test
  public void testExpand() {
    assertNull(myProfilers.getStage());
    myMonitor.expand();
    assertThat(myProfilers.getStage(), instanceOf(CpuProfilerStage.class));
  }

  private static class FakeCpuService extends CpuServiceGrpc.CpuServiceImplBase {
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
