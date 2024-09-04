/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.asdriver.tests.ComponentMatchersBuilder
import com.android.tools.asdriver.tests.MavenRepo
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.TimeUnit

// We have some code in ComposeUsageGroupingRuleProvider that logs usages it sees for the explicit purposes of this test.
// The Provider is otherwise unrelated to this test.
private const val regex = ".*ComposeUsageGroupingRuleProvider.*?Saw usage.*"

@RunWith(JUnit4::class)
class ShowUsagesTest {
  @get:Rule
  val system: AndroidSystem = AndroidSystem.standard()

  @Test
  fun basicShowUsages() {
    val installation = system.installation
    val project = AndroidProject("tools/adt/idea/android/integration/testData/minapp")

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/showusages_deps.manifest"));

    // Ensure that our log messages show up in idea.log.
    installation.addVmOption("-Didea.log.debug.categories=#com.android.tools.compose.ComposeUsageGroupingRuleProvider");

    system.runStudio(project).use { studio ->
      studio.waitForSync()
      // Line 10, column 15 corresponds to the symbol "label" which should have 2 usages in the rest of the file.
      studio.openFile(project.targetProject.fileName.toString(), "src/main/java/com/example/minapp/MainActivity.kt", 10, 15)
      ComponentMatchersBuilder()
        .addSwingClassRegexMatch(".*EditorComponentImpl")
        .let { studio.waitForComponent(it) }
      // This is here instead of above because sometimes Studio kicks off some additional indexing after we open the file.
      studio.waitForIndex()
      repeat(2) { studio.executeAction("ShowUsages", AndroidStudio.DataContextSource.SELECTED_TEXT_EDITOR) }
      repeat(2) { installation.ideaLog.waitForMatchingLine(regex, 1L, TimeUnit.MINUTES) }
    }
  }
}
