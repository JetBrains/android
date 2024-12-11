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
package com.android.tools.idea.compose

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Note: the "Kotlin" in the name of this is because the test ensures Compose Preview works on
 * Kotlin files (as opposed to XML files).
 */
class ComposePreviewKotlin {

  @get:Rule val system = AndroidSystem.standard()

  @get:Rule var watcher = MemoryDashboardNameProviderWatcher()

  private lateinit var project: AndroidProject

  @Before
  fun setup() {
    // Create a new android project, and set a fixed distribution
    project = AndroidProject("tools/adt/idea/compose-designer/testData/projects/composepreview")

    system.installRepo(MavenRepo("tools/adt/idea/compose-designer/compose_preview_deps.manifest"))

    // Enable ComposePreviewKotlin
    system.installation.addVmOption(
      "-Didea.log.debug.categories=#com.android.tools.idea.compose.preview.ComposePreviewRepresentation"
    )
  }

  @Test
  fun composePreviewKotlinBasicTest() {
    system.runStudio(project, watcher.dashboardName) { studio ->
      studio.waitForSync()
      studio.waitForIndex()

      val path: Path =
        project.targetProject.resolve(
          "app/src/main/java/com/example/composepreviewtest/MainActivity.kt"
        )
      studio.openFile("ComposePreviewTest", path.toString())

      // Ensure the instructions component is visible. It's the one that says "A successful build
      // is needed before the preview can be displayed. Build & Refresh..."
      studio.waitForComponentByClass("InstructionsComponent")
      // A build is necessary for Compose Preview to show.
      studio.executeAction("MakeGradleProject")
      studio.waitForBuild()
      studio.waitForComponentWithExactText("DefaultPreview")
      // Check the parameterized previews also render
      studio.waitForComponentWithExactText("state 0")

      system.installation.ideaLog.waitForMatchingLine(".*Render completed(.*)", 2, TimeUnit.MINUTES)
      studio.executeAction("CloseAllEditors")
    }
  }
}
