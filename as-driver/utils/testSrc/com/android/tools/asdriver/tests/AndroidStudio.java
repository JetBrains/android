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

import com.android.annotations.Nullable;
import com.android.tools.asdriver.proto.ASDriver;
import com.android.tools.asdriver.proto.AndroidStudioGrpc;
import com.android.tools.idea.io.grpc.ManagedChannel;
import com.android.tools.idea.io.grpc.ManagedChannelBuilder;
import com.android.tools.idea.io.grpc.StatusRuntimeException;
import com.android.tools.perflogger.Benchmark;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.system.CpuArch;
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
import org.jetbrains.annotations.NotNull;

public class AndroidStudio implements AutoCloseable {

  private final AndroidStudioGrpc.AndroidStudioBlockingStub androidStudio;
  private final ProcessHandle process;
  private final AndroidStudioInstallation install;
  private final Instant creationTime;

  private VideoStitcher videoStitcher = null;

  private Benchmark benchmark = null;

  static public AndroidStudio run(AndroidStudioInstallation installation,
                                  Display display,
                                  Map<String, String> env,
                                  String[] args) throws IOException, InterruptedException {
    return run(installation, display, env, args, false);
  }

  static public AndroidStudio run(AndroidStudioInstallation installation,
                       Display display,
                       Map<String, String> env,
                       String[] args,
                       boolean safeMode) throws IOException, InterruptedException {
    Path workDir = installation.getWorkDir();

    ArrayList<String> command = new ArrayList<>(args.length + 1);

    String studioExecutable = getStudioExecutable(safeMode);
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
    AndroidStudio studio = attach(installation);
    studio.startCapturingScreenshotsOnWindows();
    return studio;
  }

  static private String getStudioExecutable(boolean useSafeMode) {
    String studioExecutable;

    if (useSafeMode) {
      studioExecutable = "android-studio/bin/studio_safe.sh";
      if (SystemInfo.isMac) {
        studioExecutable = "Android Studio Preview.app/Contents/bin/studio_safe.sh";
      } else if (SystemInfo.isWindows) {
        studioExecutable = "android-studio/bin/studio_safe.bat";
      }
    } else {
      studioExecutable = "android-studio/bin/studio.sh";
      if (SystemInfo.isMac) {
        studioExecutable = "Android Studio Preview.app/Contents/MacOS/studio";
      } else if (SystemInfo.isWindows) {
        studioExecutable = String.format("android-studio/bin/studio%s.exe", CpuArch.isIntel32() ? "" : "64");
      }
    }

    return studioExecutable;
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
    Matcher matcher = reader.waitForMatchingLine(".*STDOUT - as-driver started on pid: (\\d+).*", null, true, 120, TimeUnit.SECONDS);
    return Integer.parseInt(matcher.group(1));
  }

  static private int waitForDriverServer(LogFile reader) throws IOException, InterruptedException {
    Matcher matcher = reader.waitForMatchingLine(".*STDOUT - as-driver server listening at: (\\d+).*", null, true, 30, TimeUnit.SECONDS);
    return Integer.parseInt(matcher.group(1));
  }

  public void waitForProcess() throws ExecutionException, InterruptedException {
    process.onExit().get();
  }

  public void waitForEmulatorStart(LogFile log, Emulator emulator, String appRegex, long timeout, TimeUnit timeUnit)
      throws IOException, InterruptedException {
    log.waitForMatchingLine(
      String.format(".*AndroidProcessHandler - Adding .*emulator-%s.* to monitor for launched app: %s", emulator.getPortString(), appRegex),
      timeout,
      timeUnit
    );
  }

