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

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CpuThreadsModelTest {

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("CpuThreadsModelTest", new FakeCpuService(), new FakeProfilerService());

  private CpuThreadsModel myThreadsModel;
  private Range myRange;

  @Before
  public void setUp() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), timer);
    // One second must be enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myRange = new Range();
    myThreadsModel = new CpuThreadsModel(myRange, new CpuProfilerStage(profilers), ProfilersTestData.SESSION_DATA);
  }

  @Test
  public void updateRange() {
    // Make sure there are no threads before calling update
    assertEquals(0, myThreadsModel.getSize());

    // Updates to a range with only one thread.
    myRange.set(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(5));
    assertEquals(1, myThreadsModel.getSize());

    CpuThreadsModel.RangedCpuThread thread1 = myThreadsModel.get(0);
    assertNotNull(thread1);
    assertEquals(1, thread1.getThreadId());
    assertEquals("Thread 1", thread1.getName());

    // Updates to a range with two threads.
    myRange.set(TimeUnit.SECONDS.toMicros(5), TimeUnit.SECONDS.toMicros(10));
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
    myRange.set(TimeUnit.SECONDS.toMicros(10), TimeUnit.SECONDS.toMicros(15));
    assertEquals(1, myThreadsModel.getSize());

    thread2 = myThreadsModel.get(0);
    assertNotNull(thread2);
    assertEquals(2, thread2.getThreadId());
    assertEquals("Thread 2", thread2.getName());

    // Updates (now backwards) to a range with only one alive thread.
    myRange.set(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(5));
    assertEquals(1, myThreadsModel.getSize());

    thread1 = myThreadsModel.get(0);
    assertNotNull(thread1);
    assertEquals(1, thread1.getThreadId());
    assertEquals("Thread 1", thread1.getName());

    // Updates to a range with no alive threads.
    myRange.set(TimeUnit.SECONDS.toMicros(16), TimeUnit.SECONDS.toMicros(25));
    assertEquals(0, myThreadsModel.getSize());
  }

  @Test
  public void notEmptyWhenInitialized() {
    myRange.set(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(5));
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), timer);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myThreadsModel = new CpuThreadsModel(myRange, new CpuProfilerStage(profilers), ProfilersTestData.SESSION_DATA);
    assertEquals(1, myThreadsModel.getSize());
  }
}
