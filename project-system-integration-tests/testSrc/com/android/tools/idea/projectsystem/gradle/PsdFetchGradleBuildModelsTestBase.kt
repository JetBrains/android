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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidStudio
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import org.junit.Rule
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

open class PsdFetchGradleBuildModelsTestBase {
  private val gradleModelsTimeoutSeconds: Long = 60

  @JvmField
  @Rule
  val system: AndroidSystem = AndroidSystem.standard()

  @JvmField
  @Rule
  var watcher = MemoryDashboardNameProviderWatcher()

  fun verifyPsdFetchesGradleModels(testProjectPath: String, testRepoManifest: String) {
    val project = AndroidProject(testProjectPath)
    system.installRepo(MavenRepo(testRepoManifest))

    system.runStudio(project, watcher.dashboardName) { studio ->
      studio.waitForSync()
      studio.waitForIndex()
      openPsdAndVerifyGradleModelsFetched(studio)
    }
  }

  private fun openPsdAndVerifyGradleModelsFetched(studio: AndroidStudio) {
    var modelsFetched = true
    val thread = thread(start = true) {
      try {
        // Wait for successful model fetch and UI refresh
        system.installation.ideaLog.waitForMatchingLine(
          ".*PSD fetched \\((\\d+) Gradle model\\(s\\). Refreshing the UI model.*",
          ".*PSD failed to fetch Gradle models.*",
          true, gradleModelsTimeoutSeconds,
          TimeUnit.SECONDS)
      } catch (e: Exception) {
        modelsFetched = false
        throw e
      } finally {
        studio.invokeComponent("OK") // Close PSD
      }
    }
    studio.executeAction("ShowProjectStructureSettings")
    thread.join()
    assert(modelsFetched)
  }
}
