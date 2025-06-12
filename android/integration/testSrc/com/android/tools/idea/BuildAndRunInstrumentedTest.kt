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
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import com.android.tools.testlib.Emulator
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

// TODO(b/279220000 && IDEA-337844): Run button is grayed out in the failing tests while debug button is not.
class BuildAndRunInstrumentedTest {
  @JvmField
  @Rule
  val system: AndroidSystem = AndroidSystem.standard()

  @JvmField
  @Rule
  var watcher = MemoryDashboardNameProviderWatcher()


  @Test
  fun deployInstrumentedTest() {
    val project = AndroidProject("tools/adt/idea/android/integration/testData/InstrumentedTestApp")
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/run_instrumented_test_project_deps.manifest"))

    system.runAdb { adb ->
      system.runEmulator(Emulator.SystemImage.API_33_ATD) { emulator ->
        system.runStudio(project, watcher.dashboardName) { studio ->
          studio.waitForSync()
          studio.waitForIndex()
          emulator.waitForBoot()
          adb.waitForDevice(emulator)

          studio.executeAction("MakeGradleProject")
          studio.waitForBuild()
          studio.waitForIndex()

          studio.waitForSmart();
          studio.executeActionWhenSmart("Run")

          studio.waitForEmulatorStart(system.installation.ideaLog, emulator, "com\\.example\\.instrumentedtestapp", 1, TimeUnit.MINUTES)
          adb.runCommand("logcat").waitForLog(".*Instrumented Test Success!!.*", 5.minutes)
        }
      }
    }
  }
}
