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
package com.android.tools.idea.ddms.adb;

import com.android.ddmlib.*;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.android.actions.AndroidEnableAdbServiceAction;
import org.jetbrains.android.logcat.AdbErrors;
import org.jetbrains.android.logcat.AndroidToolWindowFactory;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AdbService {
  private static final Ddmlib ourDdmlib = new Ddmlib();
  private static SettableFuture<AndroidDebugBridge> ourFuture;
  private static MonitorDebugBridgeConnectionTask ourMonitorTask;

  public static synchronized ListenableFuture<AndroidDebugBridge> initializeAndGetBridge(String adbPath, boolean recreate) {
    if (recreate && ourFuture != null) {
      ourFuture = null;
      ourMonitorTask.cancel();
      ourDdmlib.terminate();
    }

    if (ourFuture == null) {
      ourFuture = SettableFuture.create();

      AdbErrors.clear();
      ourDdmlib.initialize(adbPath);

      ourMonitorTask = new MonitorDebugBridgeConnectionTask(ourDdmlib, ourFuture);
      ApplicationManager.getApplication().executeOnPooledThread(ourMonitorTask);
    }

    return ourFuture;
  }

  public static synchronized void terminateDdmlib() {
    ourFuture = null;
    ourDdmlib.terminate();
  }

  public static boolean canDdmsBeCorrupted(@NotNull AndroidDebugBridge bridge) {
    return isDdmsCorrupted(bridge) || allDevicesAreEmpty(bridge);
  }

  private static boolean allDevicesAreEmpty(@NotNull AndroidDebugBridge bridge) {
    for (IDevice device : bridge.getDevices()) {
      if (device.getClients().length > 0) {
        return false;
      }
    }
    return true;
  }

  public static boolean isDdmsCorrupted(@NotNull AndroidDebugBridge bridge) {
    // TODO: find other way to check if debug service is available

    IDevice[] devices = bridge.getDevices();
    if (devices.length > 0) {
      for (IDevice device : devices) {
        Client[] clients = device.getClients();

        if (clients.length > 0) {
          ClientData clientData = clients[0].getClientData();
          return clientData.getVmIdentifier() == null;
        }
      }
    }
    return false;
  }

  public static synchronized void restartDdmlib(@NotNull Project project) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(AndroidToolWindowFactory.TOOL_WINDOW_ID);
    boolean hidden = false;
    if (toolWindow != null && toolWindow.isVisible()) {
      hidden = true;
      toolWindow.hide(null);
    }
    terminateDdmlib();
    if (hidden) {
      toolWindow.show(null);
    }
  }

  private static class MonitorDebugBridgeConnectionTask implements Runnable {
    private static final long TIMEOUT_MS = 10000;
    private final CountDownLatch myCancelLatch = new CountDownLatch(1);

    private final Ddmlib myDdmlib;
    private final SettableFuture<AndroidDebugBridge> myResult;

    public MonitorDebugBridgeConnectionTask(@NotNull Ddmlib ddmlib, @NotNull SettableFuture<AndroidDebugBridge> result) {
      myDdmlib = ddmlib;
      myResult = result;
    }

    @Override
    public void run() {
      long startTime = System.currentTimeMillis();

      while (!myDdmlib.isConnected()) {
        // if not connected, wait for sometime, unless we were cancelled
        if (myDdmlib.isConnectionInProgress()) {
          try {
            if (myCancelLatch.await(200, TimeUnit.MILLISECONDS)) {
              break;
            }
          }
          catch (InterruptedException ignore) {
            break;
          }
        }

        // check if we should time out
        if (System.currentTimeMillis() > (startTime + TIMEOUT_MS)) {
          break;
        }
      }

      if (myDdmlib.isConnected()) {
        myResult.set(AndroidDebugBridge.getBridge());
      }
      else {
        myResult.setException(new CancellationException());
      }
    }

    public void cancel() {
      myCancelLatch.countDown();
    }
  }

  /** Encapsulates DDM library initialization/termination*/
  private static class Ddmlib {
    private static final Logger LOG = Logger.getInstance(Ddmlib.class);
    private AndroidDebugBridge myBridge;
    private boolean myDdmLibInitialized = false;
    private boolean ourDdmLibTerminated = false;

    public synchronized void initialize(@NotNull String adbPath) {
      boolean forceRestart = true;
      if (!myDdmLibInitialized) {
        myDdmLibInitialized = true;
        ourDdmLibTerminated = false;
        DdmPreferences.setLogLevel(Log.LogLevel.INFO.getStringValue());
        DdmPreferences.setTimeOut(AndroidUtils.TIMEOUT);
        AndroidDebugBridge.init(AndroidEnableAdbServiceAction.isAdbServiceEnabled());
        LOG.info("DDMLib initialized");
      }
      else {
        final AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
        forceRestart = bridge != null && !bridge.isConnected();
        if (forceRestart) {
          LOG.info("Force restarting bridge: currently not connected.");
        }
      }
      myBridge = AndroidDebugBridge.createBridge(adbPath, forceRestart);
    }

    public synchronized boolean isConnectionInProgress() {
      return !(isConnected() || ourDdmLibTerminated);
    }

    public synchronized boolean isConnected() {
      return myBridge.isConnected();
    }

    public synchronized void terminate() {
      ourDdmLibTerminated = true;
      AndroidDebugBridge.disconnectBridge();
      AndroidDebugBridge.terminate();
      myDdmLibInitialized = false;
      LOG.info("DDMLib terminated");
    }
  }
}
