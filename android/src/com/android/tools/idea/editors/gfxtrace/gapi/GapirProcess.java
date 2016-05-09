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
package com.android.tools.idea.editors.gfxtrace.gapi;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class GapirProcess extends ChildProcess {
  @NotNull private static final Logger LOG = Logger.getInstance(GapirProcess.class);
  private static final Object myInstanceLock = new Object();
  private static String myAuthToken = null;
  private static GapirProcess myInstance;

  private static final int SERVER_LAUNCH_TIMEOUT_MS = 10000;

  private final SettableFuture<Integer> myPortF;

  private GapirProcess() {
    super("gapir");
    myPortF = start();
  }


  @Override
  public void shutdown() {
    synchronized (myInstanceLock) {
      if (myInstance == this) {
        myInstance = null;
        super.shutdown();
        this.myProcess.destroy();
      }
    }
  }

  @Override
  protected boolean prepare(ProcessBuilder pb) {
    if (!GapiPaths.gapir().exists()) {
      LOG.warn("Could not find gapir.");
      return false;
    }

    ArrayList<String> args = new ArrayList<String>(8);
    args.add(GapiPaths.gapir().getAbsolutePath());

    args.add("--log");
    args.add(new File(PathManager.getLogPath(), "gapir.log").getAbsolutePath());

    args.add("--gapis-auth-token");
    args.add(getAuthToken());

    pb.command(args);
    return true;
  }

  /** @return the auth-token for the GAPIS process. */
  public static String getAuthToken() {
    synchronized (myInstanceLock) {
      if (myAuthToken == null) {
        myAuthToken = generateAuthToken();
      }
      return myAuthToken;
    }
  }

  @Override
  protected void onExit(int code) {
    if (code != 0) {
      LOG.warn("The gapir process exited with a non-zero exit value: " + code);
    }
    else {
      LOG.info("gapir exited cleanly");
    }
  }

  /**
   * Get a gapir instance.
   * <p/>
   * Will launch a new gapir process if none has been started.
   * <p/>
   */
  public static GapirProcess get() {
    synchronized (myInstanceLock) {
      if (myInstance == null) {
        myInstance = new GapirProcess();
      }
      return myInstance;
    }
  }


  /**
   * Finds out the port gapir is running on.
   */
  public int getPort() {
    if (myPortF == null) {
      return -1;
    }
    try {
      return myPortF.get(SERVER_LAUNCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException e) {
      LOG.warn("Interrupted while waiting for gapir: " + e);
    }
    catch (ExecutionException e) {
      LOG.warn("Failed while waiting for gapir: " + e);
    }
    catch (TimeoutException e) {
      LOG.warn("Timed out waiting for gapir: " + e);
    }
    return -1;
  }
}
