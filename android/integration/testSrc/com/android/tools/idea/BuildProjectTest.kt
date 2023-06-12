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
package com.android.tools.idea

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import org.junit.Rule
import org.junit.Test

class BuildProjectTest {
  @JvmField @Rule
  val system: AndroidSystem = AndroidSystem.standard()

  @JvmField
  @Rule
  var watcher = MemoryDashboardNameProviderWatcher()

  @Test
  fun buildProjectTest() {
    // Create a new android project, and set a fixed distribution
    val project = AndroidProject("tools/adt/idea/android/integration/testData/minapp")

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/buildproject_deps.manifest"))

    system.runStudio(project, watcher.dashboardName) { studio ->
      studio.waitForSync()
      studio.waitForIndex()
      studio.executeAction("MakeGradleProject")
      studio.waitForBuild()
    }
  }
}