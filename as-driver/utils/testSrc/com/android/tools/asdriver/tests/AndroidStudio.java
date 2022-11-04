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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.system.CpuArch;
import com.android.tools.idea.io.grpc.ManagedChannel;
import com.android.tools.idea.io.grpc.ManagedChannelBuilder;
import com.android.tools.idea.io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

public class AndroidStudio implements AutoCloseable {

  private final AndroidStudioGrpc.AndroidStudioBlockingStub androidStudio;
  private final ProcessHandle process;
  private final AndroidStudioInstallation install;
  private final Instant creationTime;

  static public AndroidStudio run(AndroidStudioInstallation installation,
                       Display display,
                       Map<String, String> env,
                       String[] args) throws IOException, InterruptedException {
    Path workDir = installation.getWorkDir();

    ArrayList<String> command = new ArrayList<>(args.length + 1);

    String studioExecutable = "android-studio/bin/studio.sh";
    if (SystemInfo.isMac) {
      studioExecutable = "Android Studio Preview.app/Contents/MacOS/studio";
    } else if (SystemInfo.isWindows) {
      studioExecutable = String.format("android-studio/bin/studio%s.exe", CpuArch.isIntel32() ? "" : "64");
    }
    command.add(workDir.resolve(studioExecutable).toString());
    command.addAll(Arrays.asList(args));
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.environment().clear();

    for (Map.Entry<String, String> entry : env.entrySet()) {
      pb.environment().put(entry.getKey(), entry.getValue());
    }
    if (display.getDisplay() != null) {
      pb.environment().put("DISPLAY", display.getDisplay());
    }
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
    // We execute it and let the process instance go, as it reflects
    // the shell process, not the idea one.
    pb.start();
    // Now we attach to the real one from the logs
    return attach(installation);
  }

  static AndroidStudio attach(AndroidStudioInstallation installation) throws IOException, InterruptedException {
    int pid;
    try {
      pid = waitForDriverPid(installation.getIdeaLog());
    } catch (InterruptedException e) {
      checkForJdwpError(installation);
      throw e;
    }

    ProcessHandle process = ProcessHandle.of(pid).get();
    int port = waitForDriverServer(installation.getIdeaLog());
    return new AndroidStudio(installation, process, port);
  }

  private AndroidStudio(AndroidStudioInstallation install, ProcessHandle process, int port) {
    this.install = install;
    this.process = process;
    creationTime = Instant.now();
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
    androidStudio = AndroidStudioGrpc.newBlockingStub(channel);
  }

  /**
   * Checks to see if Android Studio failed to start because the JDWP address was already in use.
   * This method will throw an exception in the test process rather than the developer having to
   * check Android Studio's stderr itself. I.e. this method is purely for developer convenience
   * when testing locally.
   */
  static private void checkForJdwpError(AndroidStudioInstallation installation) {
    try {
      List<String> stderrContents = Files.readAllLines(installation.getStderr().getPath());
      boolean hasJdwpError = stderrContents.stream().anyMatch((line) -> line.contains("JDWP exit error AGENT_ERROR_TRANSPORT_INIT"));
      boolean isAddressInUse = stderrContents.stream().anyMatch((line) -> line.contains("Address already in use"));
      if (hasJdwpError && isAddressInUse) {
        throw new IllegalStateException("The JDWP address is already in use. You can fix this either by removing your " +
                                        "AS_TEST_DEBUG env var or by terminating the existing Android Studio process.");
      }
    }
    catch (IOException e) {
      // We tried our best. :(
    }
  }

  static private int waitForDriverPid(LogFile reader) throws IOException, InterruptedException {
    Matcher matcher = reader.waitForMatchingLine(".*STDOUT - as-driver started on pid: (\\d+).*", null, true, 30, TimeUnit.SECONDS);
    return Integer.parseInt(matcher.group(1));
  }

  static private int waitForDriverServer(LogFile reader) throws IOException, InterruptedException {
    Matcher matcher = reader.waitForMatchingLine(".*STDOUT - as-driver server listening at: (\\d+).*", null, true, 30, TimeUnit.SECONDS);
    return Integer.parseInt(matcher.group(1));
  }

