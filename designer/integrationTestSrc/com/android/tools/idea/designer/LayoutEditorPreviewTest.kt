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
package com.android.tools.idea.designer

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Ensures that Layout Editor Preview works for an XML file. */
class LayoutEditorPreviewTest {

  @get:Rule
  val system = AndroidSystem.standard()

  @get:Rule
  var watcher = MemoryDashboardNameProviderWatcher()

  @Test
  fun layoutEditorPreviewBasicTest() {
    // Create a new android project, and set a fixed distribution
    val project = AndroidProject("tools/adt/idea/designer/testData/projects/simpleApplication")
    project.setDistribution("tools/external/gradle/gradle-7.3.3-bin.zip")

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(MavenRepo("tools/adt/idea/designer/layout_preview_deps.manifest"))
    system.runAdb { adb ->
      system.runEmulator { emulator ->
        system.runStudio(project, watcher.dashboardName) { studio ->
          studio.waitForSync()
          studio.waitForIndex()
          studio.executeAction("MakeGradleProject")
          studio.waitForBuild()

          val path: Path =
            project.targetProject.resolve("app/src/main/res/layout/activity_my.xml")
          studio.openFile("simpleApplication", path.toString())
          studio.waitForComponentByClass("DesignSurfaceScrollPane", "JBViewport", "SceneViewPanel")

          studio.executeAction("Run")
          system.installation.ideaLog.waitForMatchingLine(
            ".*AndroidProcessHandler - Adding device emulator-${emulator.portString} to monitor for launched app: google\\.simpleapplication",
            60, TimeUnit.SECONDS)
        }
      }
    }
  }
}
