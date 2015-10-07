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
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.Set;

public class GapisProcess extends ChildProcess {
  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);
  public static final GapisProcess INSTANCE = new GapisProcess();
  private static final GapisConnection NOT_CONNECTED = new GapisConnection(INSTANCE, null);

  private static final int SERVER_LAUNCH_TIMEOUT_MS = 2000;
  private static final int SERVER_LAUNCH_SLEEP_INCREMENT_MS = 10;
  private static final String SERVER_HOST = "localhost";

  private final Set<GapisConnection> myConnections = Sets.newIdentityHashSet();

  private GapisProcess() {
    super("gapis");
    Factory.register();
  }

  @Override
  protected boolean prepare(ProcessBuilder pb) {
    if (!GapiPaths.isValid()) {
      LOG.warn("Could not find gapis, but needed to start the server.");
      return false;
    }
    pb.command(GapiPaths.gapis().getAbsolutePath(), "-shutdown_on_disconnect", "-rpc", SERVER_HOST + ":" + port(), "-logs",
               PathManager.getLogPath());
    // Add the server's directory to the path.  This allows the server to find and launch the gapir.
    // TODO: not needed when android studio starts gapir instead
    Map<String, String> env = pb.environment();
    String path = env.get("PATH");
    path = GapiPaths.gapis().getParentFile().getAbsolutePath() + File.pathSeparator + path;
    env.put("PATH", path);
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
  }

  /**
   * Attempts to connect to a gapis server.
   * <p/>
   * Will launch a new server process if none has been started.
   * <p/>
   * TODO: Implement more robust process management.  For example:
   * TODO: - Better way to detect when server has started in order to avoid polling for the socket.
   */
  public GapisConnection connect() {
    synchronized (this) {
      if (!isRunning()) {
        if (!start()) {
          return NOT_CONNECTED;
        }
      }
    }

    // After starting, the server requires a little time before it will be ready to accept connections.
    // This loop polls the server to establish a connection.
    GapisConnection connection = NOT_CONNECTED;
    try {
      for (int waitTime = 0; waitTime < SERVER_LAUNCH_TIMEOUT_MS; waitTime += SERVER_LAUNCH_SLEEP_INCREMENT_MS) {
        if ((connection = attemptToConnect()).isConnected()) {
          LOG.info("Established a new client connection to " + port());
          break;
        }
        Thread.sleep(SERVER_LAUNCH_SLEEP_INCREMENT_MS);
      }
    }
    catch (InterruptedException e) {
      Thread.interrupted(); // reset interrupted status
    }
    return connection;
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

  private GapisConnection attemptToConnect() {
    try {
      GapisConnection connection = new GapisConnection(this, new Socket(SERVER_HOST, port()));
      synchronized (myConnections) {
        myConnections.add(connection);
      }
      return connection;
    }
    catch (IOException ignored) {
    }
    return NOT_CONNECTED;
  }
}
