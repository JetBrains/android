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
package org.jetbrains.android.sdk;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.Log;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.actions.AndroidEnableAdbServiceAction;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates DDM library initialization/termination
 */
public final class Ddmlib {
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
