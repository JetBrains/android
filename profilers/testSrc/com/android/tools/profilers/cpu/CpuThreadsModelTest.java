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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CpuThreadsModelTest {

  @Rule
  public TestGrpcChannel<FakeCpuService> myGrpcChannel = new TestGrpcChannel<>("CpuThreadsModelTest", new FakeCpuService());

  private CpuThreadsModel myThreadsModel;

  @Before
  public void setUp() {
    myThreadsModel = new CpuThreadsModel(new CpuProfilerStage(myGrpcChannel.getProfilers()), 42 /* Any process id */);
  }

  @Test
  public void updateRange() {
    // Make sure there are no threads before calling update
    assertEquals(0, myThreadsModel.getSize());

    // Updates to a range with only one thread.
    myThreadsModel.update(new Range(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(5)));
    assertEquals(1, myThreadsModel.getSize());

    CpuThreadsModel.RangedCpuThread thread1 = myThreadsModel.get(0);
    assertNotNull(thread1);
    assertEquals(1, thread1.getThreadId());
    assertEquals("Thread 1", thread1.getName());

    // Updates to a range with two threads.
    myThreadsModel.update(new Range(TimeUnit.SECONDS.toMicros(5), TimeUnit.SECONDS.toMicros(10)));
    assertEquals(2, myThreadsModel.getSize());

    thread1 = myThreadsModel.get(0);
    assertNotNull(thread1);
    assertEquals(1, thread1.getThreadId());
    assertEquals("Thread 1", thread1.getName());

    CpuThreadsModel.RangedCpuThread thread2 = myThreadsModel.get(1);
    assertNotNull(thread2);
    assertEquals(2, thread2.getThreadId());
    assertEquals("Thread 2", thread2.getName());

    // Updates to a range with only one alive thread.
    myThreadsModel.update(new Range(TimeUnit.SECONDS.toMicros(10), TimeUnit.SECONDS.toMicros(15)));
    assertEquals(1, myThreadsModel.getSize());

    thread2 = myThreadsModel.get(0);
    assertNotNull(thread2);
    assertEquals(2, thread2.getThreadId());
    assertEquals("Thread 2", thread2.getName());

    // Updates (now backwards) to a range with only one alive thread.
    myThreadsModel.update(new Range(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(5)));
    assertEquals(1, myThreadsModel.getSize());

    thread1 = myThreadsModel.get(0);
    assertNotNull(thread1);
    assertEquals(1, thread1.getThreadId());
    assertEquals("Thread 1", thread1.getName());

    // Updates to a range with no alive threads.
    myThreadsModel.update(new Range(TimeUnit.SECONDS.toMicros(16), TimeUnit.SECONDS.toMicros(25)));
    assertEquals(0, myThreadsModel.getSize());
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
        threads.add(newThread(1, "Thread 1", activitiesThread1));
      }

      Range thread2Range = new Range(TimeUnit.SECONDS.toNanos(6), TimeUnit.SECONDS.toNanos(15));
      if (!thread2Range.getIntersection(requestRange).isEmpty()) {
        List<CpuProfiler.GetThreadsResponse.ThreadActivity> activitiesThread2 = new ArrayList<>();
        activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(6), CpuProfiler.GetThreadsResponse.State.RUNNING));
        activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(10), CpuProfiler.GetThreadsResponse.State.WAITING));
        activitiesThread2.add(newActivity(TimeUnit.SECONDS.toNanos(15), CpuProfiler.GetThreadsResponse.State.DEAD));
        threads.add(newThread(2, "Thread 2", activitiesThread2));
      }

      return threads.build();
    }

    private static CpuProfiler.GetThreadsResponse.ThreadActivity newActivity(long timestampNs, CpuProfiler.GetThreadsResponse.State state) {
      CpuProfiler.GetThreadsResponse.ThreadActivity.Builder activity = CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder();
      activity.setNewState(state);
      activity.setTimestamp(timestampNs);
      return activity.build();
    }

    private static CpuProfiler.GetThreadsResponse.Thread newThread(
      int tid, String name, List<CpuProfiler.GetThreadsResponse.ThreadActivity> activities) {
      CpuProfiler.GetThreadsResponse.Thread.Builder thread = CpuProfiler.GetThreadsResponse.Thread.newBuilder();
      thread.setTid(tid);
      thread.setName(name);
      thread.addAllActivities(activities);
      return thread.build();
    }
  }
}
