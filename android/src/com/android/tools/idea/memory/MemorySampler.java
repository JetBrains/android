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

import com.android.SdkConstants;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DeviceContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class MemorySampler
  implements Runnable, AndroidDebugBridge.IClientChangeListener, ClientData.IHprofDumpHandler, DeviceContext.DeviceSelectionListener {

  /**
   * Sample type when the device cannot be seen.
   */
  public static final int TYPE_UNREACHABLE = 0;
  /**
   * Sample created from a valid HPIF response.
   */
  public static final int TYPE_DATA = 1;
  /**
   * The device is reachable but no HPIF response was received in time.
   */
  public static final int TYPE_TIMEOUT = 2;
  /**
   * A sample that marks the beginning of an HPROF request.
   */
  public static final int TYPE_HPROF_REQUEST = 3;
  /**
   * A sample flagging that an HPROF dump has been received.
   */
  public static final int TYPE_HPROF_RESULT = 4;

  private static final Logger LOG = Logger.getInstance(MemorySampler.class);
  private static int ourLastHprofRequestId = 0;
  @NotNull
  private final TimelineData myData;
  @NotNull
  private final Project myProject;
  @NotNull
  private final Semaphore myDataSemaphore;
  private final int mySampleFrequencyMs;
  /**
   * The future representing the task being executed, which will return null upon successful completion.
   * If null, no current task is being executed.
   */
  @Nullable
  private Future<?> myExecutingTask;
  @Nullable
  private volatile Client myClient;
  private volatile boolean myRunning;
  private int myPendingHprofId;

  MemorySampler(@NotNull TimelineData data, @NotNull Project project, @NotNull DeviceContext deviceContext, int sampleFrequencyMs) {
    mySampleFrequencyMs = sampleFrequencyMs;
    myData = data;
    myProject = project;
    deviceContext.addListener(this, project);
    myDataSemaphore = new Semaphore(0, true);
    myPendingHprofId = 0;
    myData.freeze();
  }

  private static int getNextHprofId() {
    return ++ourLastHprofRequestId;
  }

  @SuppressWarnings("ConstantConditions")
  private void sample(int type, int id) {
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
  public void clientChanged(@NotNull Client client, int changeMask) {
    if (myClient != null && myClient == client) {
      if ((changeMask & Client.CHANGE_HEAP_DATA) != 0) {
        myDataSemaphore.release();
      }
    }
  }

  public void start() {
    if (myExecutingTask == null) {
      myData.clear();
      AndroidDebugBridge.addClientChangeListener(this);
      myRunning = true;
      myExecutingTask = ApplicationManager.getApplication().executeOnPooledThread(this);
      startClient();
    }
  }


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
      stopClient();
      myExecutingTask = null;
    }
  }

  @SuppressWarnings("ConstantConditions")
  private void startClient() {
    if (myExecutingTask != null & myClient != null) {
      myClient.setHeapInfoUpdateEnabled(true);
    }
  }

  @SuppressWarnings("ConstantConditions")
  private void stopClient() {
    if (myExecutingTask != null && myClient != null) {
      myClient.setHeapInfoUpdateEnabled(false);
    }
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
          Client client = myClient;
          if (client != null) {
            client.updateHeapInfo();
          }
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
  public void onSuccess(String remoteFilePath, Client client) {
    LOG.warn("Unexpected HPROF dump in remote file path.");
  }

  @Override
  public void onSuccess(final byte[] data, Client client) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (myPendingHprofId == 0) {
          // We are not waiting for any dumps. We ignore it.
          return;
        }
        File f;
        try {
          f = FileUtil.createTempFile("ddms", "." + SdkConstants.EXT_HPROF);
          FileUtil.writeToFile(f, data);
        }
        catch (IOException e) {
          return;
        }
        final VirtualFile vf = VfsUtil.findFileByIoFile(f, true);
        if (vf == null) {
          return;
        }
        sample(TYPE_HPROF_RESULT, myPendingHprofId);
        myPendingHprofId = 0;
        OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, vf);
        FileEditorManager.getInstance(myProject).openEditor(descriptor, true);
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

  @Override
  public void deviceSelected(@Nullable IDevice device) {
    // Ignore.
  }

  @Override
  public void deviceChanged(@NotNull IDevice device, int changeMask) {
    // Ignore.
  }

  @Override
  @SuppressWarnings("ConstantConditions")
  public void clientSelected(@Nullable Client client) {
    if (client != myClient) {
      stopClient();
      myClient = client;
      startClient();
      myData.setTitle(myClient == null ? "" : myClient.getDevice().getName() + ": " + myClient.getClientData().getClientDescription());
    }
  }
}
