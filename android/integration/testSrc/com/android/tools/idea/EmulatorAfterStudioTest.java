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

import com.android.tools.asdriver.tests.AndroidProject;
import com.android.tools.asdriver.tests.AndroidStudio;
import com.android.tools.asdriver.tests.AndroidSystem;
import com.android.tools.testlib.Adb;
import com.android.tools.testlib.Emulator;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;

public class EmulatorAfterStudioTest {
  @Rule
  public AndroidSystem system = AndroidSystem.standard();

  @Test
  public void recognizeNewEmulatorTest() throws Exception {
    AndroidProject project = new AndroidProject("tools/adt/idea/android/integration/testData/minapp");

    try (Adb adb = system.runAdb();
         AndroidStudio studio = system.runStudio(project);
         Emulator emulator = system.runEmulator()) {
      emulator.waitForBoot();
      adb.waitForDevice(emulator);
      system.getInstallation().getIdeaLog()
        .waitForMatchingLine(String.format(".*Device \\[emulator-%s\\] has come online", emulator.getPortString()), 300, TimeUnit.SECONDS);
    }
  }
}