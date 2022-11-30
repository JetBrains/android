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

class VisualLintTest {

  @get:Rule
  val system = AndroidSystem.standard()

  @get:Rule
  var watcher = MemoryDashboardNameProviderWatcher()

  @Test
  fun visualLintBasicTest() {
    system.installation.addVmOption("-Didea.log.debug.categories=#com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService")
    // Create a new android project, and set a fixed distribution
    val project = AndroidProject("tools/adt/idea/designer/testData/projects/visualLintApplication")
    project.setDistribution("tools/external/gradle/gradle-7.3.3-bin.zip")

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(MavenRepo("tools/adt/idea/designer/layout_preview_deps.manifest"))
    system.runStudio(project, watcher.dashboardName) { studio ->
      studio.waitForSync()
      studio.waitForIndex()

      val dashboardPath: Path =
        project.targetProject.resolve("app/src/main/res/layout/fragment_dashboard.xml")
      studio.openFile("visualLintApplication", dashboardPath.toString())
      studio.waitForComponentByClass("DesignSurfaceScrollPane", "JBViewport", "SceneViewPanel")
      system.installation.ideaLog.waitForMatchingLine(".*Visual Lint analysis finished, 2 errors found", 10, TimeUnit.SECONDS)

      val notificationsPath: Path =
        project.targetProject.resolve("app/src/main/res/layout/fragment_notifications.xml")
      studio.openFile("visualLintApplication", notificationsPath.toString())
      studio.waitForComponentByClass("DesignSurfaceScrollPane", "JBViewport", "SceneViewPanel")
      system.installation.ideaLog.waitForMatchingLine(".*Visual Lint analysis finished, 1 error found", 10, TimeUnit.SECONDS)

      val homePath: Path =
        project.targetProject.resolve("app/src/main/res/layout/fragment_home.xml")
      studio.openFile("visualLintApplication", homePath.toString())
      studio.waitForComponentByClass("DesignSurfaceScrollPane", "JBViewport", "SceneViewPanel")
      system.installation.ideaLog.waitForMatchingLine(".*Visual Lint analysis finished, 2 errors found", 10, TimeUnit.SECONDS)

      // Make button 100dp wide instead of 0dp (corresponding to match_parent). That should fix one of the issues.
      studio.editFile(homePath.toString(), "(?s)(id/button.+=\")0(dp)", "$1100$2")
      system.installation.ideaLog.waitForMatchingLine(".*Visual Lint analysis finished, 1 error found", 10, TimeUnit.SECONDS)
    }
  }
}