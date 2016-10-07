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
package com.android.tools.idea.monitor.memory;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.tools.adtui.TimelineData;
import com.android.tools.idea.monitor.DeviceSampler;
import org.jetbrains.annotations.NotNull;

public class MemorySampler extends DeviceSampler implements AndroidDebugBridge.IClientChangeListener {
  /**
   * Maximum number of samples to keep in memory. We not only sample at {@code SAMPLE_FREQUENCY_MS} but we also receive
   * a sample on every GC.
   */
  public static final int SAMPLES = 2048;

  private boolean myRequestPending;

  MemorySampler(int sampleFrequencyMs) {
    super(new TimelineData(2, SAMPLES), sampleFrequencyMs);
  }

  @Override
  public void start() {
    if (myExecutingTask == null && myClient != null) {
      AndroidDebugBridge.addClientChangeListener(this);
    }
    super.start();
  }

  @Override
  public void stop() {
    super.stop();
    myRequestPending = false;
    if (myExecutingTask != null) {
      AndroidDebugBridge.removeClientChangeListener(this);
    }
  }

  @NotNull
  @Override
  public String getName() {
    return "Memory Sampler";
  }

  @SuppressWarnings("ConstantConditions")
  protected void recordSample(int type) {
    float freeMb = 0.0f;
    float allocMb = 0.0f;
    if (myClient != null) {
      ClientData.HeapInfo m = myClient.getClientData().getVmHeapInfo(1);
      if (m != null) {
        allocMb = m.bytesAllocated / (1024.f * 1024.f);
        freeMb = m.sizeInBytes / (1024.f * 1024.f) - allocMb;
      }
    }
    else {
      type = TYPE_UNREACHABLE;
    }
    // We cannot use the timeStamp in HeapInfo because it's based on the current time of the attached device.
    myTimelineData.add(System.currentTimeMillis(), type, allocMb, freeMb);
  }

  protected void requestSample() {
    Client client = myClient;
    if (client != null) {
      client.updateHeapInfo();
    }
  }

  @Override
  protected void sample(boolean forced) throws InterruptedException {
    if (forced) {
      myRequestPending = false;
      recordSample(TYPE_DATA);
    }
    else {
      if (myRequestPending) {
        recordSample(TYPE_TIMEOUT);
      }
      requestSample();
      myRequestPending = true;
    }
  }

  @Override
  public void clientChanged(@NotNull Client client, int changeMask) {
    if (myClient != null && myClient == client) {
      if ((changeMask & Client.CHANGE_HEAP_DATA) != 0) {
        forceSample();
      }
    }
  }
}
