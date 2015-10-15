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

package com.android.tools.idea.sdk.remote.internal;

import com.android.SdkConstants;

import java.io.File;
import java.io.IOException;

/**
 * A lightweight wrapper to start & stop ADB.
 * This is <b>specific</b> to the SDK Manager install process.
 */
public class AdbWrapper {

    /*
     * Note: we could bring ddmlib in SdkManager for that purpose, however this allows us to
     * specialize the start/stop methods to our needs (e.g. a task monitor, etc.)
     */

  private final String mAdbOsLocation;
  private final ITaskMonitor mMonitor;

  /**
   * Creates a new lightweight ADB wrapper.
   *
   * @param osSdkPath The root OS path of the SDK. Cannot be null.
   * @param monitor   A logger object. Cannot be null.
   */
  public AdbWrapper(String osSdkPath, ITaskMonitor monitor) {
    mMonitor = monitor;

    if (!osSdkPath.endsWith(File.separator)) {
      osSdkPath += File.separator;
    }
    mAdbOsLocation = osSdkPath + SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER + SdkConstants.FN_ADB;
  }

  private void display(String format, Object... args) {
    mMonitor.log(format, args);
  }

  private void displayError(String format, Object... args) {
    mMonitor.logError(format, args);
  }

  /**
   * Starts the adb host side server.
   *
   * @return true if success
   */
  public synchronized boolean startAdb() {
    if (mAdbOsLocation == null) {
      displayError("Error: missing path to ADB."); //$NON-NLS-1$
      return false;
    }

    Process proc;
    int status = -1;

    try {
      ProcessBuilder processBuilder = new ProcessBuilder(mAdbOsLocation, "start-server"); //$NON-NLS-1$
      proc = processBuilder.start();
      status = proc.waitFor();

      // Implementation note: normally on Windows we need to capture stderr/stdout
      // to make sure the process isn't blocked if it's output isn't read. However
      // in this case this happens to hang when reading stdout with no proper way
      // to properly close the streams. On the other hand the output from start
      // server is rather short and not very interesting so we just drop it.

    }
    catch (IOException ioe) {
      displayError("Unable to run 'adb': %1$s.", ioe.getMessage()); //$NON-NLS-1$
      // we'll return false;
    }
    catch (InterruptedException ie) {
      displayError("Unable to run 'adb': %1$s.", ie.getMessage()); //$NON-NLS-1$
      // we'll return false;
    }

    if (status != 0) {
      displayError(String.format("Starting ADB server failed (code %d).", //$NON-NLS-1$
                                 status));
      return false;
    }

    display("Starting ADB server succeeded."); //$NON-NLS-1$

    return true;
  }

  /**
   * Stops the adb host side server.
   *
   * @return true if success
   */
  public synchronized boolean stopAdb() {
    if (mAdbOsLocation == null) {
      displayError("Error: missing path to ADB."); //$NON-NLS-1$
      return false;
    }

    Process proc;
    int status = -1;

    try {
      String[] command = new String[2];
      command[0] = mAdbOsLocation;
      command[1] = "kill-server"; //$NON-NLS-1$
      proc = Runtime.getRuntime().exec(command);
      status = proc.waitFor();

      // See comment in startAdb about not needing/wanting to capture stderr/stdout.
    }
    catch (IOException ioe) {
      // we'll return false;
    }
    catch (InterruptedException ie) {
      // we'll return false;
    }

    // adb kill-server returns:
    // 0 if adb was running and was correctly killed.
    // 1 if adb wasn't running and thus wasn't killed.
    // This error case is not worth reporting.

    if (status != 0 && status != 1) {
      displayError(String.format("Stopping ADB server failed (code %d).", //$NON-NLS-1$
                                 status));
      return false;
    }

    display("Stopping ADB server succeeded."); //$NON-NLS-1$
    return true;
  }
}
