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
package com.android.tools.idea;

import com.android.testutils.TestUtils;
import com.android.tools.asdriver.tests.Adb;
import com.android.tools.asdriver.tests.AndroidSdk;
import com.android.tools.asdriver.tests.Display;
import com.android.tools.asdriver.tests.Emulator;
import com.android.tools.asdriver.tests.TestFileSystem;
import com.android.tools.asdriver.tests.XvfbServer;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AdbServerConnectionTest {
  private static final String EMULATOR_NAME = "emu";

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void adbServerTest() throws Exception {
    TestFileSystem fileSystem = new TestFileSystem(tempFolder.getRoot().toPath());
    AndroidSdk sdk = new AndroidSdk(TestUtils.resolveWorkspacePath("prebuilts/studio/sdk/linux"));

    HashMap<String, String> env = new HashMap<>();
    sdk.install(env);

    Emulator.createEmulator(fileSystem, EMULATOR_NAME, TestUtils.getWorkspaceRoot().resolve("../system_image_android-29_default_x86_64"));

    try (Display display = new XvfbServer();
         Adb adb = Adb.start(sdk, fileSystem.getHome(), false);
         Emulator emulator = Emulator.start(fileSystem, sdk, display, EMULATOR_NAME)) {
      emulator.waitForBoot();
      try (Adb devices = adb.runCommand("devices")) {
        devices.waitForLog("List of devices attached", 5, TimeUnit.SECONDS);
      }
    }
  }
}
