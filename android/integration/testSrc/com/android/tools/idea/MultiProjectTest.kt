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
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import org.junit.Rule
import org.junit.Test

class MultiProjectTest {
  @JvmField
  @Rule
  val system: AndroidSystem = AndroidSystem.standard()

  @Test
  fun multiProjectTest() {
    // Create a new android project, and set a fixed distribution
    val project = AndroidProject("tools/adt/idea/android/integration/testData/minapp")

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/buildproject_deps.manifest"))

    system.runStudio(project) { studio ->
      studio.waitForSync()
      studio.waitForIndex()
      
      val project2 = createLiveEditProject()
      system.installLiveEditMavenDependencies()
      val targetPath = project2.install(system.installation.fileSystem.root)
      system.installation.trustPath(targetPath)
      project2.setSdkDir(system.sdk.sourceDir)
      studio.openProject(targetPath.toString(), true)

      val path = project.targetProject.resolve("src/main/java/com/example/minapp/MainActivity.kt")
      // Make first edit
      studio.editFile(project.targetProject.fileName.toString(), path.toString(), "Hello Minimal", "Hey Minimal")
      // Wait between edits
      Thread.sleep(5000)
      studio.editFile(project.targetProject.fileName.toString(), path.toString(), "MainActivity", "DebugActivity")
      // Open file in second project
      studio.openFile(project2.targetProject.fileName.toString(), path.toString())
      Thread.sleep(3000)
      // Verify edits
      studio.editFile(project2.targetProject.fileName.toString(), path.toString(), "Hey Minimal", "Hey Minimal")
      studio.editFile(project2.targetProject.fileName.toString(), path.toString(), "DebugActivity", "DebugActivity")
    }
  }
}