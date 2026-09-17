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

import com.android.tools.asdriver.tests.AndroidStudio
import com.android.tools.profilers.integration.ProfilersTaskTestBase
import org.junit.Test

class SystemTraceInEditorTaskTest : ProfilersTaskTestBase() {

  override fun selectTask(studio: AndroidStudio) {
    selectSystemTraceTask(studio)
  }

  override fun verifyTaskStarted(studio: AndroidStudio) {
    verifyIdeaLog(".*PROFILER\\:\\s+Session\\s+started.*support\\s+level\\s+\\=DEBUGGABLE\$", 120)
    verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+start\\s+succeeded\$", 120)
  }

  override fun verifyTaskStopped(studio: AndroidStudio) {
    verifyIdeaLog(".*PROFILER\\:\\s+CPU\\s+capture\\s+stop\\s+succeeded\$", 300)
    // Wait for the editor to open (early signal)
    verifyIdeaLog(".*Perfetto\\s+file\\s+editor\\s+opened\\s+for\\s+file:.*", 600)
    // Wait for the editor to fully load the trace data (late success signal)
    verifyIdeaLog(".*High\\s+level\\s+trace\\s+data\\s+loaded.*", 600)
  }

  override fun verifyUIComponents(studio: AndroidStudio) {
    // Wait for the actual Perfetto View or Trace Component
    studio.waitForComponentByClass("PerfettoView")
  }

  @Test
  fun test() {
    system.installation.addVmOption("-Dprofiler.system.trace.in.editor=true")
    testTask()
  }
}
