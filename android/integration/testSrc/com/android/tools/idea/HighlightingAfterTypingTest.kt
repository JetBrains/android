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
package com.android.tools.idea

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidStudio
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import com.android.tools.platform.performance.testing.PlatformPerformanceBenchmark
import org.junit.Rule
import org.junit.Test

class HighlightingAfterTypingTest {
  @JvmField
  @Rule
  val system: AndroidSystem = AndroidSystem.standard()

  @JvmField
  @Rule
  var watcher = MemoryDashboardNameProviderWatcher()

  @Test
  fun testHighlightingAfterTyping() {
    // Create a new android project, and set a fixed distribution
    val project = AndroidProject("tools/adt/idea/android/integration/testData/architecture-samples")
    // Don't show Decompiler legal notice in case of resolving in .class files.
    system.installation.acceptLegalDecompilerNotice()

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/editor_performance_test_deps.manifest"))
    project.setDistribution("tools/external/gradle/gradle-8.6-bin.zip")

    system.installation.addVmOption("-Didea.is.integration.test=true")

    system.runStudio(project) { studio ->
      studio.waitForSync()
      studio.waitForIndex()

      studio.openFile(null, "app/src/main/java/com/example/android/architecture/blueprints/todoapp/addedittask/AddEditTaskViewModel.kt", 89,
                      19, false,
                      false)
      // We set up caret after the `updateTitle` symbol and press backspace 5 times to remove
      // the `Title` part.
      // "_uiState.updateTitle<caret> {"
      repeat(5) {
        studio.pressKey(AndroidStudio.Keys.BACKSPACE, null)
      }
      // Now lets type the "Title" back and measure how long it takes to rehighlight the file.
      studio.delayType(null, 100, "Title")
      studio.waitForFinishedCodeAnalysis(null)
      studio.closeAllEditorTabs()
    }

    val benchmark = PlatformPerformanceBenchmark(watcher.dashboardName!!)

    system.installation.telemetry.get("highlighting_AddEditTaskViewModel.kt").reduce { _, e -> e }.get().let {
      benchmark.log("highlighting_AddEditTaskViewModel", it)
    }
    system.installation.telemetry.get("firstCodeAnalysis").reduce { _, e -> e }.get().let {
      benchmark.log("firstCodeAnalysis", it)
    }
  }
}