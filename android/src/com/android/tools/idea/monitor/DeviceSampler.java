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
import com.android.tools.adtui.TimelineData;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A base sampling method to abstract common parts of sampling (such as task lifetime management).
 * This class is conditionally thread-safe, in that all internal to this class are thread-safe, and
 * are generally thread-safe with respect to each other. However, users of this class must be sure
 * to have a consistent view of all data throughout its processing (e.g. cache the values locally
 * so that they are immune to changes during the lifetime of the method).
 */
public abstract class DeviceSampler {
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

  @NotNull protected final List<TimelineEventListener> myListeners = new ArrayList<>();

  protected final int mySampleFrequencyMs;

  @NotNull private volatile TimelineData myTimelineData;

  @Nullable private volatile Client myClient;

  @NotNull private final Semaphore myDataSemaphore;

  @Nullable private volatile CountDownLatch myTaskFinished;

  /**
   * The future representing the task being executed, which will return null upon successful completion.
   * If null, no current task is being executed.
   */
  @Nullable private volatile Future<?> myExecutingTask;

  private volatile boolean myRunning;

  private volatile boolean myIsPaused;

  public DeviceSampler(@NotNull TimelineData timelineData, int sampleFrequencyMs) {
    myTimelineData = timelineData;
    mySampleFrequencyMs = sampleFrequencyMs;
    myDataSemaphore = new Semaphore(0, true);
  }

  public void start() {
    startSamplingTask();
  }

  public void stop() {
    stopSamplingTask();
  }

  @NotNull
  public final TimelineData getTimelineData() {
    return myTimelineData;
  }

  public final void setTimelineData(@NotNull TimelineData timelineData) {
    myTimelineData = timelineData;
  }

  public synchronized final void setClient(@Nullable Client client) {
    if (requiresSamplerRestart(client)) {
      stop();
      myClient = client;
      prepareSampler();
      myTimelineData.clear();
      if (!myIsPaused) {
        start();
      }
    }
  }

  public synchronized final void setIsPaused(boolean paused) {
    myIsPaused = paused;
    if (myIsPaused) {
      if (myClient != null) {
        stop();
      }
    }
    else {
      myTimelineData.clear();
      prepareSampler();
      start();
    }
  }

  public final boolean getIsPaused() {
    return myIsPaused;
  }

  /**
   * This method returns a local copy of <code>myClient</code>, as it is volatile.
   */
  @Nullable
  public Client getClient() {
    return myClient;
  }

  @NotNull
  public abstract String getName();

  public void addListener(TimelineEventListener listener) {
    myListeners.add(listener);
  }

  public boolean isRunning() {
    return myExecutingTask != null && myRunning;
  }

  protected boolean requiresSamplerRestart(@Nullable Client client) {
    return client != myClient;
  }

  /**
   * Do device/client-specific preparations for sampler restart.
   */
  protected synchronized void prepareSampler() {
  }

  protected void forceSample() {
    myDataSemaphore.release();
  }

  protected abstract void sample(boolean forced) throws InterruptedException;

  private synchronized void startSamplingTask() {
    Client client = myClient;
    if (myExecutingTask == null && client != null) {
      myRunning = true;
      CountDownLatch taskFinished = new CountDownLatch(1);
      myTaskFinished = taskFinished;
      myExecutingTask = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        long timeToWait = mySampleFrequencyMs;
        try {
          while (myRunning) {
            long start = System.currentTimeMillis();
            boolean acquired = myDataSemaphore.tryAcquire(timeToWait, TimeUnit.MILLISECONDS);
            if (myRunning && !myIsPaused) {
              sample(acquired);
            }
            timeToWait -= System.currentTimeMillis() - start;
            if (timeToWait <= 0) {
              timeToWait = mySampleFrequencyMs;
            }

            if (!client.isValid()) {
              ApplicationManager.getApplication().invokeLater(this::stop);
              break;
            }
          }
        }
        catch (InterruptedException ignored) {
        }
        finally {
          myRunning = false;
          taskFinished.countDown();
        }
      });

      client.setHeapInfoUpdateEnabled(true);

      for (TimelineEventListener listener : myListeners) {
        listener.onStart();
      }
    }
  }

  private synchronized void stopSamplingTask() {
    Future<?> executingTask = myExecutingTask;
    Client client = myClient;
    if (executingTask == null) {
      return;
    }

    myRunning = false;
    myDataSemaphore.release();

    executingTask.cancel(true);

    CountDownLatch taskFinished = myTaskFinished;
    if (taskFinished != null) {
      try {
        taskFinished.await();
      }
      catch (InterruptedException ignored) {
        // We're stopping anyway, so just ignore the interruption.
      }
    }

    if (client != null) {
      client.setHeapInfoUpdateEnabled(false);
    }

    for (TimelineEventListener listener : myListeners) {
      listener.onStop();
    }

    myTaskFinished = null;
    myExecutingTask = null;
  }
}
