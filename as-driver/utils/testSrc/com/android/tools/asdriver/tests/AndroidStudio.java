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
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

public class AndroidStudio implements AutoCloseable {

  private final AndroidStudioGrpc.AndroidStudioBlockingStub androidStudio;
  private final ProcessHandle process;

  static public AndroidStudio run(AndroidStudioInstallation installation,
                       Display display,
                       Map<String, String> env,
                       String[] args) throws IOException, InterruptedException {
    Path workDir = installation.getWorkDir();

    ArrayList<String> command = new ArrayList<>(args.length + 1);
    command.add(workDir.resolve("android-studio/bin/studio.sh").toString());
    command.addAll(Arrays.asList(args));
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.environment().clear();

    for (Map.Entry<String, String> entry : env.entrySet()) {
      pb.environment().put(entry.getKey(), entry.getValue());
    }
    pb.environment().put("DISPLAY", display.getDisplay());
    pb.environment().put("XDG_DATA_HOME", workDir.resolve("data").toString());
    String shell = System.getenv("SHELL");
    if (shell != null && !shell.isEmpty()) {
      pb.environment().put("SHELL", shell);
    }

    System.out.println("Starting Android Studio");
    installation.getStdout().reset();
    installation.getStderr().reset();
    pb.redirectOutput(installation.getStdout().getPath().toFile());
    pb.redirectError(installation.getStderr().getPath().toFile());
    ProcessHandle process = pb.start().toHandle();
    int port = waitForDriverServer(installation.getIdeaLog());
    return new AndroidStudio(process, port);
  }

  static AndroidStudio attach(AndroidStudioInstallation installation) throws IOException, InterruptedException {
    int pid = waitForDriverPid(installation.getIdeaLog());
    ProcessHandle process = ProcessHandle.of(pid).get();
    int port = waitForDriverServer(installation.getIdeaLog());
    return new AndroidStudio(process, port);
  }

  private AndroidStudio(ProcessHandle process, int port) {
    this.process = process;
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
    androidStudio = AndroidStudioGrpc.newBlockingStub(channel);
  }

  static private int waitForDriverPid(LogFile reader) throws IOException, InterruptedException {
    Matcher matcher = reader.waitForMatchingLine(".*STDOUT - as-driver started on pid: (\\d+).*", true, 30, TimeUnit.SECONDS);
    return Integer.parseInt(matcher.group(1));
  }

  static private int waitForDriverServer(LogFile reader) throws IOException, InterruptedException {
    Matcher matcher = reader.waitForMatchingLine(".*STDOUT - as-driver server listening at: (\\d+).*", true, 30, TimeUnit.SECONDS);
    return Integer.parseInt(matcher.group(1));
  }

  public void waitForProcess() throws ExecutionException, InterruptedException {
    process.onExit().get();
  }

  @Override
  public void close() throws Exception {
    // We must terminate the process on close. If we don't and expect the test to gracefully terminate it always, it means
    // that if the test has an assertEquals, when the assertion exception is thrown the try-catch will attempt to close
    // this object that has not been asked to terminate, blocking forever until the test times out, swallowing the
    // assertion information.
    process.destroyForcibly();
    waitForProcess();
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
    }
    catch (StatusRuntimeException e) {
      // Expected as the process is killed.
    }
  }

  public boolean showToolWindow(String toolWindow) {
    ASDriver.ShowToolWindowRequest rq = ASDriver.ShowToolWindowRequest.newBuilder().setToolWindowId(toolWindow).build();
    ASDriver.ShowToolWindowResponse response = androidStudio.showToolWindow(rq);
    return response.getResult() == ASDriver.ShowToolWindowResponse.Result.OK;
  }

  public void executeAction(String action) {
    ASDriver.ExecuteActionRequest rq = ASDriver.ExecuteActionRequest.newBuilder().setActionId(action).build();
    ASDriver.ExecuteActionResponse response = androidStudio.executeAction(rq);
    switch (response.getResult()) {
      case OK:
        return;
      case ACTION_NOT_FOUND:
        throw new NoSuchElementException(String.format("No action found by this ID: %s", action));
      default:
        throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }
  }

  public void invokeComponent(String componentText) {
    InvokeComponentRequestBuilder builder = new InvokeComponentRequestBuilder();
    builder.addComponentTextMatch(componentText);
    invokeComponent(builder);
  }

  public void invokeComponent(InvokeComponentRequestBuilder requestBuilder) {
    ASDriver.InvokeComponentResponse response = androidStudio.invokeComponent(requestBuilder.build());
    switch (response.getResult()) {
      case OK:
        return;
      case ERROR:
        throw new IllegalStateException(String.format("Could not invoke component with these matchers: %s. Check the Android Studio " +
                                                       "stderr log for the cause.", requestBuilder));
      default:
        throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }
  }
}
