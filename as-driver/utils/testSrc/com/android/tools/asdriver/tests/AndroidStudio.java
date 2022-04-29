/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.asdriver.tests;

import com.android.tools.asdriver.proto.ASDriver;
import com.android.tools.asdriver.proto.AndroidStudioGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AndroidStudio implements AutoCloseable {

  private final AndroidStudioGrpc.AndroidStudioBlockingStub androidStudio;
  private final Process process;
  private final AndroidStudioInstallation installation;
  private XvfbServer xvfbServer;
  private BufferedReader logReader;

  public AndroidStudio(AndroidStudioInstallation installation) throws IOException, InterruptedException {
    this.installation = installation;
    Path workDir = installation.getWorkDir();


    ProcessBuilder pb = new ProcessBuilder(workDir.resolve("android-studio/bin/studio.sh").toString());
    pb.redirectError(installation.getStderr().toFile());
    pb.redirectOutput(installation.getStdout().toFile());
    pb.environment().clear();
    String display = System.getenv("DISPLAY");
    if (display == null) {
      // If a display is provided use that, otherwise create one.
      xvfbServer = new XvfbServer();
      display = xvfbServer.launchUnusedDisplay();
    }
    pb.environment().put("DISPLAY", display);
    pb.environment().put("STUDIO_VM_OPTIONS", installation.getVmOptionsPath().toString());
    pb.environment().put("XDG_DATA_HOME", workDir.resolve("data").toString());
    String shell = System.getenv("SHELL");
    if (shell != null) {
      pb.environment().put("SHELL", shell);
    }

    process = pb.start();
    int port = waitForDriverServer(installation.getStdout(), 10000);
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
    androidStudio = AndroidStudioGrpc.newBlockingStub(channel);
  }

  /**
   * Waits for the server to be started by monitoring the standard out.
   * @return the port at which the server was started.
   */
  private int waitForDriverServer(Path stdout, int timeoutMillis) throws IOException, InterruptedException {
    try (BufferedReader reader = new BufferedReader(new FileReader(stdout.toFile()))) {
      String pattern = "as-driver server listening at: (.*)";
      Matcher matcher = waitForMatchingLine(reader, pattern, timeoutMillis);
      return Integer.parseInt(matcher.group(1));
    }
  }


  public int waitFor() throws InterruptedException {
    return process.waitFor();
  }

  public Matcher waitForLog(String regex, int timeoutMillis) throws IOException, InterruptedException {
    if (logReader == null) {
      logReader = new BufferedReader(new FileReader(installation.getIdeaLog().toFile()));
    }
    return waitForMatchingLine(logReader, regex, timeoutMillis);
  }

  private Matcher waitForMatchingLine(BufferedReader reader, String regex, int timeoutMillis) throws IOException, InterruptedException {
    Pattern pattern = Pattern.compile(regex);
    long now = System.currentTimeMillis();
    long elapsed = 0;
    Matcher matcher = null;
    while (elapsed < timeoutMillis) {
      String line = reader.readLine();
      matcher = line == null ? null : pattern.matcher(line);
      if (matcher != null && matcher.matches()) {
        break;
      }
      if (line == null) {
        Thread.sleep(Math.min(1000, timeoutMillis - elapsed));
      }
      elapsed = System.currentTimeMillis() - now;
    }
    if (matcher == null) {
      throw new InterruptedException("Time out while waiting for line");
    }
    return matcher;
  }

  @Override
  public void close() throws Exception {
    try {
      kill(1);
      waitFor();
    } finally {
      if (logReader != null) {
        logReader.close();
      }
      if (xvfbServer != null) {
        xvfbServer.kill();
      }
    }
  }

  public String version() {
    ASDriver.GetVersionRequest rq = ASDriver.GetVersionRequest.newBuilder().build();
    ASDriver.GetVersionResponse response = androidStudio.getVersion(rq);
    return response.getVersion();
  }

  public void kill(int exitCode) {
    ASDriver.KillRequest rq = ASDriver.KillRequest.newBuilder().setExitCode(exitCode).build();
    try {
      ASDriver.KillResponse ignore = androidStudio.kill(rq);
    } catch (StatusRuntimeException e) {
      // Expected as the process is killed.
    }
  }

  public boolean showToolWindow(String toolWindow) {
    ASDriver.ShowToolWindowRequest rq = ASDriver.ShowToolWindowRequest.newBuilder().setToolWindowId(toolWindow).build();
    ASDriver.ShowToolWindowResponse response = androidStudio.showToolWindow(rq);
    return response.getResult() == ASDriver.ShowToolWindowResponse.Result.OK;
  }

  public boolean executeAction(String action) {
    ASDriver.ExecuteActionRequest rq = ASDriver.ExecuteActionRequest.newBuilder().setActionId(action).build();
    ASDriver.ExecuteActionResponse response = androidStudio.executeAction(rq);
    return response.getResult() == ASDriver.ExecuteActionResponse.Result.OK;
  }
}
