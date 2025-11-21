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
import com.android.tools.asdriver.tests.base.Ide;
import com.android.tools.idea.io.grpc.StatusRuntimeException;
import com.android.tools.perflogger.Benchmark;
import com.android.tools.testlib.TestLogger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

public class AndroidStudio extends Ide {

  private final AndroidStudioInstallation install;

  private final String PAST_DEADLINE = "Studio quitting thread is still alive at deadline in quit().";

  private Benchmark benchmark = null;

  public AndroidStudio(AndroidStudioInstallation install, ProcessHandle process, int port) {
    super(process, port);
    this.install = install;
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

  public void addBenchmark(Benchmark benchmark){
    this.benchmark = benchmark;
  }

  /**
   * @param force true to kill Studio right away, false to gracefully exit. Note that killing Studio will not allow it to run
   *              cleanup processes, such as stopping Gradle, which would let Gradle to continue writing to the filesystem.
   */
  public void quit(boolean force) {
    ASDriver.QuitRequest rq = ASDriver.QuitRequest.newBuilder().setForce(force).build();

    // gRPC may not return from the call when Studio shuts down. So we need to place a timer on the shutdown and check the state
    // of shutdown using alternative methods in case the gRPC call doesn't return by the deadline.
    Ref<Throwable> throwableRef = new Ref<>(null);
    Thread thread = new Thread(() -> {
      try {
        ASDriver.QuitResponse ignore = ide.quit(rq);
      }
      catch (StatusRuntimeException ignored) {
        // This is normally what gRPC will throw when the other end disconnects.
      }
      catch (Throwable t) {
        throwableRef.set(t);
      }
    }, "gRPC Studio Shutdown");
    thread.start();

    try {
      thread.join(TimeUnit.SECONDS.toMillis(30));
      if (thread.isAlive()) {
        throw new RuntimeException(PAST_DEADLINE);
      }
      Throwable t = throwableRef.get();
      if (t != null) {
        TestLogger.log("Studio quitting has thrown an error: %s", t.getMessage());
        throw new RuntimeException(t);
      }
    }
    catch (InterruptedException e) {
      thread.interrupt();
      Thread.currentThread().interrupt();
      TestLogger.log("Quitting Studio was interrupted.");
      throw new RuntimeException("Quitting Studio was interrupted.", e);
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
    TestLogger.log("Quitting Studio...");
    waitToWorkAroundWindowsIssue();
    try {
      quit(false);
      try {
        install.getIdeaLog().waitForMatchingLine(".*PersistentFSImpl - VFS dispose completed.*", 30, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        // Sometimes, it just doesn't print the shutdown.
        TestLogger.log("VFS dispose did not occur/complete during shutdown.");
      }
    }
    catch (Throwable t) {
      if (t.getMessage() == PAST_DEADLINE) {
        install.getStdout().waitForMatchingLine(".*Exiting Studio.", 10, TimeUnit.SECONDS);
        return;
      }
      throw t;
    }
  }

  public void executeAction(String action) {
    executeAction(action, DataContextSource.DEFAULT, false);
  }

  public void executeAction(String action, DataContextSource dataContextSource) {
    executeAction(action, dataContextSource, false);
  }

  public void executeActionWhenSmart(String action) {
    executeAction(action, DataContextSource.DEFAULT, true);
  }

  public void executeAction(String action, DataContextSource dataContextSource, Boolean whenSmart) {
    ASDriver.ExecuteActionRequest rq =
      ASDriver.ExecuteActionRequest.newBuilder().setActionId(action).setDataContextSource(dataContextSource.dataContextSource)
        .setRunWhenSmart(whenSmart).build();
    ASDriver.ExecuteActionResponse response;
    response = ide.executeAction(rq);

    switch (response.getResult()) {
      case OK -> {
      }
      case ACTION_NOT_FOUND -> throw new NoSuchElementException(String.format("No action found by this ID: %s", action));
      case ERROR -> throw new IllegalStateException(String.format("Failed to execute action: %s. %s",
                                                                  action, formatErrorMessage(response.getErrorMessage())));
      default -> throw new IllegalStateException(String.format("Unhandled response: %s", response.getResult()));
    }
  }

  public void waitForIndex() throws IOException, InterruptedException {
    benchmarkLog("calling_waitForIndex");
    TestLogger.log("Waiting for indexing to complete");
    ASDriver.WaitForIndexRequest rq = ASDriver.WaitForIndexRequest.newBuilder().build();
    ASDriver.WaitForIndexResponse ignore = ide.waitForIndex(rq);
    install.getIdeaLog().reset(); //Log position can be moved past if used after waitForBuild
    install.getIdeaLog().waitForMatchingLine(".*Unindexed files update took.*", 300, TimeUnit.SECONDS);
    benchmarkLog("after_waitForIndex");
  }

  public void waitForNativeBreakpointHit() throws IOException, InterruptedException {
    System.out.println("Waiting for native breakpoint to hit");
    install.getIdeaLog().waitForMatchingLine(".*Native breakpoint hit.*", 3, TimeUnit.MINUTES);
  }

  public void waitForGlobalInspections() throws IOException, InterruptedException {
    waitForGlobalInspections(1, TimeUnit.HOURS);
  }

  public void waitForGlobalInspections(long timeout, TimeUnit unit) throws IOException, InterruptedException {
    TestLogger.log("Waiting up to %d %s for global inspections run", timeout, unit);
    Matcher matcher = install.getIdeaLog()
      .waitForMatchingLine(".*GlobalInspectionContextImpl - Code inspection finished. Took (.*) ms", timeout, unit);
    TestLogger.log("Global inspections took %sms", matcher.group(1));
  }

  public void waitForBuild() throws IOException, InterruptedException {
    // "Infinite" timeout
    waitForBuild(1, TimeUnit.DAYS);
  }

  public void waitForBuild(long timeout, TimeUnit unit) throws IOException, InterruptedException {
    TestLogger.log("Waiting up to %d %s for Gradle build", timeout, unit);
    Matcher matcher = install.getIdeaLog()
      .waitForMatchingLine(".*Gradle build finished in (.*)",
                           "(.*org\\.gradle\\.tooling\\.\\w+Exception.*)|" +
                           "(.*Gradle build failed in (.*))", timeout, unit);
    TestLogger.log("Build took %s", matcher.group(1));
  }

  public void waitForSync() throws IOException, InterruptedException {
    benchmarkLog("calling_waitForSync");
    // "Infinite" timeout
    waitForSync(1, TimeUnit.DAYS);
    benchmarkLog("after_waitForSync");
  }

  public void waitForSync(long timeout, TimeUnit unit) throws IOException, InterruptedException {
    TestLogger.log("Waiting up to %d %s for Gradle sync", timeout, unit);
    Matcher matcher = install.getIdeaLog()
      .waitForMatchingLine(".*Gradle sync finished in (.*)", ".*org\\.gradle\\.tooling\\.\\w+Exception.*", timeout, unit);
    TestLogger.log("Sync took %s", matcher.group(1));
  }

  private void benchmarkLog(String name){
    if (benchmark != null){
      benchmark.log(name, System.currentTimeMillis());
    }
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

    if (variants.length == 0) {
      return;
    }
    builder = new StringBuilder().append("%assertCompletionCommand CONTAINS");
    for (String variant : variants) {
      builder.append(" ").append(variant);
    }
    executeCommand(builder.toString(), null);
    pressKey(Keys.ESCAPE, null);
  }

  public enum Keys { ENTER, ESCAPE, BACKSPACE }

  public void pressKey(Keys key, @Nullable String project) {
    executeCommand("%pressKey " + key.name(), project);
  }

  /**
   * Describes from which component an action should obtain its {@code DataContext}. By default, a minimal {@code DataContext} is
   * constructed with only the {@code Project} and the {@code Editor} specified.
   */
  public enum DataContextSource {
    DEFAULT(ASDriver.ExecuteActionRequest.DataContextSource.DEFAULT),
    SELECTED_TEXT_EDITOR(ASDriver.ExecuteActionRequest.DataContextSource.SELECTED_TEXT_EDITOR),
    ACTIVE_TOOL_WINDOW(ASDriver.ExecuteActionRequest.DataContextSource.ACTIVE_TOOL_WINDOW);

    // This is the underlying proto enum that we do not want to expose as part of the API.
    private final ASDriver.ExecuteActionRequest.DataContextSource dataContextSource;

    DataContextSource(ASDriver.ExecuteActionRequest.DataContextSource dataContextSource) {
      this.dataContextSource = dataContextSource;
    }
  }
}