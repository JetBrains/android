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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public abstract class DeviceSampler implements Runnable, AndroidDebugBridge.IClientChangeListener {
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

  @NotNull protected final TimelineData myData;
  @NotNull protected final Semaphore myDataSemaphore;
  protected final int mySampleFrequencyMs;
  @NotNull protected final List<TimelineEventListener> myListeners = Lists.newLinkedList();
  /**
   * The future representing the task being executed, which will return null upon successful completion.
   * If null, no current task is being executed.
   */
  @Nullable protected volatile Future<?> myExecutingTask;
  @Nullable protected volatile Client myClient;
  protected volatile boolean myRunning;

  public DeviceSampler(@NotNull TimelineData data, int sampleFrequencyMs) {
    myData = data;
    mySampleFrequencyMs = sampleFrequencyMs;
    myDataSemaphore = new Semaphore(0, true);

    myData.freeze();
  }

  @SuppressWarnings("ConstantConditions")
  public void start() {
    if (myExecutingTask == null && myClient != null) {
      myData.clear();
      AndroidDebugBridge.addClientChangeListener(this);
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

      myData.freeze();
      AndroidDebugBridge.removeClientChangeListener(this);
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
      start();
    }
  }

  public void addListener(TimelineEventListener listener) {
    myListeners.add(listener);
  }

  public boolean isRunning() {
    return myExecutingTask != null && myRunning;
  }

  @Override
  public void run() {
    boolean pending = false;
    long wait = mySampleFrequencyMs;
    while (myRunning) {
      try {
        long now = System.currentTimeMillis();
        if (myDataSemaphore.tryAcquire(wait, TimeUnit.MILLISECONDS)) {
          pending = false;
          sample(TYPE_DATA, 0);
        }
        else {
          if (pending) {
            sample(TYPE_TIMEOUT, 0);
          }
          sampleTimeoutHandler();
          pending = true;
        }
        wait -= (System.currentTimeMillis() - now);
        if (wait <= 0) {
          wait = mySampleFrequencyMs;
        }
      }
      catch (InterruptedException e) {
        myRunning = false;
      }
    }
  }

  @Override
  public void clientChanged(@NotNull Client client, int changeMask) {
    if (myClient != null && myClient == client) {
      if ((changeMask & Client.CHANGE_HEAP_DATA) != 0) {
        myDataSemaphore.release();
      }
    }
  }

  @NotNull
  public abstract String getName();

  @NotNull
  public abstract String getDescription();

  protected abstract void sample(int type, int id);

  protected abstract void sampleTimeoutHandler();
}
