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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class GapisProcess extends ChildProcess {
  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);
  private static final Object myInstanceLock = new Object();
  private static GapisProcess myInstance;
  private static final GapisConnection NOT_CONNECTED = new GapisConnection(null, null);

  private static final int SERVER_LAUNCH_TIMEOUT_MS = 10000;
  private static final String SERVER_HOST = "localhost";

  private final Set<GapisConnection> myConnections = Sets.newIdentityHashSet();
  private final GapirProcess myGapir;
  private final SettableFuture<Integer> myPortF;

  static {
    Factory.register();
  }

  private GapisProcess() {
    super("gapis");
    myGapir = GapirProcess.get();
    myPortF = start();
  }

  @Override
  protected boolean prepare(ProcessBuilder pb) {
    if (!GapiPaths.isValid()) {
      LOG.warn("Could not find gapis, but needed to start the server.");
      return false;
    }
    pb.command(GapiPaths.gapis().getAbsolutePath(), "-logs", PathManager.getLogPath(), "--gapir", Integer.toString(myGapir.getPort()));
    return true;
  }

  @Override
  protected void onExit(int code) {
    if (code != 0) {
      LOG.warn("The gapis process exited with a non-zero exit value: " + code);
    }
    else {
      LOG.info("gapis exited cleanly");
    }
    shutdown();
  }

  /**
   * Attempts to connect to a gapis server.
   * <p/>
   * Will launch a new server process if none has been started.
   * <p/>
   */
  public static GapisConnection connect() {
    GapisProcess gapis;
    synchronized (myInstanceLock) {
      if (myInstance == null) {
        myInstance = new GapisProcess();
      }
      gapis = myInstance;
    }
    return gapis.doConnect();
  }

  private GapisConnection doConnect() {
    if (myPortF == null) {
      return NOT_CONNECTED;
    }
    try {
      int port = myPortF.get(SERVER_LAUNCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      GapisConnection connection = new GapisConnection(this, new Socket(SERVER_HOST, port));
      LOG.info("Established a new client connection to " + port);
      synchronized (myConnections) {
        myConnections.add(connection);
      }
      return connection;
    }
    catch (InterruptedException e) {
      LOG.warn("Interrupted while waiting for gapis: " + e);
    }
    catch (ExecutionException e) {
      LOG.warn("Failed while waiting for gapis: " + e);
    }
    catch (UnknownHostException e) {
      LOG.warn("Unknown host starting gapis: " + e);
    }
    catch (IOException e) {
      LOG.warn("Failed read from gapis: " + e);
    }
    catch (TimeoutException e) {
      LOG.warn("Timed out waiting for gapis: " + e);
    }
    return NOT_CONNECTED;
  }

  public void onClose(GapisConnection gapisConnection) {
    synchronized (myConnections) {
      myConnections.remove(gapisConnection);
      if (myConnections.isEmpty()) {
        LOG.info("Interrupting server thread on last connection close");
        shutdown();
      }
    }
  }

  @Override
  public void shutdown() {
    synchronized (myInstanceLock) {
      if (myInstance == this) {
        myInstance = null;
        myGapir.shutdown();
        super.shutdown();
      }
    }
  }

}