  public void waitForProcess() throws ExecutionException, InterruptedException {
    process.onExit().get();
  }

  @Override
  public void close() throws Exception {
    quitAndWaitForShutdown();
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

  /**
   * @param force true to kill Studio right away, false to gracefully exit. Note that killing Studio will not allow it to run
   *              cleanup processes, such as stopping Gradle, which would let Gradle to continue writing to the filesystem.
   */
  public void quit(boolean force) {
    ASDriver.QuitRequest rq = ASDriver.QuitRequest.newBuilder().setForce(force).build();
    try {
      ASDriver.QuitResponse ignore = androidStudio.quit(rq);
    }
    catch (StatusRuntimeException e) {
      // Expected as the process is killed.
    }
  }

  /**
   * Works around b/253431062 and b/256687010, which occur on Windows when Android Studio tries to
   * initialize a class despite already being in the shutdown process. The specific failure is:
   * "Sorry but parent [...] has already been disposed [...] so the child [...] will never be
   * disposed".
   *
   * We hit this specifically when a test only needs to verify that Android Studio started
   * correctly, which means we close Android Studio so quickly that we didn't give initialization
   * time to finish.
   *
   * This is not intended to be a permanent solution. However, at least the impact is minimal;
   * we end up waiting ~5 extra seconds for initialization to complete before quitting.
   */
  private void waitToWorkAroundWindowsIssue() throws InterruptedException {
    if (!SystemInfo.isWindows) {
      return;
    }

    Duration elapsedTime = Duration.between(creationTime, Instant.now());
    Duration minimumTimeToStayOpen = Duration.ofSeconds(10);
    if (elapsedTime.compareTo(minimumTimeToStayOpen) < 0) {
      long msToWait = Math.max(Duration.ofSeconds(1).toMillis(), minimumTimeToStayOpen.toMillis() - elapsedTime.toMillis());
      System.out.printf("This AndroidStudio instance was only created %dms ago, so waiting for %dms until quitting.%n", elapsedTime.toMillis(), msToWait);
      Thread.sleep(msToWait);
      System.out.println("Done waiting");
    }
  }

  /**
   * Quit Studio such that Gradle and other Studio-owned processes are properly disposed of.
   */
  private void quitAndWaitForShutdown() throws IOException, InterruptedException {
    System.out.println("Quitting Studio...");
    waitToWorkAroundWindowsIssue();
    quit(false);
    install.getIdeaLog().waitForMatchingLine(".*PersistentFSImpl - VFS dispose completed.*", 30, TimeUnit.SECONDS);
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

  public void invokeByIcon(String icon) {
    ComponentMatchersBuilder builder = new ComponentMatchersBuilder();
    builder.addSvgIconMatch(new ArrayList<>(List.of(icon)));
    invokeComponent(builder);
  }

  public void invokeComponent(String componentText) {
    ComponentMatchersBuilder builder = new ComponentMatchersBuilder();
    builder.addComponentTextMatch(componentText);
    invokeComponent(builder);
  }

  public void invokeComponent(ComponentMatchersBuilder matchers) {
    ASDriver.InvokeComponentRequest request = ASDriver.InvokeComponentRequest.newBuilder().addAllMatchers(matchers.build()).build();
    ASDriver.InvokeComponentResponse response = androidStudio.invokeComponent(request);
    switch (response.getResult()) {
      case OK:
        return;
      case ERROR:
        throw new IllegalStateException(String.format("Could not invoke component with these matchers: %s. Check the Android Studio " +
                                                      "stderr log for the cause.", matchers));
      default:
        throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }
  }

  public void waitForIndex() {
    System.out.println("Waiting for indexing to complete");
    ASDriver.WaitForIndexRequest rq = ASDriver.WaitForIndexRequest.newBuilder().build();
    ASDriver.WaitForIndexResponse ignore = androidStudio.waitForIndex(rq);
  }

  /**
   * Opens a file and then goes to a specific line and column in the first open project's selected
   * text editor.
   * @param line 0-indexed line number.
   * @param column 0-indexed column number.
   * @see com.intellij.openapi.editor.LogicalPosition
   */
  public void openFile(String project, String file, Integer line, Integer column) {
    ASDriver.OpenFileRequest.Builder builder = ASDriver.OpenFileRequest.newBuilder().setProject(project).setFile(file);
    if (line != null) {
      builder.setLine(line);
    }
    if (column != null) {
      builder.setColumn(column);
    }
    ASDriver.OpenFileRequest rq = builder.build();
    ASDriver.OpenFileResponse response = androidStudio.openFile(rq);
    switch (response.getResult()) {
      case OK:
        return;
      case ERROR:
        throw new IllegalStateException(String.format("Could not open file \"%s\" in project \"%s\" to line %d:%d. Check the Android " +
                                                      "Studio stderr log for the cause.", file, project, line, column));
      default:
        throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }
  }

  public void openFile(String project, String file) {
    openFile(project, file, null, null);
  }

  public void editFile(String file, String searchRegex, String replacement) {
    ASDriver.EditFileRequest rq =
      ASDriver.EditFileRequest.newBuilder().setFile(file).setSearchRegex(searchRegex).setReplacement(replacement).build();
    ASDriver.EditFileResponse response = androidStudio.editFile(rq);
    switch (response.getResult()) {
      case OK:
        return;
      case ERROR:
        throw new IllegalStateException(String.format("Could not edit file \"%s\" with searchRegex %s and replacement %s. Check the " +
                                                      "Android Studio stderr log for the cause.", file, searchRegex, replacement));
      default:
        throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }
  }

  public void waitForComponent(String componentText) {
    ComponentMatchersBuilder builder = new ComponentMatchersBuilder();
    builder.addComponentTextMatch(componentText);
    waitForComponent(builder);
  }

  public void waitForComponentByClass(String... classNames) {
    ComponentMatchersBuilder builder = new ComponentMatchersBuilder();
    for (String className : classNames) {
      builder.addSwingClassRegexMatch(String.format(".*%s.*", className));
    }
    waitForComponent(builder);
  }

  public void waitForComponent(ComponentMatchersBuilder requestBuilder) {
    ASDriver.WaitForComponentRequest request = ASDriver.WaitForComponentRequest.newBuilder().addAllMatchers(requestBuilder.build()).build();
    ASDriver.WaitForComponentResponse response = androidStudio.waitForComponent(request);
    switch (response.getResult()) {
      case OK:
        return;
      case ERROR:
        throw new IllegalStateException(String.format("Could not wait for component with these matchers: %s. Check the Android Studio " +
                                                      "stderr log for the cause.", requestBuilder));
      default:
        throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }
  }

  public void waitForBuild() throws IOException, InterruptedException {
    // "Infinite" timeout
    waitForBuild(1, TimeUnit.DAYS);
  }

  public void waitForBuild(long timeout, TimeUnit unit) throws IOException, InterruptedException {
    System.out.printf("Waiting up to %d %s for Gradle build%n", timeout, unit);
    Matcher matcher = install.getIdeaLog()
      .waitForMatchingLine(".*Gradle build finished in (.*)", ".*org\\.gradle\\.tooling\\.\\w+Exception.*", timeout, unit);
    System.out.println("Build took " + matcher.group(1));
  }

  public void waitForSync() throws IOException, InterruptedException {
    // "Infinite" timeout
    waitForSync(1, TimeUnit.DAYS);
  }

  public void waitForSync(long timeout, TimeUnit unit) throws IOException, InterruptedException {
    System.out.printf("Waiting up to %d %s for Gradle sync%n", timeout, unit);
    Matcher matcher = install.getIdeaLog()
      .waitForMatchingLine(".*Gradle sync finished in (.*)", ".*org\\.gradle\\.tooling\\.\\w+Exception.*", timeout, unit);
    System.out.println("Sync took " + matcher.group(1));
  }
}
