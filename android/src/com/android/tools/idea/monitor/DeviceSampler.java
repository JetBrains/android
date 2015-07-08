/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.monitor;

import com.android.ddmlib.Client;
import com.android.tools.chartlib.TimelineData;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public abstract class DeviceSampler implements Runnable {
  /**
   * Sample type when the device cannot be seen.
   */
  public static final int TYPE_UNREACHABLE = 0;
  /**
   * Sample created from a valid response.
   */
  public static final int TYPE_DATA = 1;
  /**
   * The device is reachable but no response was received in time.
   */
  public static final int TYPE_TIMEOUT = 2;
  /**
   * This is the valid start index for inherited classes.
   */
  public static final int INHERITED_TYPE_START = 3;

  @NotNull protected TimelineData myData;
  @NotNull protected final List<TimelineEventListener> myListeners = Lists.newLinkedList();
  protected int mySampleFrequencyMs;
  /**
   * The future representing the task being executed, which will return null upon successful completion.
   * If null, no current task is being executed.
   */
  @Nullable protected volatile Future<?> myExecutingTask;
  @Nullable protected volatile Client myClient;
  @NotNull private final Semaphore myDataSemaphore;
  protected volatile boolean myRunning;

  public DeviceSampler(@NotNull TimelineData data, int sampleFrequencyMs) {
    myData = data;
    mySampleFrequencyMs = sampleFrequencyMs;
    myDataSemaphore = new Semaphore(0, true);
  }

  @SuppressWarnings("ConstantConditions")
  public void start() {
    if (myExecutingTask == null && myClient != null) {
      myRunning = true;
      myExecutingTask = ApplicationManager.getApplication().executeOnPooledThread(this);
      myClient.setHeapInfoUpdateEnabled(true);

      for (TimelineEventListener listener : myListeners) {
        listener.onStart();
      }
    }
  }

  @SuppressWarnings("ConstantConditions")
  public void stop() {
    if (myExecutingTask != null) {
      myRunning = false;
      myDataSemaphore.release();
      myData.clear();

      try {
        // Wait for the task to finish.
        myExecutingTask.get();
      }
      catch (InterruptedException e) {
        // Ignore
      }
      catch (ExecutionException e) {
        // Rethrow the original cause of the exception on this thread.
        throw new RuntimeException(e.getCause());
      }

      if (myClient != null) {
        myClient.setHeapInfoUpdateEnabled(false);
      }
      myExecutingTask = null;
      for (TimelineEventListener listener : myListeners) {
        listener.onStop();
      }
    }
  }

  public void setClient(@Nullable Client client) {
    if (client != myClient) {
      stop();
      myClient = client;
      myData.clear();
      start();
    }
  }

  @Nullable
  /**
   * This method returns a local copy of <code>myClient</code>, as it is volatile.
   */
  public Client getClient() {
    return myClient;
  }

  public void addListener(TimelineEventListener listener) {
    myListeners.add(listener);
  }

  public boolean isRunning() {
    return myExecutingTask != null && myRunning;
  }

  protected void forceSample() {
    myDataSemaphore.release();
  }

  @Override
  public void run() {
    long timeToWait = mySampleFrequencyMs;
    while (myRunning) {
      try {
        long start = System.currentTimeMillis();
        boolean acquired = myDataSemaphore.tryAcquire(timeToWait, TimeUnit.MILLISECONDS);
        if (myRunning) {
          sample(acquired);
        }
        timeToWait -= System.currentTimeMillis() - start;
        if (timeToWait <= 0) {
          timeToWait = mySampleFrequencyMs;
        }
      }
      catch (InterruptedException e) {
        myRunning = false;
      }
    }
  }

  @NotNull
  public abstract String getName();

  @NotNull
  public abstract String getDescription();

  protected abstract void sample(boolean forced) throws InterruptedException;
}
