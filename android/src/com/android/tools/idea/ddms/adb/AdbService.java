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
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.android.actions.AndroidEnableAdbServiceAction;
import org.jetbrains.android.logcat.AdbErrors;
import com.android.tools.idea.monitor.AndroidToolWindowFactory;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * {@link com.android.tools.idea.ddms.adb.AdbService} is the main entry point to initializing and obtaining the
 * {@link com.android.ddmlib.AndroidDebugBridge}.
 *
 * <p>Actions that require a handle to the debug bridge should invoke {@link #getDebugBridge(java.io.File)} to obtain
 * the debug bridge. This bridge is only valid at the time it is obtained, and could go stale in the future (e.g. user disables
 * adb integration via {@link org.jetbrains.android.actions.AndroidEnableAdbServiceAction}, or launches monitor via
 * {@link org.jetbrains.android.actions.AndroidRunDdmsAction}).
 *
 * <p>Components that need to keep a handle to the bridge for longer durations (such as tool windows that monitor device state) should do so
 * by first invoking {@link #getDebugBridge(java.io.File)} to obtain the bridge, and implementing
 * {@link com.android.ddmlib.AndroidDebugBridge.IDebugBridgeChangeListener} to ensure that they get updates to the status of the bridge.
 */
public class AdbService implements ApplicationComponent {
  @NotNull private final Ddmlib myDdmlib = new Ddmlib();
  @Nullable private SettableFuture<AndroidDebugBridge> myFuture;
  @Nullable private BridgeConnectorTask myMonitorTask;

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    terminateDdmlib();
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "AdbService";
  }

  public static AdbService getInstance() {
    return ApplicationManager.getApplication().getComponent(AdbService.class);
  }

  public synchronized ListenableFuture<AndroidDebugBridge> getDebugBridge(@NotNull File adb) {
    // Cancel previous requests if they were unsuccessful
    if (myFuture != null && !wasSuccessful(myFuture)) {
      terminateDdmlib();
    }

    if (myFuture == null) {
      myFuture = SettableFuture.create();
      myMonitorTask = new BridgeConnectorTask(adb, myDdmlib, myFuture);
      ApplicationManager.getApplication().executeOnPooledThread(myMonitorTask);
    }

    return myFuture;
  }

  public synchronized void terminateDdmlib() {
    myFuture = null;
    if (myMonitorTask != null) {
      myMonitorTask.cancel();
    }
    myDdmlib.terminate();
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

  public synchronized void restartDdmlib(@NotNull Project project) {
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

  /** Returns whether the future has completed successfully. */
  private static boolean wasSuccessful(Future<AndroidDebugBridge> future) {
    if (!future.isDone()) {
      return false;
    }

    try {
      AndroidDebugBridge bridge = future.get();
      return bridge != null && bridge.isConnected();
    }
    catch (Exception e) {
      return false;
    }
  }

  private static class BridgeConnectorTask implements Runnable {
    private static final long TIMEOUT_MS = 10000;
    private final CountDownLatch myCancelLatch = new CountDownLatch(1);

    private final Ddmlib myDdmlib;
    private final SettableFuture<AndroidDebugBridge> myResult;
    private final File myAdb;

    public BridgeConnectorTask(File adb, @NotNull Ddmlib ddmlib, @NotNull SettableFuture<AndroidDebugBridge> result) {
      myAdb = adb;
      myDdmlib = ddmlib;
      myResult = result;
    }

    @Override
    public void run() {
      AdbErrors.clear();
      myDdmlib.initialize(myAdb);

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
    private boolean myDdmLibTerminated = false;

    public synchronized void initialize(@NotNull File adb) {
      boolean forceRestart = true;
      if (!myDdmLibInitialized) {
        myDdmLibInitialized = true;
        myDdmLibTerminated = false;
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
      myBridge = AndroidDebugBridge.createBridge(adb.getPath(), forceRestart);
    }

    public synchronized boolean isConnectionInProgress() {
      return !(isConnected() || myDdmLibTerminated);
    }

    public synchronized boolean isConnected() {
      return myBridge.isConnected();
    }

    public synchronized void terminate() {
      myDdmLibTerminated = true;
      AndroidDebugBridge.disconnectBridge();
      AndroidDebugBridge.terminate();
      myDdmLibInitialized = false;
      LOG.info("DDMLib terminated");
    }
  }
}
