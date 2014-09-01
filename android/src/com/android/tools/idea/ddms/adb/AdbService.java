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

import com.android.SdkConstants;
import com.android.ddmlib.*;
import com.google.common.base.Joiner;
import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.android.actions.AndroidEnableAdbServiceAction;
import org.jetbrains.android.logcat.AdbErrors;
import org.jetbrains.android.logcat.AndroidToolWindowFactory;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

public class AdbService {
  private static final Ddmlib ourDdmlib = new Ddmlib();

  public static boolean initializeDdmlib(@NotNull Project project, String adbPath) {
    while (true) {
      final MyInitializeDdmlibTask task = new MyInitializeDdmlibTask(project, ourDdmlib);

      AdbErrors.clear();

      ourDdmlib.initialize(adbPath);

      boolean retryWas = false;
      while (!task.isFinished()) {
        ProgressManager.getInstance().run(task);
        boolean finished = task.isFinished();

        if (task.isCanceled()) {
          ourDdmlib.setAdbCrashed(!finished);
          return false;
        }

        ourDdmlib.setAdbCrashed(false);

        if (!finished) {
          final String adbErrorString = Joiner.on('\n').join(AdbErrors.getErrors());
          final int result = Messages.showDialog(project, "ADB not responding. You can wait more, or kill \"" +
                                                          SdkConstants.FN_ADB +
                                                          "\" process manually and click 'Restart'" +
                                                          (adbErrorString.length() > 0 ? "\nErrors from ADB:\n" + adbErrorString : ""),
                                                 CommonBundle.getErrorTitle(), new String[]{"&Wait more", "&Restart", "&Cancel"}, 0,
                                                 Messages.getErrorIcon());
          if (result == 2) {
            // cancel
            ourDdmlib.setAdbCrashed(true);
            return false;
          }
          else if (result == 1) {
            // restart
            ourDdmlib.setAdbCrashed(true);
            retryWas = true;
          }
        }
      }

      // task finished, but if we had problems, ddmlib can be still initialized incorrectly, so we invoke initialize once again
      if (!retryWas) {
        break;
      }
    }

    return true;
  }

  public static void terminateDdmlib() {
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

  public static void restartDdmlib(@NotNull Project project) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(
      AndroidToolWindowFactory.TOOL_WINDOW_ID);
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

  private static class MyInitializeDdmlibTask extends Task.Modal {
    private final Object myLock = new Object();
    private final Ddmlib myDdmlib;
    private volatile boolean myCanceled;

    public MyInitializeDdmlibTask(Project project, Ddmlib ddmlib) {
      super(project, "Waiting for ADB", true);
      myDdmlib = ddmlib;
    }

    public boolean isFinished() {
      synchronized (myLock) {
        return myDdmlib.isConnected();
      }
    }

    public boolean isCanceled() {
      synchronized (myLock) {
        return myCanceled;
      }
    }

    @Override
    public void onCancel() {
      synchronized (myLock) {
        myCanceled = true;
        myLock.notifyAll();
      }
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      synchronized (myLock) {
        final long startTime = System.currentTimeMillis();

        final long timeout = 10000;

        while (myDdmlib.isConnectionInProgress() && !myCanceled && !indicator.isCanceled()) {
          long wastedTime = System.currentTimeMillis() - startTime;
          if (wastedTime >= timeout) {
            break;
          }
          try {
            myLock.wait(Math.min(timeout - wastedTime, 500));
          }
          catch (InterruptedException e) {
            break;
          }
        }
      }
    }
  }

  /** Encapsulates DDM library initialization/termination*/
  private static class Ddmlib {
    private static final Logger LOG = Logger.getInstance(Ddmlib.class);
    private AndroidDebugBridge myBridge;
    private boolean myDdmLibInitialized = false;
    private boolean ourDdmLibTerminated = false;
    private boolean myAdbCrashed = false;

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
        forceRestart = myAdbCrashed || (bridge != null && !bridge.isConnected());
        if (forceRestart) {
          LOG.info("Restart debug bridge: " + (myAdbCrashed ? "crashed" : "disconnected"));
        }
      }
      myBridge = AndroidDebugBridge.createBridge(adbPath, forceRestart);
    }

    public synchronized boolean isConnectionInProgress() {
      return !(isConnected() || ourDdmLibTerminated);
    }

    public synchronized void setAdbCrashed(boolean adbCrashed) {
      myAdbCrashed = adbCrashed;
    }

    public synchronized boolean isConnected() {
      return myBridge.isConnected();
    }

    public synchronized void terminate() {
      ourDdmLibTerminated = true;
      //noinspection SynchronizeOnThis
      notifyAll();
      AndroidDebugBridge.disconnectBridge();
      AndroidDebugBridge.terminate();
      LOG.info("DDMLib terminated");
      myDdmLibInitialized = false;
    }
  }
}
