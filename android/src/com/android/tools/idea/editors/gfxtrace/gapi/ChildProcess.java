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
import com.google.common.base.Throwables;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.Charset;

public abstract class ChildProcess {
  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);
  private final String myName;
  private Thread myServerThread;
  private int myPort;

  ChildProcess(String name) {
    myName = name;
  }

  protected abstract boolean prepare(ProcessBuilder pb);

  protected abstract void onExit(int code);

  public int port() {
    return myPort;
  }

  public boolean isRunning() {
    return myServerThread != null && myServerThread.isAlive();
  }

  public boolean start() {
    myPort = findFreePort();
    final ProcessBuilder pb = new ProcessBuilder();
    pb.directory(GapiPaths.base());
    pb.redirectErrorStream(true);
    if (!prepare(pb)) {
      return false;
    }
    myServerThread = new Thread() {
      @Override
      public void run() {
        // Use the base directory as the working directory for the server.
        Process process = null;
        try {
          // This will throw IOException if the executable is not found.
          LOG.info("Starting " + myName + " as " + pb.toString());
          process = pb.start();
          final BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.forName("UTF-8")));
          try {
            new Thread() {
              @Override
              public void run() {
                try {
                  for (String line; (line = stdout.readLine()) != null; ) {
                    LOG.info(myName + ": " + line);
                  }
                }
                catch (IOException ignored) {
                }
              }
            }.start();
            onExit(process.waitFor());
          }
          finally {
            stdout.close();
          }
        }
        catch (IOException e) {
          LOG.warn(e);
        }
        catch (InterruptedException e) {
          if (process != null) {
            LOG.info("Killing " + myName);
            process.destroy();
          }
        }
      }
    };
    myServerThread.start();
    return true;
  }

  public void shutdown() {
    LOG.info("Shutting down " + myName);
    myServerThread.interrupt();
  }

  private static int findFreePort() {
    try {
      ServerSocket socket = new ServerSocket(0);
      try {
        return socket.getLocalPort();
      }
      finally {
        socket.close();
      }
    }
    catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }
}
