/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.asdriver.tests.AndroidStudio
import com.android.tools.asdriver.tests.Emulator

/**
 * This is the base test class for all Profiler Startup tests. This class verifies the functionality of the startup task-based profiling
 * feature in Android Studio. It specifically checks if the profiler can successfully start and stop a task and if the UI components
 * are displayed correctly.
 *
 * All Profiler integration tests related to startup tasks should extend this class.
 */
abstract class ProfilersStartupTaskTestBase : ProfilersTestBase() {

  abstract fun selectTask(studio: AndroidStudio)
  abstract fun verifyTaskStarted(studio: AndroidStudio)
  abstract fun verifyTaskStopped(studio: AndroidStudio)
  abstract fun verifyUIComponents(studio: AndroidStudio)

  protected fun testStartUpTask() {
    taskBasedProfiling(
      deployApp = false,
      testFunction = {studio, adb ->
        Thread.sleep(5000)
        invokeProfilerToolWindow(studio)
        waitForProfilerDeviceConnection()
        getLogger().info("Test Wait completed")
        Thread.sleep(20000)

        // Start system trace task from process start
        selectProcess(studio)
        selectTask(studio)
        setProfilingStartingPointToProcessStart(studio)
        Thread.sleep(2000)
        startTask(studio)

        // Wait for app to be deployed.
        waitForAppToBeDeployed(adb, ".*Hello Minimal World!.*")
        verifyTaskStarted(studio)
        Thread.sleep(4000)

        stopTask(studio)
        verifyTaskStopped(studio)
        verifyUIComponents(studio)
      }
    )
  }
}
