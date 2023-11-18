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

import com.android.tools.asdriver.tests.Adb;
import com.android.tools.asdriver.tests.AndroidProject;
import com.android.tools.asdriver.tests.AndroidStudio;
import com.android.tools.asdriver.tests.AndroidSystem;
import com.android.tools.asdriver.tests.Emulator;
import java.util.concurrent.TimeUnit;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class EmulatorBeforeStudioTest {
  @Rule
  public AndroidSystem system = AndroidSystem.standard();

  @Test
  public void recognizeEmulatorTest() throws Exception {
    AndroidProject project = new AndroidProject("tools/adt/idea/android/integration/testData/minapp");
    project.setDistribution("tools/external/gradle/gradle-7.2-bin.zip");
    try (Adb adb = system.runAdb();
         Emulator emulator = system.runEmulator()) {
      emulator.waitForBoot();
      adb.waitForDevice(emulator);

      try (AndroidStudio studio = system.runStudio(project)) {
        system.getInstallation().getIdeaLog()
          .waitForMatchingLine(String.format(".*Device \\[emulator-%s\\] has come online", emulator.getPortString()), 180, TimeUnit.SECONDS);
      }
    }
  }
}
