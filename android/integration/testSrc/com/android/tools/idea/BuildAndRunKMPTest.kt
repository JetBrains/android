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
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class BuildAndRunKMPTest {
  @JvmField
  @Rule
  val system = AndroidSystem.standard()

  @JvmField
  @Rule
  var watcher = MemoryDashboardNameProviderWatcher()

  @Ignore("b/314825800")
  @Test
  fun buildAndRunKmpTest() {
    val project = AndroidProject("tools/adt/idea/android/integration/testData/kmpapp")
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/buildkmpproject_deps.manifest"))

    system.runAdb { adb ->
      system.runStudio(project, watcher.dashboardName)  { studio ->
        system.runEmulator(Emulator.SystemImage.API_31) { emulator ->
          studio.waitForSync()
          studio.waitForIndex()
          println("Finished waiting for index")

          println("Waiting for boot")
          emulator.waitForBoot()

          println("Waiting for device")
          adb.waitForDevice(emulator)

          studio.executeAction("MakeGradleProject")
          studio.waitForBuild()

          println("Running the app")
          studio.executeAction("Run")

          system.installation.ideaLog.waitForMatchingLine(
            ".*AndroidProcessHandler - Adding device emulator-${emulator.portString} to monitor for launched app: com\\.google\\.samples\\.apps\\.kmp",
            60, TimeUnit.SECONDS)
          emulator.logCat.waitForMatchingLine(".*Hello World!.*", 30, TimeUnit.SECONDS)
        }
      }
    }
  }
}