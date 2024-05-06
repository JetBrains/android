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
package com.android.tools.profilers.integration

import com.android.tools.asdriver.tests.Adb
import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidStudio
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.Emulator
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import org.junit.Rule
import java.util.concurrent.TimeUnit

/**
 * This is the base test class for all unit tests in this project.
 * It provides common functionality for all profiler integration tests,
 * such as setting up the android studio, starting and waiting for emulator boot-up,
 * syncing and building project and some common profiler specific reusable execute actions
 * and log verifications.
 *
 * All Profiler integration tests should extend this class.
 */
open class ProfilersTestBase {

  private val testProjectMinAppPath = "tools/adt/idea/profilers-integration/testData/minapp"
  private val testMinAppRepoManifest = "tools/adt/idea/profilers-integration/minapp_deps.manifest"

  @JvmField
  @Rule
  val system: AndroidSystem = AndroidSystem.standard()

  @JvmField
  @Rule
  var watcher = MemoryDashboardNameProviderWatcher()

  protected fun profileApp(systemImage: Emulator.SystemImage,
                 testFunction: ((studio: AndroidStudio, adb: Adb) -> Unit)) {
    // Enabling profiler verbose logs behind the flag.
    system.installation.addVmOption("-Dprofiler.testing.mode=true")

    // Open android project, and set a fixed distribution
    val project = AndroidProject(testProjectMinAppPath)

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(MavenRepo(testMinAppRepoManifest))

    system.runAdb { adb ->
      system.runEmulator(systemImage) { emulator ->
        system.runStudio(project, watcher.dashboardName) { studio ->
          // Waiting for sync and build.
          studio.waitForSync()
          studio.waitForIndex()
          // Assume project build will be triggered by `testFunction` if needed.
          // Waiting for emulator to boot up.
          emulator.waitForBoot()
          adb.waitForDevice(emulator)

          // Test Function or test steps to be executed.
          testFunction.invoke(studio, adb)
        }
      }
    }
  }

  protected fun verifyIdeaLog(regexText: String, timeOut: Long) {
    system.installation.ideaLog.waitForMatchingLine(
      regexText,
      timeOut,
      TimeUnit.SECONDS)
  }

  protected fun profileWithCompleteData(studio: AndroidStudio) {
    studio.executeAction("Android.ProfileWithCompleteData")
  }

  protected fun profileWithLowOverhead(studio: AndroidStudio) {
    studio.executeAction("Android.ProfileWithLowOverhead")
  }

  protected fun stopProfilingSession(studio: AndroidStudio) {
    studio.executeAction("Android.StopProfilingSession")
    verifyIdeaLog(".*PROFILER\\:\\s+Session\\s+stopped.*support\\s+level\\s+\\=.*", 180)
  }

  protected fun startSystemTrace(studio: AndroidStudio) {
    studio.executeAction("Android.StartSystemTrace")
  }

  protected fun startCallstackSample(studio: AndroidStudio) {
    studio.executeAction("Android.StartCallstackSample")
  }

  protected fun stopCpuCapture(studio: AndroidStudio) {
    studio.executeAction("Android.StopCpuCapture")
  }
}