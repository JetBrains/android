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

public class AndroidStudio implements AutoCloseable {

  private final AndroidStudioGrpc.AndroidStudioBlockingStub androidStudio;
  private final Process process;
  private final AndroidStudioInstallation installation;
  private BufferedReader logReader;

  public AndroidStudio(AndroidStudioInstallation installation) throws IOException, InterruptedException {
    this.installation = installation;
    Path workDir = installation.getWorkDir();

    // TODO enable xvfb when we run in bazel
    // XvfbServer server = new XvfbServer();
    // String display = server.launchUnusedDisplay();

    ProcessBuilder pb = new ProcessBuilder(workDir.resolve("android-studio/bin/studio.sh").toString());
    pb.redirectError(installation.getStderr().toFile());
    pb.redirectOutput(installation.getStdout().toFile());
    pb.environment().clear();
    //TODO: Be smarter about choosing a display. Use xvfb in bazel, and local env otherwise.
    //pb.environment().put("DISPLAY", display); // XVFB
    //pb.environment().put("DISPLAY", ":1"); // LOCAL
    pb.environment().put("DISPLAY", ":20"); // CRD
    pb.environment().put("STUDIO_VM_OPTIONS", installation.getVmOptionsPath().toString());
    pb.environment().put("XDG_DATA_HOME", workDir.resolve("data").toString());
    String shell = System.getenv("SHELL");
    if (shell != null) {
      pb.environment().put("SHELL", shell);
    }

    process = pb.start();

    //TODO Remove this sleep, we do this to give time for the server to start up. Needs to read stdout.txt instead.
    Thread.sleep(5000);
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 5678).usePlaintext().build();
    androidStudio = AndroidStudioGrpc.newBlockingStub(channel);
  }



  public int waitFor() throws InterruptedException {
    return process.waitFor();
  }

  public String waitForLog(String regex, int timeoutMillis) throws IOException, InterruptedException {
    if (logReader == null) {
      logReader = new BufferedReader(new FileReader(installation.getIdeaLog().toFile()));
    }
    long now = System.currentTimeMillis();
    long elapsed = 0;
    String line = null;
    while (elapsed < timeoutMillis) {
      line = logReader.readLine();
      if (line != null && line.matches(regex)) {
        break;
      }
      if (line == null) {
        Thread.sleep(Math.min(1000, timeoutMillis - elapsed));
      }
      elapsed = System.currentTimeMillis() - now;
    }
    if (line == null) {
      throw new InterruptedException("Time out while waiting for log");
    }
    return line;
  }

  @Override
  public void close() throws Exception {
    kill(1);
    waitFor();
    if (logReader != null) {
      logReader.close();
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
