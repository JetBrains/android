/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.memory;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DeviceContext;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class MemorySampler implements Runnable, AndroidDebugBridge.IClientChangeListener {

  /**
   * The future representing the task being executed, which will return null upon successful completion.
   */
  @NotNull
  private final Future<?> myExecutingTask;
  @NotNull
  private final TimelineData myData;
  @NotNull
  private final String myApplicationName;
  @NotNull
  private final AndroidDebugBridge myBridge;
  @NotNull
  private final CountDownLatch myStopLatch;
  private final int mySampleFrequencyMs;
  @NotNull
  private final DeviceContext myDeviceContext;
  @Nullable
  private Client myClient;
  private volatile boolean myRunning;

  MemorySampler(@NotNull String applicationName,
                @NotNull TimelineData data,
                @NotNull AndroidDebugBridge bridge,
                @NotNull DeviceContext deviceContext,
                int sampleFrequencyMs) {
    myApplicationName = applicationName;
    mySampleFrequencyMs = sampleFrequencyMs;
    myData = data;
    myBridge = bridge;
    myDeviceContext = deviceContext;
    refreshClient();
    myRunning = true;
    myStopLatch = new CountDownLatch(1);
    myExecutingTask = ApplicationManager.getApplication().executeOnPooledThread(this);
  }

  @Nullable
  private Client findClient() {
    for (IDevice device : myBridge.getDevices()) {
      Client client = device.getClient(myApplicationName);
      if (client != null) {
        return client;
      }
    }
    return null;
  }

  private void refreshClient() {
    if (myClient != null && myClient.isValid()) {
      return;
    }

    Client client = findClient();
    if (client != myClient) {
      if (myClient != null) {
        myClient.setHeapInfoUpdateEnabled(false);
      }
      myClient = client;
      myDeviceContext.fireClientSelected(myClient);
      if (myClient != null) {
        myClient.setHeapInfoUpdateEnabled(true);
        myData.setTitle(myClient.getDevice().getName() + ": " + myClient.getClientData().getClientDescription());
      }
      else {
        myData.setTitle("<" + myApplicationName + "> not found.");
      }
    }
  }

  private void requestHeapInfo() {
    refreshClient();
    if (myClient != null) {
      myClient.updateHeapInfo();
    }
    else {
      myData.add(System.currentTimeMillis(), 0.0f, 0.0f);
    }
  }

  @Override
  public void clientChanged(@NotNull Client client, int changeMask) {
    if (myClient != null && myClient == client) {
      if ((changeMask & Client.CHANGE_HEAP_DATA) != 0) {

        float freeMb = 0.0f;
        float allocMb = 0.0f;
        ClientData.HeapInfo m = client.getClientData().getVmHeapInfo(1);
        if (m != null) {
          allocMb = m.bytesAllocated / (1024.f * 1024.f);
          freeMb = m.sizeInBytes / (1024.f * 1024.f) - allocMb;
        }

        // We cannot use the timeStamp in HeapInfo because it's based on the current time of the attached device.
        long time = System.currentTimeMillis();
        myData.add(time, allocMb, freeMb);
      }
    }
  }

  void stop() {
    myRunning = false;
    myStopLatch.countDown();
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
  }

  @Override
  public void run() {
    AndroidDebugBridge.addClientChangeListener(this);
    while (myRunning) {
      try {
        requestHeapInfo();
        myStopLatch.await(mySampleFrequencyMs, TimeUnit.MILLISECONDS);
      }
      catch (InterruptedException e) {
        myRunning = false;
      }
    }
    if (myClient != null) {
      myClient.setHeapInfoUpdateEnabled(false);
    }
    AndroidDebugBridge.removeClientChangeListener(this);
  }
}
