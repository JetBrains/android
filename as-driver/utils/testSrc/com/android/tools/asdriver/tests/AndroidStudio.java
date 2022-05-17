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
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    for (Map.Entry<String, String> entry : installation.getEnvironmentVariables().entrySet()) {
      pb.environment().put(entry.getKey(), entry.getValue());
    }
    String display = System.getenv("DISPLAY");
    if (display == null || display.isEmpty()) {
      // If a display is provided use that, otherwise create one.
      xvfbServer = new XvfbServer();
      display = xvfbServer.launchUnusedDisplay();
      System.out.println("Display: " + display);
    }
    else {
      System.out.println("Display inherited from parent: " + display);
    }
    pb.environment().put("DISPLAY", display);
    pb.environment().put("XDG_DATA_HOME", workDir.resolve("data").toString());
    String shell = System.getenv("SHELL");
    if (shell != null && !shell.isEmpty()) {
      pb.environment().put("SHELL", shell);
    }

    System.out.println("Starting Android Studio");
    process = pb.start();
    int port = waitForDriverServer(installation.getStdout(), 30000);
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
    androidStudio = AndroidStudioGrpc.newBlockingStub(channel);
  }

  /**
   * Waits for Android Studio to <i>restart</i>. Restarting is done from within Android Studio
   * itself, meaning a new process is spawned whose handle we would have to find if needed.
   */
  public static void waitForRestart(int timeoutMillis) throws InterruptedException, TimeoutException {
    long startTime = System.currentTimeMillis();
    while (true) {
      if (isAnyInstanceOfStudioRunning()) {
        return;
      }
      Thread.sleep(500);
      long elapsedTime = System.currentTimeMillis() - startTime;
      if (elapsedTime >= timeoutMillis) {
        throw new TimeoutException(String.format("Timed out after %dms waiting for Android Studio to restart", elapsedTime));
      }
    }
  }

  /**
   * Terminates all running instances of Android Studio.
   */
  public static void terminateAllStudioInstances() {
    System.out.println("Terminating all instances of Android Studio");
    int numTerminated = 0;
    for (VirtualMachineDescriptor vmd : getRunningStudioInstances()) {
      if (vmd.displayName().equals("com.intellij.idea.Main")) {
        long pid = Long.parseLong(vmd.id());
        Optional<ProcessHandle> of = ProcessHandle.of(pid);
        of.ifPresent(ProcessHandle::destroy);
        numTerminated++;
      }
    }
    System.out.printf("Terminated %d Android Studio instance(s)%n", numTerminated);
  }

  /**
   * Gets all instances of Android Studio that are running, not just ones that the test may have
   * started.
   */
  private static List<VirtualMachineDescriptor> getRunningStudioInstances() {
    Predicate<? super VirtualMachineDescriptor> filterAndroidStudioInstances = (vmd) -> {
      if (vmd.displayName().equals("com.intellij.idea.Main")) {
        try {
          VirtualMachine vm = VirtualMachine.attach(vmd.id());
          Properties properties = vm.getSystemProperties();
          if (Objects.equals(properties.getProperty("idea.platform.prefix"), "AndroidStudio")) {
            return true;
          }
        }
        catch (AttachNotSupportedException | IOException e) {
          // Ignore any VMs we can't attach to
        }
      }
      return false;
    };

    return VirtualMachine.list().stream().filter(filterAndroidStudioInstances).collect(Collectors.toList());
  }

  /**
   * Checks whether <i>any</i> instance of Android Studio is running, not just one that the test
   * might have started.
   */
  public static boolean isAnyInstanceOfStudioRunning() {
    return !getRunningStudioInstances().isEmpty();
  }

  /**
   * Thinly wraps {@code XvfbServer.debugTakeScreenshot}.
   */
  public void debugTakeScreenshot(String fileName) {
    if (xvfbServer == null) {
      System.out.println("Can't take a screenshot; xvfbServer is null");
      return;
    }
    try {
      xvfbServer.debugTakeScreenshot(fileName);
    }
    catch (Exception e) {
      System.out.println("Capturing a screenshot didn't work (ignoring): " + e.getMessage());
    }
  }

  /**
   * Waits for the server to be started by monitoring the standard out.
   *
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

  public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
    return process.waitFor(timeout, unit);
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
      throw new InterruptedException(String.format("Timed out after %dms while waiting for line", timeoutMillis));
    }
    return matcher;
  }

  @Override
  public void close() throws Exception {
    try {
      kill(1);
      waitFor();
    }
    finally {
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

  public boolean executeAction(String action) {
    ASDriver.ExecuteActionRequest rq = ASDriver.ExecuteActionRequest.newBuilder().setActionId(action).build();
    ASDriver.ExecuteActionResponse response = androidStudio.executeAction(rq);
    return response.getResult() == ASDriver.ExecuteActionResponse.Result.OK;
  }

  public boolean updateStudio() {
    ASDriver.UpdateStudioRequest request = ASDriver.UpdateStudioRequest.newBuilder().build();
    ASDriver.UpdateStudioResponse response = androidStudio.updateStudio(request);
    return response.getResult() == ASDriver.UpdateStudioResponse.Result.OK;
  }
}