  @Override
  public void close() throws Exception {
    createVideos();
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

  public String getSystemProperty(String systemProperty) {
    ASDriver.GetSystemPropertyRequest rq = ASDriver.GetSystemPropertyRequest.newBuilder().setSystemProperty(systemProperty).build();
    ASDriver.GetSystemPropertyResponse response = androidStudio.getSystemProperty(rq);
    return response.getValue();
  }

  public void addBenchmark(Benchmark benchmark){
    this.benchmark = benchmark;
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
    executeAction(action, DataContextSource.DEFAULT);
  }

  public void executeAction(String action, DataContextSource dataContextSource) {
    ASDriver.ExecuteActionRequest rq =
      ASDriver.ExecuteActionRequest.newBuilder().setActionId(action).setDataContextSource(dataContextSource.dataContextSource).build();
    ASDriver.ExecuteActionResponse response = androidStudio.executeAction(rq);
    switch (response.getResult()) {
      case OK -> {}
      case ACTION_NOT_FOUND -> throw new NoSuchElementException(String.format("No action found by this ID: %s", action));
      case ERROR -> throw new IllegalStateException(String.format("Failed to execute action: %s. %s",
                                                                  action, formatErrorMessage(response.getErrorMessage())));
      default -> throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }
  }

  private void startCapturingScreenshotsOnWindows() throws IOException {
    if (!SystemInfo.isWindows) {
      return;
    }

    Path destination = VideoStitcher.getScreenshotFolder();
    System.out.printf("Setting up screenshot capture (to %s) since this is Windows%n", destination);

    ASDriver.StartCapturingScreenshotsRequest rq =
      ASDriver.StartCapturingScreenshotsRequest.newBuilder().setDestinationPath(destination.toString())
        .setScreenshotNameFormat(VideoStitcher.SCREENSHOT_NAME_FORMAT).build();
    ASDriver.StartCapturingScreenshotsResponse response = androidStudio.startCapturingScreenshots(rq);
    switch (response.getResult()) {
      case OK -> {}
      case ERROR -> throw new IllegalStateException(String.format("Failed to start capturing screenshots: %s",
                                                                  formatErrorMessage(response.getErrorMessage())));
      default -> throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }

    videoStitcher = new VideoStitcher();
    Runtime.getRuntime().addShutdownHook(new Thread(this::createVideos));
  }

  private void createVideos() {
    if (videoStitcher != null) {
      videoStitcher.createVideos();
    }
  }

  /**
   * Waits for the "Resume Program" button, which is only enabled when the debugger has hit a
   * breakpoint.
   */
  public void waitForDebuggerToHitBreakpoint() {
    System.out.println("Waiting for a breakpoint to be hit by the debugger");

    ComponentMatchersBuilder builder = new ComponentMatchersBuilder();
    builder.addSwingClassRegexMatch(".*JBRunnerTabs")
      .addSvgIconMatch(new ArrayList<>(List.of("actions/resume.svg")));
    waitForComponent(builder, true);
  }

  public void invokeByIcon(String icon) {
    ComponentMatchersBuilder builder = new ComponentMatchersBuilder();
    builder.addSvgIconMatch(new ArrayList<>(List.of(icon)));
    invokeComponent(builder);
  }

  public void invokeComponent(String componentText) {
    ComponentMatchersBuilder builder = new ComponentMatchersBuilder();
    builder.addComponentTextExactMatch(componentText);
    invokeComponent(builder);
  }

  public void invokeComponent(ComponentMatchersBuilder matchers) {
    ASDriver.InvokeComponentRequest request = ASDriver.InvokeComponentRequest.newBuilder().addAllMatchers(matchers.build()).build();
    ASDriver.InvokeComponentResponse response = androidStudio.invokeComponent(request);
    switch (response.getResult()) {
      case OK -> {}
      case ERROR -> throw new IllegalStateException(String.format("Could not invoke component with these matchers: %s. %s",
                                                                  matchers, formatErrorMessage(response.getErrorMessage())));
      default -> throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }
  }

  public void waitForIndex() throws IOException, InterruptedException {
    benchmarkLog("calling_waitForIndex");
    System.out.println("Waiting for indexing to complete");
    ASDriver.WaitForIndexRequest rq = ASDriver.WaitForIndexRequest.newBuilder().build();
    ASDriver.WaitForIndexResponse ignore = androidStudio.waitForIndex(rq);
    install.getIdeaLog().reset(); //Log position can be moved past if used after waitForBuild
    install.getIdeaLog().waitForMatchingLine(".*Unindexed files update took.*", 300, TimeUnit.SECONDS);
    benchmarkLog("after_waitForIndex");
  }

  public void waitForNativeBreakpointHit() throws IOException, InterruptedException {
    System.out.println("Waiting for native breakpoint to hit");
    install.getIdeaLog().waitForMatchingLine(".*Native breakpoint hit.*", 3, TimeUnit.MINUTES);
  }

  /**
   * Opens a file and then goes to a specific line and column in the first open project's selected
   * text editor.
   *
   * @param file     the name of the file, interpreted initially as an absolute file path, but subsequently as a project-relative file path
   *                 if no file is initially found.
   * @param line     0-indexed line number.
   * @param column   0-indexed column number.
   * @see com.intellij.openapi.editor.LogicalPosition
   */
  public void openFile(@Nullable String project, @NotNull String file, Integer line, Integer column) {
    openFile(project, file, false);
    goTo(line, column);
  }

  /**
   * Opens a file and then goes to a specific line and column in the first open project's selected
   * text editor.
   *
   * @param file     the name of the file, interpreted initially as an absolute file path, but subsequently as a project-relative file path
   *                 if no file is initially found.
   * @param line     0-indexed line number.
   * @param column   0-indexed column number.
   * @param isWarmup should the metric entries describing this call be marked as warmup
   * @see com.intellij.openapi.editor.LogicalPosition
   */
  public void openFile(@Nullable String project, @NotNull String file, Integer line, Integer column, boolean isWarmup) {
    openFile(project, file, isWarmup);
    goTo(line, column);
  }

  /**
   * Method that calls performanceTesting `openFile` command. Command opens the file with a specified path.
   * The implementation of the command can be found here: com.jetbrains.performancePlugin.commands.OpenFileCommand
   * @param filePath path to the file to be opened
   */
  public void openFile(@Nullable String project, @NotNull final String filePath) {
    openFile(project, filePath, false);
  }

  /**
   * Opens file in the editor.
   * The implementation of the command can be found here: com.jetbrains.performancePlugin.commands.OpenFileCommand
   * @param filePath path to the file to be opened
   * @param isWarmup should the metric entries describing this call be marked as warmup
   */
  public void openFile(@Nullable String project, @NotNull final String filePath, boolean isWarmup) {
    executeCommand("%openFile " + filePath + " -disableCodeAnalysis" + (isWarmup ? " WARMUP" : ""), project);
  }

  /**
   * Moves the caret of the Editor to a specified position.
   * The implementation of the command can be found here: com.jetbrains.performancePlugin.commands.GoToCommand
   * @param line where the caret will be moved (0-indexed)
   * @param column on the line where the caret will be moved (0-indexed)
   */
  private void goTo(int line, int column) {
    executeCommand(String.format("%%goto %d %d", line + 1, column + 1), null);
  }

  public void editFile(String file, String searchRegex, String replacement) {
    ASDriver.EditFileRequest rq =
      ASDriver.EditFileRequest.newBuilder().setFile(file).setSearchRegex(searchRegex).setReplacement(replacement).build();
    ASDriver.EditFileResponse response = androidStudio.editFile(rq);
    switch (response.getResult()) {
      case OK:
        return;
      case ERROR:
        throw new IllegalStateException(String.format("Could not edit file \"%s\" with searchRegex %s and replacement %s. %s",
                                                      file, searchRegex, replacement, formatErrorMessage(response.getErrorMessage())));
      default:
        throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }
  }

  /**
   * Moves caret in the currently open editor to position indicated by the window string.
   *
   * @param window the string indicating where the cursor should be moved. The string needs to contain a `|` character surrounded by a
   *               prefix and/or suffix to be found in the file. The file is searched for the concatenation of prefix and suffix strings
   *               and the caret is placed at the first matching offset, between the prefix and suffix.
   */
  public void moveCaret(String window) {
    ASDriver.MoveCaretRequest rq = ASDriver.MoveCaretRequest.newBuilder().setWindow(window).build();
    ASDriver.MoveCaretResponse response = androidStudio.moveCaret(rq);
    switch (response.getResult()) {
      case OK:
        return;
      case ERROR:
        throw new IllegalStateException(String.format("Could not move caret with window '%s'. %s",
                                                      window, formatErrorMessage(response.getErrorMessage())));
      default:
        throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }
  }

  /** Waits for a {@code Component} whose <b>entire</b> text matches {@code componentText}.*/
  public void waitForComponentWithExactText(String componentText) {
    ComponentMatchersBuilder builder = new ComponentMatchersBuilder();
    builder.addComponentTextExactMatch(componentText);
    waitForComponent(builder);
  }

  /** Waits for a {@code Component} whose text contains {@code containedText}. */
  public void waitForComponentWithTextContaining(String containedText) {
    ComponentMatchersBuilder builder = new ComponentMatchersBuilder();
    builder.addComponentTextContainsMatch(containedText);
    waitForComponent(builder);
  }

  /** Waits for a {@code Component} whose <b>entire</b> text matches the regular expression {@code regex}. */
  public void waitForComponentWithTextMatchingRegex(String regex) {
    ComponentMatchersBuilder builder = new ComponentMatchersBuilder();
    builder.addComponentTextRegexMatch(regex);
    waitForComponent(builder);
  }

  public void waitForComponentByClass(String... classNames){
    waitForComponentByClass(false, classNames);
  }

  public void waitForComponentByClass(boolean waitForEnabled, String... classNames) {
    ComponentMatchersBuilder builder = new ComponentMatchersBuilder();
    for (String className : classNames) {
      builder.addSwingClassRegexMatch(String.format(".*%s.*", className));
    }
    waitForComponent(builder, waitForEnabled);
  }

  public void waitForComponent(ComponentMatchersBuilder requestBuilder){
    waitForComponent(requestBuilder, false);
  }

  public void waitForComponent(ComponentMatchersBuilder requestBuilder, boolean waitForEnabled) {
    ASDriver.WaitForComponentRequest request = ASDriver.WaitForComponentRequest.newBuilder().addAllMatchers(requestBuilder.build()).setWaitForEnabled(waitForEnabled).build();
    ASDriver.WaitForComponentResponse response = androidStudio.waitForComponent(request);
    switch (response.getResult()) {
      case OK -> {}
      case ERROR -> throw new IllegalStateException(String.format("Failed while waiting for component with these matchers: %s. %s",
                                                    requestBuilder, formatErrorMessage(response.getErrorMessage())));
      default -> throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }
  }

  public void waitForProjectInit() {
    // Need to wait for the device selector to be ready
    System.out.println("Wait for ActionToolBar");
    this.waitForComponentByClass(true, "MainToolbar", "MyActionToolbarImpl", "DeviceAndSnapshotComboBoxAction");
  }

  public List<AnalysisResult> analyzeFile(String file) {
    ASDriver.AnalyzeFileRequest.Builder builder = ASDriver.AnalyzeFileRequest.newBuilder().setFile(file);
    ASDriver.AnalyzeFileRequest rq = builder.build();
    ASDriver.AnalyzeFileResponse response = androidStudio.analyzeFile(rq);
    return switch (response.getStatus()) {
      case OK -> response.getAnalysisResultsList().stream().map(AnalysisResult::fromProto).toList();
      case ERROR ->
        throw new IllegalStateException(String.format("Could not analyze file %s. %s",
                                                      file, formatErrorMessage(response.getErrorMessage())));
      default -> throw new IllegalStateException(String.format("Unhandled response: %s", response.getStatus()));
    };
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
    benchmarkLog("calling_waitForSync");
    // "Infinite" timeout
    waitForSync(1, TimeUnit.DAYS);
    benchmarkLog("after_waitForSync");
  }

  public void waitForSync(long timeout, TimeUnit unit) throws IOException, InterruptedException {
    System.out.printf("Waiting up to %d %s for Gradle sync%n", timeout, unit);
    Matcher matcher = install.getIdeaLog()
      .waitForMatchingLine(".*Gradle sync finished in (.*)", ".*org\\.gradle\\.tooling\\.\\w+Exception.*", timeout, unit);
    System.out.println("Sync took " + matcher.group(1));
  }

  /**
   * Triggers basic completion in the editor at the current caret position and checks that the specified completion variants were showed.
   * The implementation of the command can be found here: com.jetbrains.performancePlugin.commands.CompletionCommand and
   * com.jetbrains.performancePlugin.commands.AssertCompletionCommand
   * @param isWarmup should the metric entries describing this call be marked as warmup
   * @param variants expected completion variants
   */
  public void completeCode(boolean isWarmup, String... variants) {
    StringBuilder builder = new StringBuilder().append("%doComplete");
    if (isWarmup) {
      builder.append(" WARMUP");
    }
    executeCommand(builder.toString(), null);

    builder = new StringBuilder().append("%assertCompletionCommand CONTAINS");
    for (String variant : variants) {
      builder.append(" ").append(variant);
    }
    executeCommand(builder.toString(), null);
    executeCommand("%pressKey ESCAPE", null);
  }

  /**
   * Triggers find usages action with the popup and asserts that the usage from the specific file and line is presented.
   * The implementation of the command can be found here: com.jetbrains.performancePlugin.commands.FindUsagesCommand
   * @param isWarmup should the metric entries describing this call be marked as warmup
   * @param path to the file of the symbol to be asserted
   * @param line of the symbol in the file (0-indexed)
   */
  public void findUsages(boolean isWarmup, @NotNull final String path, int line) {
    StringBuilder builder = new StringBuilder().append("%findUsages");
    if (isWarmup) {
      builder.append(" WARMUP");
    }
    executeCommand(builder.toString(), null);

    executeCommand(String.format("%%assertFindUsagesEntryCommand -filePath %s|-line %d", path, line + 1), null);
  }


  /**
   * Closes all editor tabs.
   * The implementation of the command can be found here: com.jetbrains.performancePlugin.commands.CloseAllTabsCommand
   */
  public void closeAllEditorTabs() {
    executeCommand("%closeAllTabs", null);
  }

  /**
   * Executes an editor action with the specified actionId.
   * The implementation of the command can be found here: com.jetbrains.performancePlugin.commands.ExecuteEditorActionCommand
   * @param actionId id of the action to be executed
   * @param isWarmup should the metric entries describing this call be marked as warmup
   */
  public void executeEditorAction(@NotNull final String actionId, boolean isWarmup) {
    StringBuilder builder = new StringBuilder().append("%executeEditorAction ").append(actionId);
    if (isWarmup) {
      builder.append(" WARMUP");
    }
    executeCommand(builder.toString(), null);
  }

  private void executeCommand(@NotNull final String command, @Nullable final String projectName) {
    ASDriver.ExecuteCommandsRequest.Builder requestBuilder =
      ASDriver.ExecuteCommandsRequest.newBuilder();
    requestBuilder.addCommands(command);
    if (projectName != null) {
      requestBuilder.setProjectName(projectName);
    }
    ASDriver.ExecuteCommandsResponse response = androidStudio.executeCommands(requestBuilder.build());
    switch (response.getResult()) {
      case OK -> {}
      case ERROR -> throw new IllegalStateException(String.format("Failed to execute command: %s.",
                                                                  formatErrorMessage(response.getErrorMessage())));
      default -> throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }
  }

  public void runWithBleak(Runnable scenario) {
    scenario.run(); // warm up: for BLeak to track a path in the heap, it must exist when the first snapshot is taken.
    int lastIter = 3;
    for (int i = 0; i < lastIter; i++) {
      ASDriver.TakeBleakSnapshotRequest request = ASDriver.TakeBleakSnapshotRequest.newBuilder().setCurrentIteration(i).setLastIteration(lastIter).build();
      ASDriver.TakeBleakSnapshotResponse response = androidStudio.takeBleakSnapshot(request);
      if (response.getResult() == ASDriver.TakeBleakSnapshotResponse.Result.ERROR) {
        throw new IllegalStateException("Error in BLeak. " + formatErrorMessage(response.getErrorMessage()));
      }
      scenario.run();
    }
    ASDriver.TakeBleakSnapshotRequest request = ASDriver.TakeBleakSnapshotRequest.newBuilder().setCurrentIteration(lastIter).setLastIteration(lastIter).build();
    ASDriver.TakeBleakSnapshotResponse response = androidStudio.takeBleakSnapshot(request);
    switch (response.getResult()) {
      case OK -> {}
      case ERROR -> throw new IllegalStateException("Error in BLeak. " + formatErrorMessage(response.getErrorMessage()));
      case LEAK_DETECTED -> throw new MemoryLeakException(response.getLeakInfo());
    }
  }

  private static String formatErrorMessage(String errorMessage) {
    if (StringUtil.isEmpty(errorMessage)) {
      return "Check the Android Studio stderr log for the cause. See go/e2e-find-log-files for more info.";
    }
    return "Error message: " + errorMessage;
  }

  private void benchmarkLog(String name){
    if (benchmark != null){
      benchmark.log(name, System.currentTimeMillis());
    }
  }

  /**
   * Describes from which component an action should obtain its {@code DataContext}. By default, a minimal {@code DataContext} is
   * constructed with only the {@code Project} and the {@code Editor} specified.
   */
  public enum DataContextSource {
    DEFAULT(ASDriver.ExecuteActionRequest.DataContextSource.DEFAULT),
    SELECTED_TEXT_EDITOR(ASDriver.ExecuteActionRequest.DataContextSource.SELECTED_TEXT_EDITOR);

    // This is the underlying proto enum that we do not want to expose as part of the API.
    private final ASDriver.ExecuteActionRequest.DataContextSource dataContextSource;

    DataContextSource(ASDriver.ExecuteActionRequest.DataContextSource dataContextSource) {
      this.dataContextSource = dataContextSource;
    }
  }

  private static class MemoryLeakException extends RuntimeException {
    private MemoryLeakException(String message) {
      super(message);
    }
  }
}
