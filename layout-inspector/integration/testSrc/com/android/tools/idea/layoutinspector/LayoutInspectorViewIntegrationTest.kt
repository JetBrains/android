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
package com.android.tools.idea.layoutinspector

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import org.jetbrains.kotlin.utils.join
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class LayoutInspectorViewIntegrationTest {

  @get:Rule
  val system = AndroidSystem.standard()

  @Test
  fun testEmptyApplication() {
    val project = AndroidProject("tools/adt/idea/layout-inspector/integration/testData/projects/emptyApplication")

    system.installation.addVmOption(
      join(listOf(
        "-Didea.log.debug.categories=#com.android.tools.idea.layoutinspector.LayoutInspector",
        "-Dlayout.inspector.dynamic.layout.inspector.enable.auto.connect.foreground=false",  // Disable foreground detection: b/262770420
      ), "\n")
    )

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(MavenRepo("tools/adt/idea/layout-inspector/view_project_deps.manifest"))
    system.runAdb { adb ->
      system.runEmulator { emulator ->
        system.runStudio(project).use { studio ->
          studio.waitForSync()
          studio.waitForIndex()
          studio.executeAction("MakeGradleProject")
          studio.waitForBuild()
          studio.executeAction("Run")
          val ideaLog = system.installation.ideaLog
          ideaLog.waitForMatchingLine(".*AndroidProcessHandler - Adding device emulator-${emulator.portString} to monitor for " +
                                      "launched app: com\\.example\\.emptyapplication", 600, TimeUnit.SECONDS)
          adb.runCommand("shell", "settings", "put global debug_view_attributes 1", emulator = emulator)
          emulator.waitForBoot()
          studio.executeAction("Android.RunLayoutInspector")
          ideaLog.waitForMatchingLine(".*g:1 Model Updated for process: com.example.emptyapplication", 120, TimeUnit.SECONDS)
        }
      }
    }
  }
}