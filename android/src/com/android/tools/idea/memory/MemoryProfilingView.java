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
import com.android.tools.idea.model.AndroidModuleInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.JBColor;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class MemoryProfilingView extends ToolWindowManagerAdapter implements AndroidDebugBridge.IClientChangeListener {

  public static final int SAMPLE_FREQUENCY_MS = 500;
  private final ToolWindowManagerEx myToolWindowManager;
  private final AndroidDebugBridge myBridge;
  private final Project myProject;

  private JPanel myContentPane;
  private JPanel myMainPanel;

  private boolean myVisible;
  private ToolWindow myToolWindow;
  private MemorySampler myMemorySampler;
  private TimelineData myData;
  private Client myClient;
  private String myApplicationName;
  private TimelineComponent myTimelineComponent;

  public MemoryProfilingView(Project project, ToolWindow toolWindow) {
    myProject = project;
    myToolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
    myToolWindow = toolWindow;
    myData = new TimelineData(
      new TimelineData.Stream[]{new TimelineData.Stream("Allocated", new JBColor(new Color(0x78abd9), new Color(0x78abd9))),
        new TimelineData.Stream("Free", new JBColor(new Color(0xbaccdc), new Color(0x51585c)))}, 200, "MB");
    // Buffer at one and a half times the sample frequency.
    float bufferTimeInSeconds = SAMPLE_FREQUENCY_MS * 1.5f / 1000.f;
    float initialMax = 5.0f;
    float initialMarker = 2.0f;
    myTimelineComponent = new TimelineComponent(myData, bufferTimeInSeconds, initialMax, initialMarker);
    myMainPanel.add(myTimelineComponent);

    myBridge = AndroidSdkUtils.getDebugBridge(project);
    AndroidDebugBridge.addClientChangeListener(this);

    myToolWindowManager.addToolWindowManagerListener(this);

    stateChanged();
    reset();
  }

  @Override
  public void stateChanged() {
    boolean isRegistered = myToolWindowManager.getToolWindow(MemoryProfilingToolWindowFactory.ID) != null;
    boolean disposed = myToolWindow.isDisposed();
    boolean visible = !disposed && isRegistered && myToolWindow.isVisible();
    if (visible != myVisible || disposed) {
      if (myMemorySampler != null) {
        myMemorySampler.stop();
        myMemorySampler = null;
      }

      if (visible) {
        myMemorySampler = new MemorySampler();
        ApplicationManager.getApplication().executeOnPooledThread(myMemorySampler);
        reset();
      }
      myVisible = visible;
    }
  }

  private void reset() {
    myApplicationName = getApplicationName();
    myData.clear();
    myTimelineComponent.reset();
  }


  @Nullable
  private String getApplicationName() {
    //TODO: Allow users to select the client to profile.
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(module);
      if (moduleInfo != null) {
        String pkg = moduleInfo.getPackage();
        if (pkg != null) {
          return pkg;
        }
      }
    }
    return null;
  }

  private void requestHeapInfo() {
    if (myClient == null || !myClient.isValid()) {
      IDevice[] devices = myBridge.getDevices();
      if (devices.length > 0 && myApplicationName != null && devices[0].getClient(myApplicationName) != null) {
        IDevice device = devices[0];
        myClient = device.getClient(myApplicationName);
        myData.setTitle(device.getName() + ": " + myClient.getClientData().getClientDescription());
      }
      else {
        myClient = null;
        myData.setTitle("<" + myApplicationName + "> not found.");
      }
    }
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

        // TODO: The timestamp for this sample should be the one in the HPIF block.
        myData.add(System.currentTimeMillis(), allocMb, freeMb);
      }
    }
  }

  public JPanel getContentPane() {
    return myContentPane;
  }

  class MemorySampler implements Runnable {
    private volatile boolean myRunning = true;

    void stop() {
      myRunning = false;
    }

    @Override
    public void run() {
      while (myRunning) {
        try {
          requestHeapInfo();
          //noinspection BusyWait
          Thread.sleep(SAMPLE_FREQUENCY_MS);
        }
        catch (InterruptedException e) {
          myRunning = false;
        }
      }
    }
  }
}
