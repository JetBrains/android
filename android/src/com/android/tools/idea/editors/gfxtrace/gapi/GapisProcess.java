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
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GapisProcess {
  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);
  public static final GapisProcess INSTANCE = new GapisProcess();
  private static final GapisConnection NOT_CONNECTED = new GapisConnection(INSTANCE, null);

  private static final int SERVER_LAUNCH_TIMEOUT_MS = 2000;
  private static final int SERVER_LAUNCH_SLEEP_INCREMENT_MS = 10;
  private static final String SERVER_HOST = "localhost";

  private final Set<GapisConnection> myConnections = Sets.newIdentityHashSet();
  private Thread myServerThread;
  private int myPort;

  private GapisProcess() {
    Factory.register();
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
      if (!isServerRunning()) {
        if (!launchServer()) {
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
          LOG.info("Established a new client connection to " + myPort);
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
        myServerThread.interrupt();
      }
    }
  }

  private GapisConnection attemptToConnect() {
    try {
      GapisConnection connection = new GapisConnection(this, new Socket(SERVER_HOST, myPort));
      synchronized (myConnections) {
        myConnections.add(connection);
      }
      return connection;
    }
    catch (IOException ignored) {
    }
    return NOT_CONNECTED;
  }

  private boolean isServerRunning() {
    return myServerThread != null && myServerThread.isAlive();
  }

  private boolean launchServer() {
    myPort = findFreePort();
    final GapiPaths paths = GapiPaths.get();
    // The connection failed, so try to start a new instance of the server.
    if (paths.myGapisPath == null) {
      LOG.warn("Could not find gapis, but needed to start the server.");
      return false;
    }
    myServerThread = new Thread() {
      @Override
      public void run() {
        LOG.info("Launching gapis: \"" + paths.myGapisPath.getAbsolutePath() + "\" on port " + myPort);
        ProcessBuilder pb = new ProcessBuilder(getCommandAndArgs(paths.myGapisPath));

        // Add the server's directory to the path.  This allows the server to find and launch the replayd.
        Map<String, String> env = pb.environment();
        String path = env.get("PATH");
        path = paths.myServerDirectory.getAbsolutePath() + File.pathSeparator + path;
        env.put("PATH", path);

        // Use the plugin directory as the working directory for the server.
        pb.directory(paths.myGapisRoot);
        pb.redirectErrorStream(true);

        Process serverProcess = null;
        try {
          // This will throw IOException if the server executable is not found.
          serverProcess = pb.start();
          final BufferedReader stdout =
            new BufferedReader(new InputStreamReader(serverProcess.getInputStream(), Charset.forName("UTF-8")));
          new Thread() {
            @Override
            public void run() {
              try {
                for (String line; (line = stdout.readLine()) != null; ) {
                  LOG.warn("gapis: " + line);
                }
              }
              catch (IOException ignored) {
              }
            }
          }.start();

          int exitValue = serverProcess.waitFor();
          if (exitValue != 0) {
            LOG.warn("The gapis process exited with a non-zero exit value: " + exitValue);
          }
          else {
            LOG.info("gapis exited cleanly");
          }
        }
        catch (IOException e) {
          LOG.warn(e);
        }
        catch (InterruptedException e) {
          if (serverProcess != null) {
            LOG.info("Killing server process");
            serverProcess.destroy();
          }
        }
      }

      private List<String> getCommandAndArgs(File gapis) {
        List<String> result = Lists.newArrayList();
        result.add(gapis.getAbsolutePath());
        result.add("-shutdown_on_disconnect");
        result.add("-rpc"); result.add(SERVER_HOST + ":" + myPort);
        result.add("-logs"); result.add(PathManager.getLogPath());
        return result;
      }
    };
    myServerThread.start();
    return true;
  }

  private static int findFreePort() {
    ServerSocket socket = null;
    try {
      socket = new ServerSocket(0);
      return socket.getLocalPort();
    }
    catch (IOException e) {
      throw Throwables.propagate(e);
    }
    finally {
      if (socket != null) {
        try {
          socket.close();
        }
        catch (IOException ignored) {
        }
      }
    }
  }
}
