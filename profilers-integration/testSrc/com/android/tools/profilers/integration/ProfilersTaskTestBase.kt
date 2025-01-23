/*
 * Copyright (C) 2025 The Android Open Source Project
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

abstract class ProfilersTaskTestBase : ProfilersTestBase() {

  abstract fun selectTask(studio: AndroidStudio)
  abstract fun verifyTaskStarted(studio: AndroidStudio)
  abstract fun verifyTaskStopped(studio: AndroidStudio)
  abstract fun verifyUIComponents(studio: AndroidStudio)
  open fun stopCurrentTask(studio: AndroidStudio) = stopTask(studio)

  protected fun testTask() {
    taskBasedProfiling(
      deployApp = true,
      testFunction = { studio, _ ->
        // Selecting the device.
        selectDevice(studio)
        // Selecting the process id which consists of `minapp`
        selectProcess(studio)
        // Select task.
        selectTask(studio)
        // Select Process start to "Now"
        setProfilingStartingPointToNow(studio)
        Thread.sleep(2000)
        // Start task
        startTask(studio)
        verifyTaskStarted(studio)
        Thread.sleep(4000)

        // Stop task
        stopCurrentTask(studio)
        verifyTaskStopped(studio)
        verifyUIComponents(studio)
      }
    )
  }
}
