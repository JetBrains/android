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

import com.android.SdkConstants;
import com.android.testutils.TestUtils;
import com.google.common.collect.Lists;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Adb implements AutoCloseable {
  private final AndroidSdk sdk;
  private final Path home;
  private final Process process;
  private final Path stdout;
  private final Path stderr;

  private Adb(Process process, AndroidSdk sdk, Path home, Path stdout, Path stderr) {
    this.process = process;
    this.sdk = sdk;
    this.home = home;
    this.stdout = stdout;
    this.stderr = stderr;
  }

  private Adb(AndroidSdk sdk, Path home) {
    this.process = null;
    this.stdout = null;
    this.stderr = null;
    this.sdk = sdk;
    this.home = home;
  }

  @Override
  public void close() throws IOException {
    if (process == null) {
      runCommand("kill-server");
    }
    else {
      if (process.isAlive()) {
        process.destroy();
      }
    }
  }

  /**
   * Default start for most use cases.
   */
  public static Adb start(AndroidSdk sdk, Path home) throws IOException {
    return start(sdk, home, true, "nodaemon");
  }

  public static Adb start(AndroidSdk sdk, Path home, boolean startServer, String... params) throws IOException {
    if (!startServer) {
      return new Adb(sdk, home);
    }

    List<String> command = new ArrayList<>();
    command.add("server");
    for (String param : params) {
      if (!param.isBlank()) {
        command.add(param);
      }
    }
    return exec(sdk, home, command.toArray(new String[]{}));
  }

  public void waitForLog(String expectedRegex, long timeout, TimeUnit unit) throws IOException, InterruptedException {
    LogFile logfile = new LogFile(stdout);
    logfile.waitForMatchingLine(expectedRegex, timeout, unit);
  }

  public void waitForDevice(Emulator emulator) throws IOException, InterruptedException {
    try (Adb child = runCommand("track-devices")) {
      // https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/SERVICES.TXT;l=23
      child.waitForLog(String.format("0015emulator-%s\tdevice", emulator.getPortString()), 10, TimeUnit.SECONDS);
    }
  }

  public Adb runCommand(String... command) throws IOException {
    return exec(sdk, home, command);
  }

  /**
   * Kotlin-friendly shorthand for an ADB command without params. If a parameterized version is desired (and only with very limited number
   * of options), more of these exact-numbered methods may be created. E.g. runCommand(cmd, param0, consumer),
   * runCommand(cmd, param0, param1, consumer), ....
   *
   * If many parameters are needed, please use the generic version {@link #runCommand(String...)}.
   */
  public void runCommand(String command, Consumer<Adb> consumer) throws IOException {
    try (Adb cmd = runCommand(command)) {
      consumer.accept(cmd);
    }
  }

  private static Adb exec(AndroidSdk sdk, Path home, String... params) throws IOException {
    Path logsDir = Files.createTempDirectory(TestUtils.getTestOutputDir(), "adb_logs");
    Path stdout = logsDir.resolve("stdout.txt");
    Path stderr = logsDir.resolve("stderr.txt");
    Files.createFile(stdout);
    Files.createFile(stderr);

    try (FileWriter outWriter = new FileWriter(stdout.toString(), true);
         FileWriter errWriter = new FileWriter(stderr.toString())) {
      String header = String.format("=== %s %s %d ===%n", stdout, String.join("-", params), System.currentTimeMillis());
      outWriter.write(header);
      errWriter.write(header);
    }

    List<String> command = new ArrayList<>();
    command.add(sdk.getSourceDir().resolve(SdkConstants.FD_PLATFORM_TOOLS).resolve(SdkConstants.FN_ADB).toString());
    Collections.addAll(command, params);
    System.out.printf("Adb invocation '%s' has stdout log at: %s%n", String.join(" ", command), stdout);

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectOutput(stdout.toFile());
    pb.redirectError(stderr.toFile());
    pb.environment().put("HOME", home.toString());
    return new Adb(pb.start(), sdk, home, stdout, stderr);
  }
}
