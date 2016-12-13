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
import com.android.tools.profilers.TestGrpcChannel;
import com.google.common.collect.ImmutableList;
import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.intellij.util.ObjectUtils.assertNotNull;
import static org.junit.Assert.assertEquals;

public class CpuThreadCountDataSeriesTest {

  @Rule
  public TestGrpcChannel<FakeCpuService> myGrpcChannel = new TestGrpcChannel<>("CpuThreadCountDataSeriesTest", new FakeCpuService());

  private CpuThreadCountDataSeries myDataSeries;

  @Before
  public void setUp() {
    myDataSeries = new CpuThreadCountDataSeries(myGrpcChannel.getProfilers().getClient().getCpuClient(), 1);
  }

  @Test
  public void noAliveThreadsInRange() {
    double rangeMin = TimeUnit.SECONDS.toMicros(20);
    double rangeMax = TimeUnit.SECONDS.toMicros(25);
    Range range = new Range(rangeMin, rangeMax);
    List<SeriesData<Long>> seriesDataList = myDataSeries.getDataForXRange(range);
    // When no threads are find within the requested range, we add the threads count (0)
    // to both range's min and max
    assertEquals(2, seriesDataList.size());
    SeriesData<Long> seriesData = seriesDataList.get(0);
    assertNotNull(seriesData);
    assertEquals(rangeMin, seriesData.x, 0);
    assertEquals(0, (long)seriesData.value);

    seriesData = seriesDataList.get(1);
    assertNotNull(seriesData);
    assertEquals(rangeMax, seriesData.x, 0);
    assertEquals(0, (long)seriesData.value);
  }

  @Test
  public void oneAliveThreadInRange() {
    Range range = new Range(TimeUnit.SECONDS.toMicros(10), TimeUnit.SECONDS.toMicros(11));
    List<SeriesData<Long>> seriesDataList = myDataSeries.getDataForXRange(range);
    assertEquals(2, seriesDataList.size());

    // Threads count by thread2 state change to RUNNING
    SeriesData<Long> seriesData = seriesDataList.get(0);
    assertNotNull(seriesData);
    assertEquals(TimeUnit.SECONDS.toMicros(6), seriesData.x, 0);
    assertEquals(1, (long)seriesData.value);

    // Threads count by thread2 state change to DEAD
    seriesData = seriesDataList.get(1);
    assertNotNull(seriesData);
    assertEquals(TimeUnit.SECONDS.toMicros(15), seriesData.x, 0);
    assertEquals(0, (long)seriesData.value);
  }

  @Test
  public void multipleAliveThreadInRange() {
    Range range = new Range(TimeUnit.SECONDS.toMicros(6), TimeUnit.SECONDS.toMicros(10));
    List<SeriesData<Long>> seriesDataList = myDataSeries.getDataForXRange(range);
    assertEquals(4, seriesDataList.size());

    // Threads count by thread1 state change to RUNNING
    SeriesData<Long> seriesData = seriesDataList.get(0);
    assertNotNull(seriesData);
    assertEquals(TimeUnit.SECONDS.toMicros(1), seriesData.x, 0);
    assertEquals(1, (long)seriesData.value); // Only thread1 is alive

    // Threads count by thread2 state change to RUNNING
    seriesData = seriesDataList.get(1);
    assertNotNull(seriesData);
    assertEquals(TimeUnit.SECONDS.toMicros(6), seriesData.x, 0);
    assertEquals(2, (long)seriesData.value); // Both threads are alive

    // Threads count by thread1 state change to DEAD
    seriesData = seriesDataList.get(2);
    assertNotNull(seriesData);
    assertEquals(TimeUnit.SECONDS.toMicros(8), seriesData.x, 0);
    assertEquals(1, (long)seriesData.value); // Only thread2 remains alive

    // Threads count by thread2 state change to DEAD
    seriesData = seriesDataList.get(3);
    assertNotNull(seriesData);
    assertEquals(TimeUnit.SECONDS.toMicros(15), seriesData.x, 0);
    assertEquals(0, (long)seriesData.value); // Both threads are dead now
  }

