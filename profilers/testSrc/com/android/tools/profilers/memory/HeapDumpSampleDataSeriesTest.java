/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import static org.junit.Assert.assertEquals;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Memory.HeapDumpInfo;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class HeapDumpSampleDataSeriesTest {

  private static final HeapDumpInfo INFO1 = HeapDumpInfo.newBuilder()
    .setStartTime(TimeUnit.MICROSECONDS.toNanos(2))
    .setEndTime(TimeUnit.MICROSECONDS.toNanos(7))
    .build();
  private static final HeapDumpInfo INFO2 = HeapDumpInfo.newBuilder()
    .setStartTime(TimeUnit.MICROSECONDS.toNanos(17))
    .setEndTime(Long.MAX_VALUE)
    .build();

  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myTransportService = new FakeTransportService(myTimer);

  @NotNull private final FakeIdeProfilerServices myIdeProfilerServices = new FakeIdeProfilerServices();

  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("HeapDumpSampleDataSeriesTest", myTransportService);

  private MainMemoryProfilerStage myStage;

  @Before
  public void setUp() {
    myStage =
      new MainMemoryProfilerStage(new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myIdeProfilerServices, new FakeTimer()),
                                  new FakeCaptureObjectLoader());
  }

  @Test
  public void testGetDataForXRange() {
    myTransportService.addEventToStream(ProfilersTestData.SESSION_DATA.getStreamId(),
                                        ProfilersTestData.generateMemoryHeapDumpData(INFO1.getStartTime(), INFO1.getStartTime(), INFO1)
                                          .setPid(ProfilersTestData.SESSION_DATA.getPid())
                                          .build());
    myTransportService.addEventToStream(ProfilersTestData.SESSION_DATA.getStreamId(),
                                        ProfilersTestData.generateMemoryHeapDumpData(INFO2.getStartTime(), INFO2.getStartTime(), INFO2)
                                          .setPid(ProfilersTestData.SESSION_DATA.getPid())
                                          .build());

    DataSeries<CaptureDurationData<? extends CaptureObject>> series =
      CaptureDataSeries.ofHeapDumpSamples(new ProfilerClient(myGrpcChannel.getChannel()), ProfilersTestData.SESSION_DATA,
                                          myIdeProfilerServices.getFeatureTracker(), myStage);
    List<SeriesData<CaptureDurationData<? extends CaptureObject>>> dataList = series.getDataForRange(new Range(0, Double.MAX_VALUE));

    assertEquals(2, dataList.size());
    SeriesData<CaptureDurationData<? extends CaptureObject>> data1 = dataList.get(0);
    assertEquals(2, data1.x);
    assertEquals(5, data1.value.getDurationUs());
    CaptureObject capture1 = data1.value.getCaptureEntry().getCaptureObject();
    assertEquals(TimeUnit.MICROSECONDS.toNanos(2), capture1.getStartTimeNs());
    assertEquals(TimeUnit.MICROSECONDS.toNanos(7), capture1.getEndTimeNs());

    SeriesData<CaptureDurationData<? extends CaptureObject>> data2 = dataList.get(1);
    assertEquals(17, data2.x);
    assertEquals(Long.MAX_VALUE, data2.value.getDurationUs());
    CaptureObject capture2 = data2.value.getCaptureEntry().getCaptureObject();
    assertEquals(TimeUnit.MICROSECONDS.toNanos(17), capture2.getStartTimeNs());
    assertEquals(Long.MAX_VALUE, capture2.getEndTimeNs());
  }
}