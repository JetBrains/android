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
package com.android.tools.idea.play.test

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Rule
import org.junit.Test

class PlayPolicyInsightIntegrationTest {
  @JvmField @Rule val system = AndroidSystem.standard()!!

  @JvmField @Rule var watcher = MemoryDashboardNameProviderWatcher()

  @Test
  fun loadPolicyRulesFromBundledJar() {
    val project =
      AndroidProject("tools/adt/idea/app-quality-insights/play-policy/integration/testData/minapp")
    system.installRepo(
      MavenRepo("tools/adt/idea/app-quality-insights/play-policy/integration/minapp_deps.manifest")
    )

    system.runStudio(project, watcher.dashboardName) { studio ->
      studio.waitForSync()
      studio.waitForIndex()
      // Start a new thread to close the code inspection dialog.
      thread(start = true) { studio.invokeComponent("Analyze") }
      // Open the code inspection dialog.
      // This method blocks until the dialog gets closed.
      studio.executeAction("InspectPlayPolicyCode")
      system.installation.ideaLog.waitForMatchingLine(
        ".*rules are loaded for Play Policy Insights.*",
        "Failed to load rules for Play Policy Insights",
        60,
        TimeUnit.SECONDS,
      )
    }
  }
}
