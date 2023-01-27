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
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.warn
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


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
    project.setDistribution("tools/external/gradle/gradle-7.5-bin.zip")
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/run_instrumented_test_project_deps.manifest"))

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
          system.installation.ideaLog.waitForMatchingLine(
            ".*AndroidProcessHandler - Adding device emulator-${emulator.portString} to monitor for launched app: com\\.example\\.instrumentedtestapp",
            5, TimeUnit.MINUTES)
          adb.runCommand("logcat").waitForLog(".*Instrumented Test Success!!.*", 5.minutes)
        }
      }
    }
  }
}
