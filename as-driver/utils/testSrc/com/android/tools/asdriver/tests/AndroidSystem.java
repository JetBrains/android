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
import com.android.tools.asdriver.tests.AndroidStudioInstallation.AndroidStudioFlavor;
import com.android.tools.perflogger.Benchmark;
import com.android.utils.PathUtils;
import com.google.common.base.Preconditions;
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
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Represents a system running android development tools.
 * It has a display, environment variables, a file system etc.
 */
public class AndroidSystem implements AutoCloseable, TestRule {
  /**
   * By default, we set the emulator to a system image that most integration tests should be
   * using. This version corresponds to {@code INTEGRATION_TEST_SYSTEM_IMAGE} in Bazel.
   */
  private static final Emulator.SystemImage DEFAULT_EMULATOR_SYSTEM_IMAGE = Emulator.SystemImage.API_31;

  private final TestFileSystem fileSystem;
  private final HashMap<String, String> env;
  private final Display display;
  private final AndroidSdk sdk;
  private AndroidStudioInstallation install;
  // Currently running emulators
  private final List<Emulator> emulators;
  private int nextPort = 8554;

  @Nullable
  private static Throwable initializedAt = null;

  private AndroidSystem(TestFileSystem fileSystem, Display display, AndroidSdk sdk) {
    this.fileSystem = fileSystem;
    this.display = display;
    this.sdk = sdk;
    this.env = new HashMap<>();
    this.install = null;
    this.emulators = new ArrayList<>();
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
        initializedAt = new Throwable("AndroidSystem was previously initialized here.");
        try {
          base.evaluate();
          if (install != null) {
            install.verify();
          }
        } finally {
          AndroidSystem.this.close();
        }
      }
    };
  }

  public AndroidSdk getSdk() {
    return sdk;
  }

  public static AndroidSystem testDebugStandard(Display display, AndroidStudioFlavor androidStudioFlavor) {
    try {
      AndroidSystem system = basic(display, Files.createTempDirectory("root"));

      system.install = AndroidStudioInstallation.fromZip(system.fileSystem, androidStudioFlavor);
      system.install.createFirstRunXml();
      system.install.setNewUi();
      system.install.createGeneralPropertiesXml();

      return system;
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Creates a standard system with a default temp folder
   * that contains a preinstalled version of android studio
   * from the distribution zips. The SDK is set up pointing
   * to the standard prebuilts one.
   */
  public static AndroidSystem standard(AndroidStudioFlavor androidStudioFlavor) {
    try {
      AndroidSystem system = basic(Files.createTempDirectory("root"));

      system.install = AndroidStudioInstallation.fromZip(system.fileSystem, androidStudioFlavor);
      system.install.createFirstRunXml();
      system.install.setNewUi();
      system.install.createGeneralPropertiesXml();

      return system;
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static AndroidSystem standard() {
    return standard(AndroidStudioFlavor.FOR_EXTERNAL_USERS);
  }

  /**
   * Creates a system that contains only the sdk installed
   * and with a default temp folder.
   */
  public static AndroidSystem basic() {
    try {
      return basic(Files.createTempDirectory("root"));
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Creates a system that contains only the sdk installed.
   */
  public static AndroidSystem basic(Path root) throws IOException {
    return basic(Display.createDefault(), root);
  }

  public static AndroidSystem basic(Display display, Path root) throws IOException {
    TestFileSystem fileSystem = new TestFileSystem(root);
    AndroidSdk sdk = new AndroidSdk(TestUtils.resolveWorkspacePath(TestUtils.getRelativeSdk()));

    AndroidSystem system = new AndroidSystem(fileSystem, display, sdk);

    sdk.install(system.env);

    createRemediationShutdownHook();

    return system;
  }

  public static void createRemediationShutdownHook() {
    // When running from Bazel on Windows, the JVM isn't terminated in such a way that the shutdown
    // hook is triggered, so we have to emit the remediation steps ahead of time (without knowing
    // if they'll even be needed).
    if (SystemInfo.isWindows && TestUtils.runningFromBazel()) {
      System.out.println("Running on Bazel on Windows, so the shutdown hook may not be properly triggered. If this test fails, please " +
                         "check go/e2e-find-log-files for more information on how to diagnose test issues.");
    }
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("The test was terminated early (e.g. it was manually ended or Bazel may have timed out). Please see " +
                         "go/e2e-find-log-files for more information on how to diagnose test issues.");
    }));
  }

  /**
   * Assumes there is only one AndroidStudio installed in the system.
   * If there are multiple it will throw an exception.
   */
  public AndroidStudioInstallation getInstallation() {
    Preconditions.checkState(install != null, "Android studio has not been installed on this system.");
    return install;
  }

  /**
   * Runs Android Studio without a project (e.g. for a scenario where you want to create that project).
   */
  public AndroidStudio runStudioWithoutProject() throws IOException, InterruptedException {
    AndroidStudioInstallation install = getInstallation();
    return install.run(display, env);
  }

  public AndroidStudio runStudio(AndroidProject project) throws IOException, InterruptedException {
    AndroidStudioInstallation install = getInstallation();
    return install.run(display, env, project, sdk.getSourceDir());
  }

  public void runStudio(@NotNull final AndroidProject project,
                        Consumer<AndroidStudio> callback) throws Exception {
    try (AndroidStudio studio = runStudio(project)) {
      callback.accept(studio);
    }
  }

  public void runStudio(@NotNull final AndroidProject project,
                        @Nullable final String memoryDashboardName,
                        Consumer<AndroidStudio> callback) throws Exception {
    try (AndroidStudio studio = runStudio(project)) {
      callback.accept(studio);
      MemoryUsageReportProcessor.Companion.collectMemoryUsageStatistics(studio, install, memoryDashboardName);
    }
  }

  public void runStudio(@NotNull final AndroidProject project,
                        @NotNull Benchmark benchmark,
                        Consumer<AndroidStudio> callback) throws Exception{
    try (AndroidStudio studio = runStudio(project)) {
      callback.accept(studio);
      studio.addBenchmark(benchmark);
    }
  }

  public AndroidStudio runStudioFromApk(AndroidProject project) throws IOException, InterruptedException {
    AndroidStudioInstallation install = getInstallation();
    return install.run(display, env, project, sdk.getSourceDir());
  }

  public void runStudioFromApk(AndroidProject project, Consumer<AndroidStudio> callback) throws Exception {
    try (AndroidStudio studio = runStudioFromApk(project)) {
      callback.accept(studio);
    }
  }

  public void installRepo(MavenRepo repo) throws Exception {
    AndroidStudioInstallation install = getInstallation();
    repo.install(fileSystem.getRoot(), install, env);
  }

  /** Runs and returns an emulator using the default {@link Emulator.SystemImage}. */
  public Emulator runEmulator() throws IOException, InterruptedException {
    return runEmulator(DEFAULT_EMULATOR_SYSTEM_IMAGE);
  }

  /**
   * Runs an emulator using the default {@link Emulator.SystemImage}, providing it to the {@code callback} and then calling
   * {@link Emulator#close()}.
   */
  public void runEmulator(Consumer<Emulator> callback) throws IOException, InterruptedException {
    runEmulator(DEFAULT_EMULATOR_SYSTEM_IMAGE, callback);
  }

  /** Wraps {@code runEmulator} such that the default image is used with specifiable flags. */
  public void runEmulator(List<String> extraEmulatorFlags, Consumer<Emulator> callback) throws IOException, InterruptedException {
    runEmulator(DEFAULT_EMULATOR_SYSTEM_IMAGE, extraEmulatorFlags, callback);
  }

  /** Wraps {@code runEmulator} such that the default image is used with no extra emulator flags. */
  public void runEmulator(Emulator.SystemImage systemImage, Consumer<Emulator> callback) throws IOException, InterruptedException {
    runEmulator(systemImage, new ArrayList<>(), callback);
  }

  /**
   * Runs an emulator using the given {@link Emulator.SystemImage} and flags, providing it to the {@code callback} and then calling
   * {@link Emulator#close()}.
   */
  public void runEmulator(Emulator.SystemImage systemImage, List<String> extraEmulatorFlags, Consumer<Emulator> callback)
    throws IOException, InterruptedException {
    try (Emulator emulator = runEmulator(systemImage, extraEmulatorFlags)) {
      callback.accept(emulator);
    }
  }

  /** Wraps {@code runEmulator} such that no extra emulator flags are used. */
  public Emulator runEmulator(Emulator.SystemImage systemImage) throws IOException, InterruptedException {
    return runEmulator(systemImage, new ArrayList<>());
  }

  /** Runs and returns an emulator using the given {@link Emulator.SystemImage}. */
  public Emulator runEmulator(Emulator.SystemImage systemImage, List<String> extraEmulatorFlags) throws IOException, InterruptedException {
    String curEmulatorName = String.format("emu%d", emulators.size());
    Path workspaceRoot = TestUtils.getWorkspaceRoot(systemImage.path);
    Emulator.createEmulator(fileSystem, curEmulatorName, workspaceRoot);
    // Increase grpc port by one after spawning an emulator to avoid conflict
    Emulator emulator = Emulator.start(fileSystem, sdk, display, curEmulatorName, nextPort++, extraEmulatorFlags);
    emulators.add(emulator);
    return emulator;
  }

  public Adb runAdb() throws IOException {
    return Adb.start(sdk, getAdbEnv());
  }

  public Adb runAdb(boolean startServer, String... args) throws IOException {
    return Adb.start(sdk, getAdbEnv(), startServer, args);
  }

  private Map<String, String> getAdbEnv() throws IOException {
    Map<String, String> env = new HashMap<>();
    env.put("HOME", fileSystem.getHome().toString());
    env.put("TMPDIR", Files.createTempDirectory(TestUtils.getTestOutputDir(), "adb_server_session_output").toString());
    env.put("ADB_TRACE", "1");
    return env;
  }

  public void runAdb(Consumer<Adb> callback) throws IOException {
    try (Adb adb = runAdb()) {
      callback.accept(adb);
    }
  }

  public void setEnv(String name, String value) {
    env.put(name, value);
  }

  @Override
  public void close() throws Exception {
    display.close();
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
      System.out.printf("*** Files being written while shutting down system: ***%n");
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
}
