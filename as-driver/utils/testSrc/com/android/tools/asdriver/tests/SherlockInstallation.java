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

import com.android.testutils.TestUtils;
import com.android.tools.asdriver.tests.base.IdeInstallation;
import com.android.tools.testlib.AndroidSdk;
import com.android.tools.testlib.Display;
import com.android.tools.testlib.Emulator;
import com.android.tools.testlib.TestFileSystem;
import com.android.tools.testlib.TestLogger;
import com.android.utils.PathUtils;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class SherlockInstallation extends IdeInstallation<Sherlock> implements TestRule {

  private final HashMap<String, String> env;
  private final Display display;
  private final AndroidSdk sdk;
  // Currently running emulators
  private final List<Emulator> emulators;
  private int nextPort = 8554;

  @Nullable
  private static Throwable initializedAt = null;

  public static SherlockInstallation fromZip(TestFileSystem testFileSystem) throws IOException {
    return fromZip(testFileSystem, null, null, true);
  }

  public static SherlockInstallation fromZip(TestFileSystem testFileSystem,  Display display, AndroidSdk sdk, boolean disableFirstRun) throws IOException {
    Path workDir = Files.createTempDirectory(testFileSystem.getRoot(), "sherlock");
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

    String zipPath = String.format("prebuilts/studio/intellij-sdk/sherlock-sdk.%s.zip", platform);
    Path sherlockZip = TestUtils.getBinPath(zipPath);
    unzip(sherlockZip, workDir);

    String sherlockDir = getSherlockDirectory(workDir);
    return new SherlockInstallation(testFileSystem, workDir, workDir.resolve(sherlockDir), disableFirstRun, display, sdk);
  }

  static public SherlockInstallation fromDir(TestFileSystem testFileSystem, Path sherlockDir) throws IOException {
    Path workDir = Files.createTempDirectory(testFileSystem.getRoot(), "sherlock");
    return new SherlockInstallation(testFileSystem, workDir, sherlockDir, true, null, null);
  }

  private SherlockInstallation(TestFileSystem testFileSystem,
                               Path workDir,
                               Path sherlockDir,
                               Boolean disableFirstRun,
                               Display display,
                               AndroidSdk sdk) throws IOException {
    super("sherlock-sdk", testFileSystem, workDir, sherlockDir);

    this.display = display;
    this.sdk = sdk;
    this.env = new HashMap<>();
    this.emulators = new ArrayList<>();

    if (disableFirstRun) {
      this.addVmOption("-Ddisable.android.first.run=true");
    }

    bundlePlugin(TestUtils.getBinPath("prebuilts/studio/intellij-sdk/sherlock_performanceTesting.zip"));
    bundlePlugin(TestUtils.getBinPath("tools/adt/idea/as-driver/asdriver.plugin-sherlock-sdk.zip"));
  }

  @Override
  protected String getExecutable() {
    String sherlockExecutable = "bin/sherlock.sh";
    if (SystemInfo.isMac) {
      sherlockExecutable = "Contents/MacOS/sherlock";
    }
    else if (SystemInfo.isWindows) {
      sherlockExecutable = "bin/sherlock64.exe";
    }
    return workDir.resolve(sherlockExecutable).toString();
  }

  @Override
  protected String vmOptionEnvName() {
    return "SHERLOCK_VM_OPTIONS";
  }

  @Override
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

  public static SherlockInstallation standard() {
    try {
      TestFileSystem fileSystem = new TestFileSystem(Files.createTempDirectory("root"));

      AndroidSdk sdk = new AndroidSdk(TestUtils.resolveWorkspacePath(TestUtils.getRelativeSdk()));

      SherlockInstallation install = SherlockInstallation.fromZip(fileSystem, Display.createDefault(), sdk, true);
      install.createFirstRunXml();
      install.setNewUi();
      install.createGeneralPropertiesXml();

      sdk.install(install.env);

      createRemediationShutdownHook();

      return install;
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void setEnv(String name, String value) {
    env.put(name, value);
  }

  public AndroidSdk getSdk() {
    return sdk;
  }

  public static void createRemediationShutdownHook() {
    // When running from Bazel on Windows, the JVM isn't terminated in such a way that the shutdown
    // hook is triggered, so we have to emit the remediation steps ahead of time (without knowing
    // if they'll even be needed).
    if (SystemInfo.isWindows && TestUtils.runningFromBazel()) {
      TestLogger.log("Running on Bazel on Windows, so the shutdown hook may not be properly triggered. If this test fails, please " +
                     "check go/e2e-find-log-files for more information on how to diagnose test issues.");
    }
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      TestLogger.log("The test was terminated early (e.g. it was manually ended or Bazel may have timed out). Please see " +
                     "go/e2e-find-log-files for more information on how to diagnose test issues.");
    }));
  }

  /** Runs and returns an emulator using the given {@link Emulator.SystemImage}. */
  public Emulator runEmulator(Emulator.SystemImage systemImage, List<String> extraEmulatorFlags) throws IOException, InterruptedException {
    TestLogger.log("Emulator#runEmulator");
    String curEmulatorName = String.format("emu%d", emulators.size());
    Path systemImageDir = Workspace.getRoot(systemImage.path);
    Emulator.createEmulator(fileSystem, curEmulatorName, systemImageDir);
    // Increase grpc port by one after spawning an emulator to avoid conflict
    Emulator emulator = Emulator.start(fileSystem, sdk, display, curEmulatorName, nextPort++, extraEmulatorFlags);
    emulators.add(emulator);
    return emulator;
  }

  /**
   * Runs Sherlock without a project (e.g. for a scenario where you want to create that project).
   */
  public Sherlock runSherlockWithoutProject() throws IOException, InterruptedException {
    return run(display, env);
  }

  public Sherlock runSherlock(AndroidProject project) throws IOException, InterruptedException {
    return run(display, env, project, sdk.getSourceDir());
  }

  @Override
  public void close() throws Exception {
    if(display != null) {
      display.close();
    }
    try {
      try {
        PathUtils.deleteRecursivelyIfExists(fileSystem.getRoot());
      }
      catch (AccessDeniedException e) {
        // TODO(b/240166122): on Windows, there seems to be a race condition preventing deletions, so
        // we try again after waiting for a bit.
        if (SystemInfo.isWindows) {
          Thread.sleep(5000);
          PathUtils.deleteRecursivelyIfExists(fileSystem.getRoot());
        }
        else {
          throw e;
        }
      }
    }
    catch (RuntimeException | IOException e) {
      TestLogger.log("*** Files being written while shutting down system: ***");
      printContents(fileSystem.getRoot().toFile());
      throw e;
    }
  }

  private static void printContents(File root) throws IOException {
    if (root.isDirectory()) {
      for (File subPath : root.listFiles()) {
        printContents(subPath);
      }
    }
    System.out.printf("%s%n", root.getCanonicalPath());
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        if (initializedAt != null) {
          // This object can be used as a rule only once per execution to avoid multiple
          // integration tests on the same target. We only want a single test in a single
          // target so that integration tests are parallelized, since they tend to take
          // much longer time than unit tests.
          throw new IllegalStateException("There should only be one integration test per test execution.", initializedAt);
        }
        initializedAt = new Throwable("Sherlock was previously initialized here.");
        try {
          base.evaluate();
          verify();
        } finally {
          close();
        }
      }
    };
  }
}