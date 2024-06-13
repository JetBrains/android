
/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.asdriver.tests.Emulator
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
class ApplyCodeChangesTest {
  @JvmField
  @Rule
  val system: AndroidSystem = AndroidSystem.standard()

  @JvmField
  @Rule
  var watcher = MemoryDashboardNameProviderWatcher()

  /**
   * ETE Test for Apply Code Changes.
   *
   * The goal is to run project, modify a BroadcastReceiver and verify the change by sending an
   * intent from the ADB shell.
   *
   * Since Apply Code Changes do not rely on Activity restart, we need a robust way to interact
   * with the running application in a lockstep manner for verification so we base our test on a
   * small isolated BroadcastReceiver in the shared project.
   */
  @Test
  fun applyChangesTest() {
    val project = AndroidProject("tools/adt/idea/android/integration/testData/applychanges")
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/buildproject_deps.manifest"))

    system.runAdb { adb ->
      system.runEmulator(Emulator.SystemImage.API_33) { emulator ->
        println("Waiting for boot")
        emulator.waitForBoot()

        println("Waiting for device")
        adb.waitForDevice(emulator)

        system.runStudio(project, watcher.dashboardName) { studio ->
          studio.waitForSync()
          studio.waitForIndex()

          println("Waiting for project init");
          studio.waitForProjectInit()

          // Open the file ahead of time so that Live Edit is ready when we want to make a change
          val path = project.targetProject.resolve("src/main/java/com/example/applychanges/MyBroadcastReceiver.kt")
          studio.openFile("ApplyChangesTest", path.toString())

          studio.executeAction("MakeGradleProject")
          studio.waitForBuild()
          studio.executeAction("Run")

          studio.waitForEmulatorStart(system.installation.ideaLog, emulator, "com\\.example\\.applychanges", 60, TimeUnit.SECONDS)

          println("Waiting for application startup")
          adb.runCommand("logcat") {
            waitForLog(".*OnResume Before.*", 600, TimeUnit.SECONDS);
          }

          adb.runCommand("shell", "am", "broadcast", "-a", "com.example.applychanges.MyBroadcastReceiver.intent.TEST",
                         "-n", "com.example.applychanges/.MyBroadcastReceiver")

          adb.runCommand("logcat") {
            waitForLog(".*onReceive Before.*", 600, TimeUnit.SECONDS);
          }

          val newContents = "printAfter()\n"
          studio.editFile(path.toString(), "(?s)// EASILY SEARCHABLE ONRECEIVE LINE.*?// END ONRECEIVE SEARCH", newContents)
          studio.executeAction("android.deploy.CodeSwap")

          adb.runCommand("logcat") {
            waitForLog(".*Using Structure Redefinition Extension.*", 600, TimeUnit.SECONDS);
          }

          adb.runCommand("shell", "am", "broadcast", "-a", "com.example.applychanges.MyBroadcastReceiver.intent.TEST",
                         "-n", "com.example.applychanges/.MyBroadcastReceiver")

          adb.runCommand("logcat") {
            waitForLog(".*onReceive After.*", 600, TimeUnit.SECONDS);
          }
        }
      }
    }
  }
}
