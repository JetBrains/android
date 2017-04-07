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

import com.intellij.openapi.diagnostic.Logger;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;

public class PollRunner implements RunnableFuture<Void> {
  interface PollingCallback {
    void poll();
  }

  public static final long POLLING_DELAY_NS = TimeUnit.MILLISECONDS.toNanos(250);

  private static Logger getLog() { return Logger.getInstance(PollRunner.class); }

  private long myPollPeriodNs;

  private CountDownLatch myRunning = new CountDownLatch(1);

  private CountDownLatch myIsDone = new CountDownLatch(1);

  PollingCallback myPollingCallback;

  public PollRunner(PollingCallback pollCallback, long pollPeriodNs) {
    myPollingCallback = pollCallback;
    myPollPeriodNs = pollPeriodNs;

  }
  public void stop() {
    cancel(true);
    try {
      get();
    }
    catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void run() {
    try {
      while (myRunning.getCount() > 0) {
        long startTimeNs = System.nanoTime();
        try {
          myPollingCallback.poll();
        }
        catch (StatusRuntimeException ignored) {}
        long sleepTime = Math.max(myPollPeriodNs - (System.nanoTime() - startTimeNs), 0L);
        myRunning.await(sleepTime, TimeUnit.NANOSECONDS);
      }
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    finally {
      myIsDone.countDown();
    }
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    myRunning.countDown();
    return true;
  }

  @Override
  public boolean isCancelled() {
    return myRunning.getCount() == 0;
  }

  @Override
  public boolean isDone() {
    return myIsDone.getCount() == 0;
  }

  @Override
  public Void get() throws InterruptedException {
    myIsDone.await();
    return null;
  }

  @Override
  public Void get(long timeout, TimeUnit unit) throws InterruptedException {
    myIsDone.await(timeout, unit);
    return null;
  }
}