  @Test
  public void threadDiesBeforeRangeMax() {
    Range range = new Range(TimeUnit.SECONDS.toMicros(10), TimeUnit.SECONDS.toMicros(20));
    List<SeriesData<Long>> seriesDataList = myDataSeries.getDataForXRange(range);
    assertEquals(3, seriesDataList.size());

    // Threads count by thread2 state change to RUNNING
    SeriesData<Long> seriesData = seriesDataList.get(0);
    assertNotNull(seriesData);
    assertEquals(TimeUnit.SECONDS.toMicros(6), seriesData.x, 0);
    assertEquals(1, (long)seriesData.value); // thread2 is alive

    // Threads count by thread2 state change to DEAD
    seriesData = seriesDataList.get(1);
    assertNotNull(seriesData);
    assertEquals(TimeUnit.SECONDS.toMicros(15), seriesData.x, 0);
    assertEquals(0, (long)seriesData.value); // thread2 is dead now

    // Threads count by range.getMax(). This value is added when range.getMax()
    // is greater than the timestamp of the thread state change to DEAD
    seriesData = seriesDataList.get(2);
    assertNotNull(seriesData);
    assertEquals(range.getMax(), seriesData.x, 0);
    assertEquals(0, (long)seriesData.value); // thread2 is still dead
  }


  private static class FakeCpuService extends CpuServiceGrpc.CpuServiceImplBase {

    @Override
    public void getThreads(CpuProfiler.GetThreadsRequest request, StreamObserver<CpuProfiler.GetThreadsResponse> responseObserver) {
      CpuProfiler.GetThreadsResponse.Builder response = CpuProfiler.GetThreadsResponse.newBuilder();
      response.addAllThreads(buildThreads(request.getStartTimestamp(), request.getEndTimestamp()));

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    /**
     * Create two threads that overlap for certain amount of time.
     * They are referred as thread1 and thread2 in the comments present in the tests.
     *
     * Thread1 is alive from 1s to 8s, while thread2 is alive from 6s to 15s.
     */
    private static ImmutableList<CpuProfiler.GetThreadsResponse.Thread> buildThreads(long start, long end) {
      ImmutableList.Builder<CpuProfiler.GetThreadsResponse.Thread> threads = new ImmutableList.Builder<>();

      Range requestRange = new Range(start, end);

      Range thread1Range = new Range(TimeUnit.SECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(8));
      if (!thread1Range.getIntersection(requestRange).isEmpty()) {
        List<CpuProfiler.GetThreadsResponse.ThreadActivity> activitiesThread1 = new ArrayList<>();
        activitiesThread1.add(newActivity(TimeUnit.SECONDS.toNanos(1), CpuProfiler.GetThreadsResponse.State.RUNNING));
        activitiesThread1.add(newActivity(TimeUnit.SECONDS.toNanos(8), CpuProfiler.GetThreadsResponse.State.DEAD));
        threads.add(newThread(activitiesThread1));
      }

      Range thread2Range = new Range(TimeUnit.SECONDS.toNanos(6), TimeUnit.SECONDS.toNanos(15));
      if (!thread2Range.getIntersection(requestRange).isEmpty()) {
        List<CpuProfiler.GetThreadsResponse.ThreadActivity> activitiesThread2 = new ArrayList<>();
        activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(6), CpuProfiler.GetThreadsResponse.State.RUNNING));
        activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(10), CpuProfiler.GetThreadsResponse.State.WAITING));
        activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(15), CpuProfiler.GetThreadsResponse.State.DEAD));
        threads.add(newThread(activitiesThread2));
      }

      return threads.build();
    }

    private static CpuProfiler.GetThreadsResponse.ThreadActivity newActivity(long timestampNs, CpuProfiler.GetThreadsResponse.State state) {
      CpuProfiler.GetThreadsResponse.ThreadActivity.Builder activity = CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder();
      activity.setNewState(state);
      activity.setTimestamp(timestampNs);
      return activity.build();
    }

    private static CpuProfiler.GetThreadsResponse.Thread newThread(List<CpuProfiler.GetThreadsResponse.ThreadActivity> activities) {
      CpuProfiler.GetThreadsResponse.Thread.Builder thread = CpuProfiler.GetThreadsResponse.Thread.newBuilder();
      thread.addAllActivities(activities);
      return thread.build();
    }

  }
}
