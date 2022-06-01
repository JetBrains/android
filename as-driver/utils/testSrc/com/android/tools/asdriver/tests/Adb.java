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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Adb {
  private static void exec(AndroidSdk sdk, Path home, Path stdout, Path stderr, String... params) throws IOException {
    try (FileWriter outWriter = new FileWriter(stdout.toString(), true);
         FileWriter errWriter = new FileWriter(stderr.toString())) {
      String header = String.format("=== %s %s %d ===%n", stdout, String.join("-", params), System.currentTimeMillis());
      outWriter.write(header);
      errWriter.write(header);
    }

    Path adbPath = sdk.getSourceDir().resolve(SdkConstants.FD_PLATFORM_TOOLS).resolve(SdkConstants.FN_ADB);

    List<String> command = new ArrayList<>();
    command.add(adbPath.toString());
    Collections.addAll(command, params);

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectOutput(stdout.toFile());
    pb.redirectError(stderr.toFile());
    pb.environment().put("HOME", home.toString());
    pb.start();
  }

  public static void waitFor(Emulator emulator) throws IOException, InterruptedException {
    Path logsDir = Files.createTempDirectory(TestUtils.getTestOutputDir(), "adb_logs");
    Path stdout = logsDir.resolve("stdout.txt");
    Path stderr = logsDir.resolve("stderr.txt");
    Files.createFile(stdout);
    Files.createFile(stderr);

    exec(emulator.getSdk(), emulator.getHome(), stdout, stderr, "track-devices");
    LogFile logfile = new LogFile(stdout);
    // https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/SERVICES.TXT;l=23
    logfile.waitForMatchingLine(String.format("0015emulator-%s\tdevice", emulator.getPortString()), 10, TimeUnit.SECONDS);
  }
}
