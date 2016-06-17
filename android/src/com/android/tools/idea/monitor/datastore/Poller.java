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
package com.android.tools.idea.monitor.datastore;

import com.android.tools.idea.monitor.profilerclient.DeviceProfilerService;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;

public abstract class Poller implements RunnableFuture<Void> {
  @NotNull
  protected final DeviceProfilerService myService;

  private long myPollPeriodNs;

  private CountDownLatch myRunning = new CountDownLatch(1);

  private CountDownLatch myIsDone = new CountDownLatch(1);

  public Poller(@NotNull DeviceProfilerService service, long pollPeriodNs) {
    myService = service;
    myPollPeriodNs = pollPeriodNs;
  }

  protected abstract void asyncInit();

  protected abstract void asyncShutdown();

  protected abstract void poll();

  @Override
  public void run() {
    try {
      asyncInit();

      while (myRunning.getCount() > 0) {
        long startTimeNs = System.nanoTime();
        poll();

        long sleepTime = Math.max(myPollPeriodNs - (System.nanoTime() - startTimeNs), 0L);
        try {
          myRunning.await(sleepTime, TimeUnit.NANOSECONDS);
        }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    } finally {
      try {
        asyncShutdown();
      }
      finally {
        myIsDone.countDown();
      }
    }
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
