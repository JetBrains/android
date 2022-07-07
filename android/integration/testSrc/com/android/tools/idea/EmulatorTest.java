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
import java.util.HashMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EmulatorTest {
  private static final String EMULATOR_NAME = "emu";

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void runEmulatorTest() throws Exception {
    TestFileSystem fileSystem = new TestFileSystem(tempFolder.getRoot().toPath());
    HashMap<String, String> env = new HashMap<>();
    AndroidSdk sdk = new AndroidSdk(TestUtils.resolveWorkspacePath(TestUtils.getRelativeSdk()));
    sdk.install(env);

    Emulator.createEmulator(fileSystem, EMULATOR_NAME, TestUtils.getWorkspaceRoot().resolve("../system_image_android-29_default_x86_64"));

    try (Display display = Display.createDefault();
         Adb adb = Adb.start(sdk, fileSystem.getHome());
         Emulator emulator = Emulator.start(fileSystem, sdk, display, EMULATOR_NAME)) {
      emulator.waitForBoot();
      adb.waitForDevice(emulator);
    }
  }
}
