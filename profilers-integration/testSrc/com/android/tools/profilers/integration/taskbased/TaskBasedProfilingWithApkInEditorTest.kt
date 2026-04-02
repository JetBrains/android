/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.profilers.integration.taskbased

import com.android.tools.profilers.integration.ProfilersTestBase
import com.android.tools.profilers.integration.TestConstants
import org.junit.Test

class TaskBasedProfilingWithApkInEditorTest : ProfilersTestBase() {

  override val projectPath = TestConstants.APK_PROJECT_PATH

  @Test
  fun test() {
    system.installation.addVmOption("-Dprofiler.system.trace.in.editor=true")
    profileAppUsingApk(
      enableTaskBasedProfiling = true,
      testFunction = { studio, adb ->
        profileAction(studio)
        waitForAppToBeDeployed(adb, ".*Hello Minimal World!.*")
        waitForProfilerTaskBasedToolWindowToBeActivated(studio)
        waitForProfilerDeviceConnection()

        Thread.sleep(2000)

        selectDevice(studio)
        selectProcess(studio)
        selectSystemTraceTask(studio)
        setProfilingStartingPointToNow(studio)

        startTask(studio)
        verifyIdeaLog(".*PROFILER\\:\\s+Session\\s+started.*support\\s+level\\s+\\=DEBUGGABLE\$", 240)
        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+start\\s+succeeded\$", 120)
        Thread.sleep(4000)

        stopTask(studio)
        verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+stop\\s+succeeded\$", 300)

        // Wait for the editor to open (early signal)
        verifyIdeaLog(".*Perfetto\\s+file\\s+editor\\s+opened\\s+for\\s+file:.*", 600)
        // Wait for the editor to fully load the trace data (late success signal)
        verifyIdeaLog(".*High\\s+level\\s+trace\\s+data\\s+loaded.*", 600)

        // Wait for the actual Perfetto View or Trace Component
        studio.waitForComponentByClass("PerfettoView")
      },
    )
  }
}
