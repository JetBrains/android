/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.asdriver.tests.base;

import com.android.annotations.Nullable;
import com.android.tools.asdriver.proto.ASDriver;
import com.android.tools.asdriver.proto.AndroidStudioGrpc;
import com.android.tools.asdriver.tests.AnalysisResult;
import com.android.tools.asdriver.tests.ComponentMatchersBuilder;
import com.android.tools.asdriver.tests.VideoStitcher;
import com.android.tools.idea.io.grpc.ManagedChannel;
import com.android.tools.idea.io.grpc.ManagedChannelBuilder;
import com.android.tools.testlib.Emulator;
import com.android.tools.testlib.LogFile;
import com.android.tools.testlib.TestLogger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public abstract class Ide implements AutoCloseable{

  protected final AndroidStudioGrpc.AndroidStudioBlockingStub ide;
  protected final ProcessHandle process;
  protected final Instant creationTime;

  private VideoStitcher videoStitcher = null;

  /**
   * This constructor implementation is for IntelliJ {@link com.android.tools.asdriver.tests.base.IntelliJ}
   */
  protected Ide() {
    this.process = null;
    creationTime = Instant.now();
    this.ide = null;
  }

  protected Ide(ProcessHandle process, int port) {
    this.process = process;
    creationTime = Instant.now();
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
    ide = AndroidStudioGrpc.newBlockingStub(channel);
  }

  public void waitForProcess() throws ExecutionException, InterruptedException {
    process.onExit().get();
  }

  public String version() {
    ASDriver.GetVersionRequest rq = ASDriver.GetVersionRequest.newBuilder().build();
    ASDriver.GetVersionResponse response = ide.getVersion(rq);
    return response.getVersion();
  }

  public String getSystemProperty(String systemProperty) {
    ASDriver.GetSystemPropertyRequest rq = ASDriver.GetSystemPropertyRequest.newBuilder().setSystemProperty(systemProperty).build();
    ASDriver.GetSystemPropertyResponse response = ide.getSystemProperty(rq);
    return response.getValue();
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
    ASDriver.MoveCaretResponse response = ide.moveCaret(rq);
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
    openFile(project, file, line, column, true, isWarmup);
  }

  /**
   * Opens a file and then goes to a specific line and column in the first open project's selected
   * text editor.
   *
   * @param file     the name of the file, interpreted initially as an absolute file path, but subsequently as a project-relative file path
   *                 if no file is initially found.
   * @param line     0-indexed line number.
   * @param column   0-indexed column number.
   * @param disableCodeAnalysis should the method wait for the code analysis following the file opening to finish
   * @param isWarmup should the metric entries describing this call be marked as warmup
   * @see com.intellij.openapi.editor.LogicalPosition
   */
  public void openFile(@Nullable String project,
                       @NotNull String file,
                       Integer line,
                       Integer column,
                       boolean disableCodeAnalysis,
                       boolean isWarmup) {
    openFile(project, file, disableCodeAnalysis, isWarmup);
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
    openFile(project, filePath, true, isWarmup);
  }
  /**
   * Opens file in the editor.
   * The implementation of the command can be found here: com.jetbrains.performancePlugin.commands.OpenFileCommand
   * @param filePath path to the file to be opened
   * @param disableCodeAnalysis should the method wait for the code analysis following the file opening to finish
   * @param isWarmup should the metric entries describing this call be marked as warmup
   */
  public void openFile(@Nullable String project, @NotNull final String filePath, boolean disableCodeAnalysis, boolean isWarmup) {
    executeCommand("%openFile " + filePath + (disableCodeAnalysis ? " -disableCodeAnalysis" : "") + (isWarmup ? " WARMUP" : ""), project);
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

  /**
   * Command types text with some delay between typing.
   *
   * @param project optional project name
   * @param delayMs delay between typing characters in milliseconds
   * @param text text to type
   */
  public void delayType(@Nullable String project,
                        int delayMs,
                        @NotNull final String text) {
    executeCommand(String.format("%%delayType %d|%s|false|false", delayMs, text), project);
  }

  /**
   * Triggers find usages action with the popup and asserts that the usage from the specific file and line is presented.
   * The implementation of the command can be found here: com.jetbrains.performancePlugin.commands.FindUsagesCommand
   * @param isWarmup should the metric entries describing this call be marked as warmup
   * @param path to the file of the symbol to be asserted
   * @param line of the symbol in the file (0-indexed)
   */
  public void findUsages(boolean isWarmup, @NotNull final String path, int line) {
    findUsages(isWarmup);

    executeCommand(String.format("%%assertFindUsagesEntryCommand -filePath %s|-line %d", path, line + 1), null);
  }
  public void findUsages(boolean isWarmup) {
    StringBuilder builder = new StringBuilder().append("%findUsages");
    if (isWarmup) {
      builder.append(" WARMUP");
    }
    executeCommand(builder.toString(), null);
  }

  /**
   * Closes all editor tabs.
   * The implementation of the command can be found here: com.jetbrains.performancePlugin.commands.CloseAllTabsCommand
   */
  public void closeAllEditorTabs() {
    executeCommand("%closeAllTabs", null);
  }

  public void altEnter(@NotNull final String intention, boolean invoke) {
    executeCommand(String.format("%%altEnter %s|%b", intention, invoke), null);
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

  public void waitForSmart() {
    executeCommand("%waitForSmart", null);
  }

  protected void executeCommand(@NotNull final String command, @Nullable final String projectName) {
    ASDriver.ExecuteCommandsRequest.Builder requestBuilder =
      ASDriver.ExecuteCommandsRequest.newBuilder();
    requestBuilder.addCommands(command);
    if (projectName != null) {
      requestBuilder.setProjectName(projectName);
    }
    ASDriver.ExecuteCommandsResponse response = ide.executeCommands(requestBuilder.build());
    switch (response.getResult()) {
      case OK -> {}
      case ERROR -> throw new IllegalStateException(String.format("Failed to execute command: %s.",
                                                                  formatErrorMessage(response.getErrorMessage())));
      default -> throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }
  }

  public boolean showToolWindow(String toolWindow) {
    ASDriver.ShowToolWindowRequest rq = ASDriver.ShowToolWindowRequest.newBuilder().setToolWindowId(toolWindow).build();
    ASDriver.ShowToolWindowResponse response = ide.showToolWindow(rq);
    return response.getResult() == ASDriver.ShowToolWindowResponse.Result.OK;
  }

  public void startCapturingScreenshotsOnWindows() throws IOException {
    if (!SystemInfo.isWindows) {
      return;
    }

    Path destination = VideoStitcher.getScreenshotFolder();
    TestLogger.log("Setting up screenshot capture (to %s) since this is Windows", destination);
    ASDriver.StartCapturingScreenshotsRequest rq =
      ASDriver.StartCapturingScreenshotsRequest.newBuilder().setDestinationPath(destination.toString())
        .setScreenshotNameFormat(VideoStitcher.SCREENSHOT_NAME_FORMAT).build();
    ASDriver.StartCapturingScreenshotsResponse response = ide.startCapturingScreenshots(rq);
    switch (response.getResult()) {
      case OK -> {}
      case ERROR -> throw new IllegalStateException(String.format("Failed to start capturing screenshots: %s",
                                                                  formatErrorMessage(response.getErrorMessage())));
      default -> throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }

    videoStitcher = new VideoStitcher();
    Runtime.getRuntime().addShutdownHook(new Thread(this::createVideos));
  }

  protected void createVideos() {
    if (videoStitcher != null) {
      videoStitcher.createVideos();
    }
  }

  public void waitForEmulatorStart(LogFile log, Emulator emulator, String appRegex, long timeout, TimeUnit timeUnit)
    throws IOException, InterruptedException {
    log.waitForMatchingLine(
      String.format(".*AndroidProcessHandler - Adding .*%s.* to monitor for launched app: %s", emulator.getSerialNumber(), appRegex),
      timeout,
      timeUnit
    );
  }

  public void waitForFinishedCodeAnalysis(@Nullable String project) {
    executeCommand("%waitForFinishedCodeAnalysis", project);
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
    ASDriver.WaitForComponentResponse response = ide.waitForComponent(request);
    switch (response.getResult()) {
      case OK -> {}
      case ERROR -> throw new IllegalStateException(String.format("Failed while waiting for component with these matchers: %s. %s",
                                                                  requestBuilder, formatErrorMessage(response.getErrorMessage())));
      default -> throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }
  }

  public void waitForProjectInit() {
    // Need to wait for the device selector to be ready
    TestLogger.log("Wait for ActionToolBar");
    this.waitForComponentByClass(true, "MainToolbar", "MyActionToolbarImpl", "DeviceAndSnapshotComboBoxAction");
  }

  /**
   * Waits for the "Resume Program" button, which is only enabled when the debugger has hit a
   * breakpoint.
   */
  public void waitForDebuggerToHitBreakpoint() {
    System.out.println("Waiting for a breakpoint to be hit by the debugger");

    ComponentMatchersBuilder builder = new ComponentMatchersBuilder();
    builder.addSwingClassRegexMatch(".*InternalDecoratorImpl")
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
    ASDriver.InvokeComponentResponse response = ide.invokeComponent(request);
    switch (response.getResult()) {
      case OK -> {}
      case ERROR -> throw new IllegalStateException(String.format("Could not invoke component with these matchers: %s. %s",
                                                                  matchers, formatErrorMessage(response.getErrorMessage())));
      default -> throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }
  }

  public List<AnalysisResult> analyzeFile(String file) {
    ASDriver.AnalyzeFileRequest.Builder builder = ASDriver.AnalyzeFileRequest.newBuilder().setFile(file);
    ASDriver.AnalyzeFileRequest rq = builder.build();
    ASDriver.AnalyzeFileResponse response = ide.analyzeFile(rq);
    return switch (response.getStatus()) {
      case OK -> response.getAnalysisResultsList().stream().map(AnalysisResult::fromProto).toList();
      case ERROR ->
        throw new IllegalStateException(String.format("Could not analyze file %s. %s",
                                                      file, formatErrorMessage(response.getErrorMessage())));
      default -> throw new IllegalStateException(String.format("Unhandled response: %s", response.getStatus()));
    };
  }

  public void runWithBleak(Runnable scenario) {
    scenario.run(); // warm up: for BLeak to track a path in the heap, it must exist when the first snapshot is taken.
    int lastIter = 3;
    for (int i = 0; i < lastIter; i++) {
      ASDriver.TakeBleakSnapshotRequest request = ASDriver.TakeBleakSnapshotRequest.newBuilder().setCurrentIteration(i).setLastIteration(lastIter).build();
      ASDriver.TakeBleakSnapshotResponse response = ide.takeBleakSnapshot(request);
      if (response.getResult() == ASDriver.TakeBleakSnapshotResponse.Result.ERROR) {
        throw new IllegalStateException("Error in BLeak. " + formatErrorMessage(response.getErrorMessage()));
      }
      scenario.run();
    }
    ASDriver.TakeBleakSnapshotRequest request = ASDriver.TakeBleakSnapshotRequest.newBuilder().setCurrentIteration(lastIter).setLastIteration(lastIter).build();
    ASDriver.TakeBleakSnapshotResponse response = ide.takeBleakSnapshot(request);
    switch (response.getResult()) {
      case OK -> {}
      case ERROR -> throw new IllegalStateException("Error in BLeak. " + formatErrorMessage(response.getErrorMessage()));
      case LEAK_DETECTED -> throw new Ide.MemoryLeakException(response.getLeakInfo());
    }
  }

  /**
   * Opens a project in a new window
   * The implementation of the command can be found here: com.jetbrains.performancePlugin.commands.OpenProjectCommand
   * @param projectPath path to the project to be opened
   */
  public void openProject(@NotNull final String projectPath, boolean newWindow) {
    System.out.println("Opening project: " + projectPath);
    ASDriver.OpenProjectRequest.Builder rqBuilder =
      ASDriver.OpenProjectRequest.newBuilder().setProjectPath(projectPath).setNewWindow(newWindow);
    ASDriver.OpenProjectResponse response = ide.openProject(rqBuilder.build());
    switch (response.getResult()) {
      case OK:
        return;
      case ERROR:
        throw new IllegalStateException(String.format("Could not open project \"%s\": %s", projectPath, response.getErrorMessage()));
    }
  }

  public void editFile(String file, String searchRegex, String replacement){
    editFile(null, file, searchRegex, replacement);
  }

  public void editFile(@Nullable String projectName, String file, String searchRegex, String replacement) {
    ASDriver.EditFileRequest.Builder rqBuilder =
      ASDriver.EditFileRequest.newBuilder().setFile(file).setSearchRegex(searchRegex).setReplacement(replacement);
    if (projectName != null){
      rqBuilder.setProjectName(projectName);
    }
    ASDriver.EditFileResponse response = ide.editFile(rqBuilder.build());
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

  protected static String formatErrorMessage(String errorMessage) {
    if (StringUtil.isEmpty(errorMessage)) {
      return "Check the stderr log for the cause. See go/e2e-find-log-files for more info.";
    }
    return "Error message: " + errorMessage;
  }

  private static class MemoryLeakException extends RuntimeException {
    private MemoryLeakException(String message) {
      super(message);
    }
  }
}
