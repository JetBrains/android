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

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.service.Factory;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class GapirProcess extends ChildProcess {
  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);
  public static final GapirProcess INSTANCE = new GapirProcess();

  private static final int SERVER_LAUNCH_TIMEOUT_MS = 10000;
  private static final String SERVER_HOST = "localhost";

  private final Object myPortLock = new Object();
  private SettableFuture<Integer> myPortF = null;

  private GapirProcess() {
    super("gapir");
  }

  @Override
  protected boolean prepare(ProcessBuilder pb) {
    if (!GapiPaths.gapir().exists()) {
      LOG.warn("Could not find gapir.");
      return false;
    }
    pb.command(GapiPaths.gapir().getAbsolutePath(), "--port", "0", "--nocache",
               "--log", new File(PathManager.getLogPath(), "gapir.log").getAbsolutePath());
    return true;
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
   * Finds out the port gapir is running on.
   * <p/>
   * Will launch a new gapir process if none has been started.
   * <p/>
   */
  public int getPort() {
    SettableFuture<Integer> portF;
    synchronized (myPortLock) {
      if (myPortF == null) {
        myPortF = start();
      }
      portF = myPortF;
    }
    if (portF == null) {
      return -1;
    }
    try {
      return portF.get(SERVER_LAUNCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
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
