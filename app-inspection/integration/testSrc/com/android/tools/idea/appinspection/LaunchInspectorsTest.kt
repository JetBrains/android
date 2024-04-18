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
package com.android.tools.idea.appinspection

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test

private const val APP_INSPECTION_TOOL_WINDOW_TITLE = "App Inspection"

class LaunchInspectorsTest {
  @JvmField @Rule val system = AndroidSystem.standard()

  @JvmField @Rule var watcher = MemoryDashboardNameProviderWatcher()

  /**
   * Verifies that all inspectors are deployed successfully with a non-empty UI component after
   * opening the App Inspection tool window. <p> <pre> Test Steps:
   * 1. Import minapp in the testData directory of this module
   * 2. Add a few layout elements to the default activity
   * 3. Open the App Inspection tool window
   * 4. Wait until agent deployed
   * 5. Click Run
   * 6. From the device chooser dialog, select the running emulator and click Ok
   * 7. Open the App Inspection tool window again Verify: Project builds successfully and runs on
   *    the emulator Find specific UI components for all inspectors Find specific logs for all
   *    inspector deployments </pre>
   *
   * TODO(b/255808916): The test should be able to run as described above, with the App Inspection
   *   tool launched before the app. But for some reason, the app detaches if the inspectors are
   *   initialized right away. A workaround is to not deploy the inspectors until after the app has
   *   launched. When this bug is fixed, we should add another test so we have 2 versions. One with
   *   the inspectors tool already open when the app is launched, and another where the tool is
   *   started after the app is run.
   */
  @Test
  fun openAppInspectionToolWindow() {
    val project = AndroidProject("tools/adt/idea/app-inspection/integration/testData/minapp")
    system.installRepo(MavenRepo("tools/adt/idea/app-inspection/integration/minapp_deps.manifest"))

    system.runAdb { adb ->
      system.runEmulator { emulator ->
        system.runStudio(project, watcher.dashboardName) { studio ->
          studio.waitForSync()
          studio.waitForIndex()
          emulator.waitForBoot()
          adb.waitForDevice(emulator)

          studio.executeAction("MakeGradleProject")
          studio.waitForBuild()

          studio.executeAction("Run")
          studio.waitForEmulatorStart(
            system.installation.ideaLog,
            emulator,
            "com\\.example\\.minapp",
            60,
            TimeUnit.SECONDS,
          )

          // TODO(b/255808916): See TODO section in the test KDoc.
          emulator.logCat.waitForMatchingLine(".*Hello Minimal World!.*", 60, TimeUnit.SECONDS)
          Thread.sleep(2_000)

          studio.showToolWindow(APP_INSPECTION_TOOL_WINDOW_TITLE)
          studio.waitForComponentByClass("WorkBenchLoadingPanel")
          // Component for Background Task Inspector.
          studio.waitForComponentByClass("BackgroundTaskEntriesView")
          // Component for Network Inspector.
          studio.waitForComponentByClass("RangeTooltipComponent")

          emulator.logCat.waitForMatchingLine(
            ".*Inspector installed: androidx.sqlite.inspection",
            60,
            TimeUnit.SECONDS,
          )
          emulator.logCat
            .reset() // have to reset log position between checks because Inspector lines can appear
          // in any order
          emulator.logCat.waitForMatchingLine(
            ".*Inspector installed: backgroundtask.inspection",
            60,
            TimeUnit.SECONDS,
          )
          emulator.logCat.reset()
          emulator.logCat.waitForMatchingLine(
            ".*Inspector installed: studio.network.inspection",
            60,
            TimeUnit.SECONDS,
          )
        }
      }
    }
  }
}
