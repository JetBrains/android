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

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

public class CpuThreadsModelTest {

  private FakeCpuService myCpuService = new FakeCpuService();
  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("CpuThreadsModelTest", myCpuService, new FakeProfilerService());
  private StudioProfilers myProfilers;
  private CpuThreadsModel myThreadsModel;
  private Range myRange;

  @Before
  public void setUp() {
    FakeTimer timer = new FakeTimer();
    myProfilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), timer);
    // One second must be enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myRange = new Range();
  }

  @Test
  public void updateRange() {
    myThreadsModel = new CpuThreadsModel(myRange, new CpuProfilerStage(myProfilers), ProfilersTestData.SESSION_DATA);
    // Make sure there are no threads before calling update
    assertThat(myThreadsModel.getSize()).isEqualTo(0);

    // Updates to a range with only one thread.
    myRange.set(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(5));
    assertThat(myThreadsModel.getSize()).isEqualTo(1);

    validateThread(0, 1, "Thread 1");

    // Updates to a range with two threads.
    myRange.set(TimeUnit.SECONDS.toMicros(5), TimeUnit.SECONDS.toMicros(10));
    assertThat(myThreadsModel.getSize()).isEqualTo(2);

    validateThread(0, 1, "Thread 1");
    validateThread(1, 2, "Thread 2");

    // Updates to a range with only one alive thread.
    myRange.set(TimeUnit.SECONDS.toMicros(10), TimeUnit.SECONDS.toMicros(15));
    assertThat(myThreadsModel.getSize()).isEqualTo(1);

    validateThread(0, 2, "Thread 2");

    // Updates (now backwards) to a range with only one alive thread.
    myRange.set(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(5));
    assertThat(myThreadsModel.getSize()).isEqualTo(1);

    validateThread(0, 1, "Thread 1");

    // Updates to a range with no alive threads.
    myRange.set(TimeUnit.SECONDS.toMicros(16), TimeUnit.SECONDS.toMicros(25));
    assertThat(myThreadsModel.getSize()).isEqualTo(0);
  }

  @Test
  public void testThreadsSorted() {
    //Add a few more threads.
    myCpuService.addAdditionalThreads(104, "Thread 100", new ArrayList<>());
    myCpuService.addAdditionalThreads(100, "Thread 100", new ArrayList<>());
    myCpuService.addAdditionalThreads(ProfilersTestData.SESSION_DATA.getPid(), "Main", new ArrayList<>());
    myCpuService.addAdditionalThreads(101, "RenderThread", new ArrayList<>());
    myCpuService.addAdditionalThreads(102, "A Named Thread", new ArrayList<>());
    myCpuService.addAdditionalThreads(103, "RenderThread", new ArrayList<>());
    // Updates to a range with all threads.
    myRange.set(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(10));

    // Create new model so we sort on our first queried range.
    myThreadsModel = new CpuThreadsModel(myRange, new CpuProfilerStage(myProfilers), ProfilersTestData.SESSION_DATA);

    assertThat(myThreadsModel.getSize()).isEqualTo(8);
    // Main thread gets sorted first per thread id passed into reset function.
    validateThread(0, ProfilersTestData.SESSION_DATA.getPid(), "Main");
    // After main thread we sort render threads. Within render threads they will be sorted by thread id.
    validateThread(1, 101, "RenderThread");
    validateThread(2, 103, "RenderThread");
    // After render threads we sort named threads. Within duplicated names we will sort by thread id.
    validateThread(3, 102, "A Named Thread");
    validateThread(4, 1, "Thread 1");
    validateThread(5, 100, "Thread 100");
    validateThread(6, 104, "Thread 100");
    validateThread(7, 2, "Thread 2");
  }

  @Test
  public void notEmptyWhenInitialized() {
    myRange.set(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(5));
    myThreadsModel = new CpuThreadsModel(myRange, new CpuProfilerStage(myProfilers), ProfilersTestData.SESSION_DATA);
    assertThat(myThreadsModel.getSize()).isEqualTo(1);
  }

  private void validateThread(int index, int threadId, String name) {
    CpuThreadsModel.RangedCpuThread thread = myThreadsModel.get(index);
    assertThat(thread).isNotNull();
    assertThat(thread.getThreadId()).isEqualTo(threadId);
    assertThat(thread.getName()).isEqualTo(name);
  }
}
