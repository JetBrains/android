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

import static com.android.tools.asdriver.tests.MemoryUsageReportProcessorKt.COLLECT_AND_LOG_EXTENDED_MEMORY_REPORTS;
import static com.android.tools.asdriver.tests.MemoryUsageReportProcessorKt.DUMP_HPROF_SNAPSHOT;
import com.android.testutils.TestUtils;
import com.android.tools.asdriver.tests.base.IdeInstallation;
import com.android.tools.testlib.LogFile;
import com.android.tools.testlib.TestFileSystem;
import com.android.tools.testlib.TestLogger;
import com.intellij.openapi.util.SystemInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

public class SherlockInstallation extends IdeInstallation<Sherlock> {

  public static SherlockInstallation fromZip(TestFileSystem testFileSystem) throws IOException {
    Options options = new Options(testFileSystem);
    return fromZip(options);
  }

  public static SherlockInstallation fromZip(Options options) throws IOException {
    Path workDir = Files.createTempDirectory(options.testFileSystem.getRoot(), "sherlock");
    TestLogger.log("workDir: %s", workDir);

    String platform = "linux";
    if (SystemInfo.isMac) {
      if (SystemInfo.OS_ARCH.equals("aarch64")) {
        platform = "mac_arm";
      } else {
        platform = "mac";
      }
    } else if (SystemInfo.isWindows) {
      platform = "win";
    }

    String zipPath;
    zipPath = String.format("prebuilts/studio/intellij-sdk/sherlock-sdk.%s.zip", platform);
    Path sherlockZip = TestUtils.getBinPath(zipPath);
    unzip(sherlockZip, workDir);

    String sherlockDir = getSherlockDirectory(workDir);
    return new SherlockInstallation(options.testFileSystem, workDir, workDir.resolve(sherlockDir), options.disableFirstRun);
  }

  static public SherlockInstallation fromDir(TestFileSystem testFileSystem, Path sherlockDir) throws IOException {
    Path workDir = Files.createTempDirectory(testFileSystem.getRoot(), "sherlock");
    return new SherlockInstallation(testFileSystem, workDir, sherlockDir, true);
  }

  private SherlockInstallation(TestFileSystem testFileSystem,
                               Path workDir,
                               Path sherlockDir,
                               Boolean disableFirstRun) throws IOException {
    super("sherlock-sdk", testFileSystem, workDir, sherlockDir);

    if (disableFirstRun) {
      this.addVmOption("-Ddisable.android.first.run=true");
    }

    bundlePlugin(TestUtils.getBinPath("prebuilts/studio/intellij-sdk/sherlock_performanceTesting.zip"));
    bundlePlugin(TestUtils.getBinPath("tools/adt/idea/as-driver/asdriver.plugin-sherlock-sdk.zip"));
  }

  @Override
  protected void createVmOptions(StringBuilder vmOptions) throws IOException {
    super.createVmOptions(vmOptions);
    Path threadingCheckerAgentZip = TestUtils.getBinPath("tools/base/threading-agent/threading_agent.jar");
    if (!Files.exists(threadingCheckerAgentZip)) {
      // Threading agent can be built using 'bazel build //tools/base/threading-agent:threading_agent'
      throw new IllegalStateException("Threading checker agent not found at " + threadingCheckerAgentZip);
    }
    vmOptions.append(String.format("-javaagent:%s%n", threadingCheckerAgentZip));
    // Need to disable android first run checks, or we get stuck in a modal dialog complaining about lack of web access.
    vmOptions.append(String.format("-Dgradle.ide.save.log.to.file=true%n"));
    // Prevent our crash metrics from going to the production URL
    vmOptions.append(String.format("-Duse.staging.crash.url=true%n"));
    // Enabling this flag is required for connecting all the Java Instrumentation agents needed for memory statistics.
    vmOptions.append(String.format("-Dstudio.run.under.integration.test=true%n"));
    vmOptions.append(String.format("-Djdk.attach.allowAttachSelf=true%n"));
    // Always collect histograms
    vmOptions.append(String.format("-D%s=true%n", COLLECT_AND_LOG_EXTENDED_MEMORY_REPORTS));
    if (Boolean.getBoolean(DUMP_HPROF_SNAPSHOT)) {
      vmOptions.append(String.format("-D%s=true%n", DUMP_HPROF_SNAPSHOT));
    }
    // Specify the folder where completion variants for all the calls of the %doComplete performanceTesting command will be stored, same
    // file is used by the %assertCompletionCommand command to access the output of the last completion event.
    vmOptions.append(
      String.format("-Dcompletion.command.report.dir=%s%n", Files.createTempDirectory(TestUtils.getTestOutputDir(), "completion_report")));
    // Specify the file where the results of the findUsages action triggered by the %findUsages command will be stored. The same file is
    // used by the %assertFindUsagesEntryCommand to access the result of the last findUsages event.
    vmOptions.append(
      String.format("-Dfind.usages.command.found.usages.list.file=%s%n", TestUtils.getTestOutputDir().resolve("find.usages.list.txt")));
  }

  @Override
  protected String getExecutable() {
    String sherlockExecutable = "bin/sherlock.sh";
    if (SystemInfo.isMac) {
      sherlockExecutable = "Contents/MacOS/sherlock";
    } else if (SystemInfo.isWindows) {
      sherlockExecutable = "bin/sherlock64.exe";
    }
    return workDir.resolve(sherlockExecutable).toString();
  }

  @Override
  protected Sherlock createAndAttach() throws IOException, InterruptedException {
    Sherlock sherlock = attach();
    sherlock.startCapturingScreenshotsOnWindows();
    return sherlock;
  }

  @Override
  protected String vmOptionEnvName() {
    return "SHERLOCK_VM_OPTIONS";
  }

  public Sherlock attach() throws IOException, InterruptedException {
    int pid;
    try {
      pid = waitForDriverPid(getIdeaLog());
    } catch (InterruptedException e) {
      checkForJdwpError(this);
      throw e;
    }
    ProcessHandle process = ProcessHandle.of(pid).get();
    int port = waitForDriverServer(getIdeaLog());
    return new Sherlock(this, process, port);
  }

  /**
   * Checks to see if Android Studio failed to start because the JDWP address was already in use.
   * This method will throw an exception in the test process rather than the developer having to
   * check Android Studio's stderr itself. I.e. this method is purely for developer convenience
   * when testing locally.
   */
  static private void checkForJdwpError(SherlockInstallation installation) {
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

  public static class Options {
    public TestFileSystem testFileSystem;
    boolean disableFirstRun = true;
    public Options(TestFileSystem system) {
      testFileSystem = system;
    }
  }

  private static String getSherlockDirectory(Path workDir) {
    return "";
  }
}
