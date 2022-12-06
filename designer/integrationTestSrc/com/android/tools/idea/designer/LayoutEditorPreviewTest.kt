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
import com.android.tools.asdriver.tests.AndroidStudio
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

  private fun AndroidStudio.waitForSuccessfulRender(xmlName: String) {
    system.installation.ideaLog
      .waitForMatchingLine(
        ".*RenderResult\\{renderResult=Result\\{status=SUCCESS, errorMessage=null, throwable=null, data=null\\}, psiFile=XmlFile:$xmlName.*",
        10, TimeUnit.SECONDS)
  }

  private fun AndroidStudio.openAndWaitForRender(path: Path) {
    openFile("simpleApplication", path.toString())
    waitForComponentByClass("DesignSurfaceScrollPane", "JBViewport", "SceneViewPanel")
    waitForSuccessfulRender(path.fileName.toString())
  }

  @Test
  fun layoutEditorPreviewBasicTest() {
    // Create a new android project, and set a fixed distribution
    val project = AndroidProject("tools/adt/idea/designer/testData/projects/simpleApplication")
    project.setDistribution("tools/external/gradle/gradle-7.3.3-bin.zip")

    // Enable additional logging
    system.installation.addVmOption(
      "-Didea.log.debug.categories=#com.android.tools.idea.rendering.RenderResult"
    )

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(MavenRepo("tools/adt/idea/designer/layout_preview_deps.manifest"))
    system.runAdb { adb ->
      system.runEmulator { emulator ->
        system.runStudio(project, watcher.dashboardName) { studio ->
          studio.waitForSync()
          studio.waitForIndex()
          studio.executeAction("MakeGradleProject")
          studio.waitForBuild()

          studio.openAndWaitForRender(project.targetProject.resolve("app/src/main/res/layout/simple_layout.xml"))
          studio.executeAction("CloseAllEditors")
          studio.openAndWaitForRender(project.targetProject.resolve("app/src/main/res/layout/normal_layout.xml"))
          studio.executeAction("CloseAllEditors")
          val complexLayoutFile = project.targetProject.resolve("app/src/main/res/layout/complex_layout.xml")
          studio.openAndWaitForRender(complexLayoutFile)
          studio.editFile(complexLayoutFile.toString(), "\\s*<!--EASY TEXT FIND", "")
          studio.editFile(complexLayoutFile.toString(), "\\s*EASY TEXT FIND-->", "")
          studio.waitForSuccessfulRender("complex_layout.xml")

          studio.executeAction("Run")
          system.installation.ideaLog.waitForMatchingLine(
            ".*AndroidProcessHandler - Adding device emulator-${emulator.portString} to monitor for launched app: google\\.simpleapplication",
            60, TimeUnit.SECONDS)
        }
      }
    }
  }
}
