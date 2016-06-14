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
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.android.tools.idea.editors.gfxtrace.gapi.Version.*;

public final class GapisProcess extends ChildProcess {
  @NotNull private static final Logger LOG = Logger.getInstance(GapisProcess.class);
  private static final Object myInstanceLock = new Object();
  private static GapisProcess myInstance;
  private static final GapisConnection NOT_CONNECTED = new GapisConnection(null, null);

  private static final int SERVER_LAUNCH_TIMEOUT_MS = 10000;
  private static final String SERVER_HOST = "localhost";
  private static final String SERVER_POLL_VERSION_PREFIX = "GAPIS version ";
  private static final int SERVER_POLL_VERSION_TIMEOUT_MS = 1000;
  private static final Version SERVER_DEFAULT_VERSION = VERSION_1;

  private final Set<GapisConnection> myConnections = Sets.newIdentityHashSet();
  private final GapirProcess myGapir;
  private final SettableFuture<Integer> myPortF;

  private String myAuthToken = null;
  private Version myVersion = NULL_VERSION;

  static {
    Factory.register();
  }

  private GapisProcess() {
    super("gapis");
    myGapir = GapirProcess.get();
    myPortF = start();
  }

  /**
   * Returns the version of the GAPIS instance.
   */
  public Version getVersion() {
    return myVersion;
  }

  @Override
  protected boolean prepare(ProcessBuilder pb) {
    if (!GapiPaths.isValid()) {
      LOG.warn("Could not find gapis, but needed to start the server.");
      return false;
    }

    if (myVersion == NULL_VERSION) {
      myVersion = fetchVersion();
      LOG.info("GAPIS is version " + myVersion);
    }

    int gapirPort = myGapir.getPort();
    if (gapirPort <= 0) {
      return false;
    }

    ArrayList<String> args = new ArrayList<String>(8);
    args.add(GapiPaths.gapis().getAbsolutePath());

    args.add("-logs");
    args.add(PathManager.getLogPath());

    args.add("--gapir");
    args.add(Integer.toString(gapirPort));

    File strings = GapiPaths.strings();
    if (myVersion.isAtLeast(VERSION_2) && strings.exists()) {
      args.add("--strings");
      args.add(strings.getAbsolutePath());
    }

    if (myVersion.isAtLeast(VERSION_3)) {
      myAuthToken = generateAuthToken();
      args.add("--gapis-auth-token");
      args.add(myAuthToken);
      args.add("--gapir-auth-token");
      args.add(GapirProcess.getAuthToken());
    }

    pb.command(args);
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
      if (myAuthToken != null) {
        connection.sendAuth(myAuthToken);
      }
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

  /**
   * Run GAPIS using the --version command line flag to enquire about the server's version.
   * This call is blocking and should not be made on the main UI thread.
   * <p>
   * Note earlier versions of GAPIS did not support this flag, and any non-version response will
   * assume to be GAPIS version {@link #SERVER_DEFAULT_VERSION}.
   *
   * @return the GAPIS version code.
   */
  private Version fetchVersion() {
    final ProcessBuilder pb = new ProcessBuilder();
    pb.directory(GapiPaths.base());
    pb.command(GapiPaths.gapis().getAbsolutePath(), "--version");
    pb.redirectErrorStream(true);

    Process process = null;
    // Use the base directory as the working directory for the server.
    try {
      // This will throw IOException if the executable is not found.
      LOG.info("Probing GAPIS version");
      process = pb.start();
    }
    catch (IOException e) {
      LOG.warn(e);
      return SERVER_DEFAULT_VERSION;
    }

    final SettableFuture<Version> versionF = SettableFuture.create();

    OutputHandler stdout = new OutputHandler(process.getInputStream(), false) {
      private boolean seenVersion = false;

      @Override
      protected void processLine(String line) {
        super.processLine(line);
        if (!seenVersion && line.startsWith(SERVER_POLL_VERSION_PREFIX)) {
          Version version = Version.parse(line);
          if (version != NULL_VERSION) {
            seenVersion = true;
            versionF.set(version);
          }
        }
      }
    };

    try {
      return versionF.get(SERVER_POLL_VERSION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
    catch (ExecutionException e) {
      return SERVER_DEFAULT_VERSION;
    }
    catch (TimeoutException e) {
      return SERVER_DEFAULT_VERSION;
    }
    catch (InterruptedException e) {
      return SERVER_DEFAULT_VERSION;
    }
    finally {
      process.destroy();
      stdout.close();
    }
  }
}
