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
package com.android.tools.idea

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class BuildAndRunTest {
  @JvmField @Rule
  val system = AndroidSystem.standard()

  /**
   * Verifies that a project can build and deploy on an emulator
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 579892c4-e1b6-48f7-a5a2-69a12c12ce83
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import minapp in the testData directory of this module
   *   2. Add a few layout elements to the default activity
   *   3. Click Run
   *   4. From the device chooser dialog, select the running emulator and click Ok
   *   Verify:
   *   Project builds successfully and runs on the emulator
   *   </pre>
   */
  @Test
  fun deploymentTest() {
    val project = AndroidProject("tools/adt/idea/android/integration/testData/minapp")
    project.setDistribution("tools/external/gradle/gradle-7.2-bin.zip")
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/buildproject_deps.manifest"))

    system.runAdb { adb ->
      system.runEmulator { emulator ->
        system.runStudio(project) { studio ->
          studio.waitForSync()
          studio.waitForIndex()
          studio.executeAction("MakeGradleProject")
          studio.waitForBuild()

          studio.executeAction("Run")
          system.installation.ideaLog.waitForMatchingLine(
            ".*AndroidProcessHandler - Adding device emulator-${emulator.portString} to monitor for launched app: com\\.example\\.minapp",
            60, TimeUnit.SECONDS)
          adb.runCommand("logcat") { logcat ->
            logcat.waitForLog(".*Hello Minimal World!.*", 30, TimeUnit.SECONDS);
          }
        }
      }
    }
  }
}