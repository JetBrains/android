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
import com.android.tools.asdriver.tests.ComponentMatchersBuilder
import com.android.tools.asdriver.tests.MavenRepo
import java.nio.file.Paths
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.io.path.name

@RunWith(JUnit4::class)
class GoToDeclarationTest {
  @get:Rule
  val system: AndroidSystem = AndroidSystem.standardWithTmpDir()

  @Test
  fun goToDeclaration() {
    val projectArtifactsPath = Paths.get("tools/adt/idea/android/integration/languagehighlighting_project_model")
    val project = AndroidProject(projectArtifactsPath.resolve("languagehighlighting").toString())

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/languagehighlighting_deps.manifest"))
    system.getInstallation().copySystemDir(projectArtifactsPath);
    system.runStudio(project).use { studio ->
      studio.waitForSyncSkippedLog()
      studio.waitForIndexingSkippedLog()

      val path = project.targetProject.resolve("src/main/java/com/example/languagehighlighting/MainActivity.kt")
      studio.openFile(project.targetProject.name, path.toString())

      // Position the cursor over a reference to the `Hello` class, and invoke going to its declaration.
      studio.moveCaret("Hel|lo()")
      studio.executeAction("GotoDeclaration", AndroidStudio.DataContextSource.SELECTED_TEXT_EDITOR)

      // Validate that the navigation has occurred by looking for the editor tab label for the `Hello.kt` file.
      studio.waitForComponent(
        ComponentMatchersBuilder().apply {
          addSwingClassRegexMatch(".*com\\.intellij\\.ui\\.tabs\\.impl\\.TabLabel.*")
          addComponentTextExactMatch("Hello.kt")
        }
      )
    }
  }
}
