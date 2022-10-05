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
import com.android.tools.asdriver.tests.MavenRepo;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;

public class DebuggerTest {
  @Rule
  public AndroidSystem system = AndroidSystem.standard();

  @Test
  public void runDebuggerTest() throws Exception {
    AndroidProject project = new AndroidProject("tools/adt/idea/android/integration/testData/minapp");
    project.setDistribution("tools/external/gradle/gradle-7.2-bin.zip");

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(new MavenRepo("tools/adt/idea/android/integration/buildproject_deps.manifest"));

    try (Adb adb = system.runAdb();
         Emulator emulator = system.runEmulator();
         AndroidStudio studio = system.runStudio(project)) {
      studio.waitForSync();
      studio.waitForIndex();
      studio.executeAction("MakeGradleProject");
      studio.waitForBuild();
      emulator.waitForBoot();
      adb.waitForDevice(emulator);

      System.out.println("Opening a file");
      Path path = project.getTargetProject().resolve("src/main/java/com/example/minapp/MainActivity.kt");
      String projectName = project.getTargetProject().getFileName().toString();
      studio.openFile(projectName, path.toString(), 9, 0);

      System.out.println("Setting a breakpoint");
      studio.executeAction("ToggleLineBreakpoint");

      System.out.println("Debugging the application");
      studio.executeAction("android.deploy.DebugWithoutBuild");

      System.out.println("Checking for the debugger UI");

      // Check for XDebuggerFramesList and XDebuggerTree; their presence represents that the
      // debugger hit a breakpoint.
      studio.waitForComponentByClass("XDebuggerFramesList");
      studio.waitForComponentByClass("XDebuggerTree");
    }
  }
}