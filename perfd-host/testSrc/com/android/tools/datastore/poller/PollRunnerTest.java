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
package com.android.tools.datastore.poller;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class PollRunnerTest {

  private static final long TEST_PERIOD_NS = TimeUnit.MILLISECONDS.toNanos(1);

  @Test
  public void testRun() throws Exception {
    PollRunnerMinimalImpl runner = new PollRunnerMinimalImpl(10, TEST_PERIOD_NS);
    new Thread(runner).start();
    assertEquals(runner.isDone(), false);
    assertEquals(runner.isCancelled(), false);
    while(!runner.isDone()) {
      Thread.yield();
    }
    assertEquals(runner.passed(), true);
    runner.stop();
    assertEquals(runner.isDone(), true);
    assertEquals(runner.isCancelled(), true);
    assertEquals(runner.get(), null);
    assertEquals(runner.get(1, TimeUnit.SECONDS), null); // For code completion
  }

  private static class PollRunnerMinimalImpl extends PollRunner {
    private long myLastCallbackTime = 0;
    private long myTickCallCount = 0;
    private long myMinimumDelayNs = 0;
    private boolean myTestDone = false;
    private boolean myTestPassed = true;

    public PollRunnerMinimalImpl(int count, long minimumDelayNs) {
      super(POLLING_DELAY_NS);
      myTickCallCount = count;
      myMinimumDelayNs = minimumDelayNs;
    }

    @Override
    public boolean isDone() {
      return myTestDone;
    }

    public boolean passed() {
      return myTestPassed;
    }

    @Override
    public void poll() {
      long callbackTime = System.nanoTime();
      if (myLastCallbackTime != 0) {
        long diff = callbackTime - myLastCallbackTime;
        myTestPassed &= diff >= myMinimumDelayNs;
      }
      myLastCallbackTime = callbackTime;
      myTestDone = (myTickCallCount-- <= 0);
    }
  }
}
