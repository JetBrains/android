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

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.tools.idea.monitor.DeviceSampler;
import com.android.tools.idea.monitor.TimelineData;
import com.android.tools.idea.monitor.TimelineEvent;
import com.android.tools.idea.monitor.TimelineEventListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MemorySampler extends DeviceSampler implements ClientData.IHprofDumpHandler {
  public class HprofDumpCompletedEvent implements TimelineEvent {
    private byte[] myData;

    private HprofDumpCompletedEvent(@NotNull byte[] data) {
      myData = data;
    }

    @Nullable
    @Override
    public byte[] getData() {
      return myData;
    }
  }

  /**
   * A sample that marks the beginning of an HPROF request.
   */
  public static final int TYPE_HPROF_REQUEST = INHERITED_TYPE_START;
  /**
   * A sample flagging that an HPROF dump has been received.
   */
  public static final int TYPE_HPROF_RESULT = INHERITED_TYPE_START + 1;

  private static final Logger LOG = Logger.getInstance(MemorySampler.class);
  private static int ourLastHprofRequestId = 0;
  private int myPendingHprofId;

  MemorySampler(@NotNull TimelineData data, int sampleFrequencyMs) {
    super(data, sampleFrequencyMs);
    myPendingHprofId = 0;
  }

  @NotNull
  @Override
  public String getName() {
    return "Memory Sampler";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "memory information";
  }

  private static int getNextHprofId() {
    return ++ourLastHprofRequestId;
  }

  @Override
  @SuppressWarnings("ConstantConditions")
  protected void sample(int type, int id) {
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
    myData.add(System.currentTimeMillis(), type, id, allocMb, freeMb);
  }

  @Override
  protected void sampleTimeoutHandler() {
    Client client = myClient;
    if (client != null) {
      client.updateHeapInfo();
    }
  }

  @Override
  public void onSuccess(String remoteFilePath, Client client) {
    LOG.warn("Unexpected HPROF dump in remote file path.");
  }

  @Override
  public void onSuccess(final byte[] data, final Client client) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (myPendingHprofId == 0) {
          // We are not waiting for any dumps. We ignore it.
          return;
        }
        sample(TYPE_HPROF_RESULT, myPendingHprofId);
        myPendingHprofId = 0;
        for (TimelineEventListener listener : myListeners) {
          listener.onEvent(new HprofDumpCompletedEvent(data));
        }
      }
    });
  }

  @Override
  public void onEndFailure(Client client, String message) {
    LOG.error("Error getting the HPROF dump.");
  }

  public boolean canRequestHeapDump() {
    return myPendingHprofId == 0;
  }

  @SuppressWarnings("ConstantConditions")
  public void requestHeapDump() {
    if (myClient != null) {
      ClientData.setHprofDumpHandler(this);
      myClient.dumpHprof();
      myPendingHprofId = getNextHprofId();
      sample(TYPE_HPROF_REQUEST, myPendingHprofId);
    }
  }

  @SuppressWarnings("ConstantConditions")
  public void requestGc() {
    if (myClient != null) {
      myClient.executeGarbageCollector();
    }
  }
}
