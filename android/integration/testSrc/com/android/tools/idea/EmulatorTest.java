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
import com.android.tools.asdriver.tests.AndroidSystem;
import com.android.tools.asdriver.tests.Workspace;
import com.android.tools.testlib.Adb;
import com.android.tools.testlib.AndroidSdk;
import com.android.tools.testlib.Display;
import com.android.tools.testlib.Emulator;
import com.android.tools.testlib.TestFileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EmulatorTest {
  @Rule
  public AndroidSystem system = AndroidSystem.basic();

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void runEmulatorTest() throws Exception {
    TestFileSystem fileSystem = new TestFileSystem(tempFolder.getRoot().toPath());
    AndroidSdk sdk = new AndroidSdk(TestUtils.resolveWorkspacePath(TestUtils.getRelativeSdk()));

    Path systemImageDir = Workspace.getRoot(Emulator.DEFAULT_EMULATOR_SYSTEM_IMAGE.path);
    Emulator.createEmulator(fileSystem, "emu", systemImageDir);

    try (Display display = Display.createDefault();
         Adb adb = Adb.start(sdk, fileSystem);
         Emulator emulator = Emulator.start(fileSystem, sdk, display, "emu", 8554, new ArrayList<>())) {
      emulator.waitForBoot();
      adb.waitForDevice(emulator);
    }
  }
}
