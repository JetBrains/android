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
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import com.android.tools.asdriver.tests.integration.RemoteDeviceManager
import com.android.tools.testlib.Emulator
import com.intellij.openapi.util.SystemInfo
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test

class ApplyChangesTest {
  @JvmField @Rule val system: AndroidSystem = AndroidSystem.standard()

  @JvmField @Rule var watcher = MemoryDashboardNameProviderWatcher()

  /**
   * ETE Test for Apply Changes.
   *
   * The goal is to run project, modify the Activity's start up and apply the delta.
   *
   * We verify that the activity is properly restarted and the modified onResume is invoked.
   */
  @Test
  fun applyChangesTest() {
    val project = AndroidProject("tools/adt/idea/android/integration/testData/applychanges")

    system.installRepo(MavenRepo("tools/adt/idea/android/integration/buildproject_deps.manifest"))

    system.runAdb { adb ->
      var emulator: Emulator? = null
      var remoteDeviceManager: RemoteDeviceManager? = null

      if (!SystemInfo.isWindows) {
        emulator = system.runEmulator(Emulator.SystemImage.API_33)
      } else {
        remoteDeviceManager = RemoteDeviceManager("MediumPhone.arm", "34")
        remoteDeviceManager.setupRemoteDevice()
      }

      try {
        println("Waiting for boot")
        if (!SystemInfo.isWindows) {
          emulator!!.waitForBoot()
        }

        println("Waiting for device")
        if (!SystemInfo.isWindows) {
          adb.waitForDevice(emulator!!)
        } else {
          adb.waitForRemoteDevice()
        }

        system.runStudio(project, watcher.dashboardName) { studio ->
          studio.waitForSync()
          studio.waitForIndex()

          println("Waiting for project init")
          studio.waitForProjectInit()

          // Open the file ahead of time so that Live Edit is ready when we want to make a change
          val ktPath = project.targetProject.resolve("src/main/java/com/example/applychanges/MainActivity.kt")
          studio.openFile("ApplyChangesTest", ktPath.toString())

          val xmlPath = project.targetProject.resolve("src/main/res/values/strings.xml")
          studio.openFile("ApplyChangesTest", xmlPath.toString())

          studio.executeAction("MakeGradleProject")
          studio.waitForBuild()
          studio.executeAction("Run")

          studio.waitForEmulatorStart(system.installation.ideaLog, emulator, "com\\.example\\.applychanges", 60, TimeUnit.SECONDS)

          adb.runCommand("logcat") { waitForLog(".*OnResume Before.*", 600, TimeUnit.SECONDS) }

          val ktNewContents = "printAfter()\n"
          studio.editFile(ktPath.toString(), "(?s)// EASILY SEARCHABLE ONRESUME LINE.*?// END ONRESUME SEARCH", ktNewContents)

          val xmlNewContents = "new\n"
          studio.editFile(xmlPath.toString(), "(?s)<!-- EASILY SEARCHABLE RESOURCE LINE.*?<!-- END RESOURCE SEARCH", xmlNewContents)

          studio.executeAction("android.deploy.ApplyChanges")

          adb.runCommand("logcat") { waitForLog(".*OnResume After with resource status: new.*", 600, TimeUnit.SECONDS) }
        }
      } finally {
        if (!SystemInfo.isWindows) emulator?.close() else remoteDeviceManager?.close()
      }
    }
  }
}
