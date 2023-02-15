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
import kotlin.time.Duration.Companion.seconds
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
   * the emulator Find specific UI components for all inspectors Find specific logs for all
   * inspector deployments </pre>
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
          studio.executeAction("MakeGradleProject")
          studio.waitForBuild()

          /**
           * Android Studio deploys agents to the device after opening the App Inspection tool
           * window, which may terminate the running process in RBE environment. To avoid the issue
           * temporarily, we open the tool window before launching the app.
           *
           * TODO (b/255808916): prevent process from being terminated after opening the app
           * inspection tool window.
           */
          studio.showToolWindow(APP_INSPECTION_TOOL_WINDOW_TITLE)
          system.installation.ideaLog.waitForMatchingLine(
            ".*TransportProxy successfully created for device.*",
            60,
            TimeUnit.SECONDS
          )

          studio.executeAction("Run")
          system.installation.ideaLog.waitForMatchingLine(
            ".*AndroidProcessHandler - Adding device emulator-${emulator.portString} to monitor for launched app: com\\.example\\.minapp",
            60,
            TimeUnit.SECONDS
          )

          /**
           * The Run tool window pops up immediately after launching the application. To prevent the
           * Run tool window from covering the App Inspection tool window, we need to wait until the
           * Run tool window pops up before showing the App Inspection tool window again.
           */
          studio.waitForComponent("Run:")
          studio.showToolWindow(APP_INSPECTION_TOOL_WINDOW_TITLE)
          studio.waitForComponentByClass("WorkBenchLoadingPanel")
          // Component for Background Task Inspector.
          studio.waitForComponentByClass("BackgroundTaskEntriesView")
          // Component for Network Inspector.
          studio.waitForComponentByClass("RangeTooltipComponent")
          adb.runCommand("logcat") {
            // Log for Database Inspector: Accessing hidden method
            // Landroid/database/sqlite/SQLiteDatabase
            waitForLog(".*Accessing hidden method.*SQLiteDatabase.*", 60.seconds)
            // Log for Background Task Inspector: Transformed class:
            // android/os/PowerManager$WakeLock
            waitForLog(".*Transformed class.*WakeLock.*", 60.seconds)
            // Log for Network Inspector: Transformed class: java/net/URL
            waitForLog(".*Transformed class.*URL.*", 60.seconds)
          }
        }
      }
    }
  }
}
