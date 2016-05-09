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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Base64;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ChildProcess {
  @NotNull private static final Logger LOG = Logger.getInstance(ChildProcess.class);
  @NotNull private static final Pattern PORT_PATTERN = Pattern.compile("^Bound on port '(\\d+)'$", 0);

  /** The length in characters of an auth-token */
  private static final int AUTH_TOKEN_LENGTH = 8;

  private final String myName;
  private Thread myServerThread;
  protected Process myProcess;

  ChildProcess(String name) {
    myName = name;
  }

  protected abstract boolean prepare(ProcessBuilder pb);

  protected abstract void onExit(int code);

  public boolean isRunning() {
    return myServerThread != null && myServerThread.isAlive();
  }

  public SettableFuture<Integer> start() {
    final ProcessBuilder pb = new ProcessBuilder();
    pb.directory(GapiPaths.base());
    if (!prepare(pb)) {
      return null;
    }
    final SettableFuture<Integer> portF = SettableFuture.create();
    myServerThread = new Thread() {
      @Override
      public void run() {
        runProcess(portF, pb);
      }
    };
    myServerThread.start();
    return portF;
  }

  private void runProcess(final SettableFuture<Integer> portF, final ProcessBuilder pb) {
    // Use the base directory as the working directory for the server.
    try {
      // This will throw IOException if the executable is not found.
      LOG.info("Starting " + myName + " as " + pb.command());
      myProcess = pb.start();
    }
    catch (IOException e) {
      LOG.warn(e);
      portF.setException(e);
      return;
    }

    OutputHandler stdout = new OutputHandler(myProcess.getInputStream(), false) {
      private boolean seenPort = false;

      @Override
      protected void processLine(String line) {
        super.processLine(line);
        if (!seenPort) {
          Matcher matcher = PORT_PATTERN.matcher(line);
          if (matcher.matches()) {
            int port = Integer.parseInt(matcher.group(1));
            seenPort = true;
            portF.set(port);
            LOG.info("Detected server " + myName + " startup on port " + port);
          }
        }
      }
    };
    OutputHandler stderr = new OutputHandler(myProcess.getErrorStream(), true);
    try {
      onExit(myProcess.waitFor());
    }
    catch (InterruptedException e) {
      LOG.info("Killing " + myName);
      portF.setException(e);
      myProcess.destroy();
    }
    finally {
      stdout.close();
      stderr.close();
    }
  }

  public void shutdown() {
    LOG.info("Shutting down " + myName);
    myServerThread.interrupt();
  }

  /** @return a randomly generated auth-token string. */
  @NotNull
  protected static String generateAuthToken() {
    SecureRandom rnd = new SecureRandom();
    byte[] bytes = new byte[AUTH_TOKEN_LENGTH * 3 / 4];
    rnd.nextBytes(bytes);
    return Base64.encode(bytes);
  }

  protected class OutputHandler extends Thread implements Closeable {
    private final BufferedReader reader;
    private final boolean warn;

    public OutputHandler(InputStream in, boolean warn) {
      this.reader = new BufferedReader(new InputStreamReader(in, Charset.forName("UTF-8")));
      this.warn = warn;
      start();
    }

    @Override
    public void run() {
      try {
        for (String line; (line = reader.readLine()) != null; ) {
          processLine(line);
        }
      }
      catch (IOException e) { /* ignore */ }
    }

    protected void processLine(String line) {
      if (warn) {
        LOG.warn(myName + ": " + line);
      } else {
        LOG.info(myName + ": " + line);
      }
    }

    @Override
    public void close() {
      try {
        reader.close();
      }
      catch (IOException e) { /* ignore */ }
    }
  }
}
