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

class MultiProjectSameWindowTest {
  @JvmField @Rule val system: AndroidSystem = AndroidSystem.standard()

  @Test
  fun multiProjectTestSameWindow() {
    // Create a new android project, and set a fixed distribution
    val project = AndroidProject("tools/adt/idea/android/integration/testData/minapp")

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/buildproject_deps.manifest"))

    system.runStudio(project) { studio ->
      studio.waitForSync()
      studio.waitForIndex()

      // Install and open a second project.
      val project2 = createLiveEditProject()
      system.installLiveEditMavenDependencies()

      val projectPath2 = project2.install(system.installation.fileSystem.root)
      system.installation.trustPath(projectPath2)

      project2.setSdkDir(system.sdk.sourceDir)
      studio.openProject(projectPath2.toString(), false)


      // Wait for sync so that the project name reliably is "LiveEditTest".
      // Since other actions might have reset the log, we may accidentally find the first sync
      // result instead of the second. Therefore, this code resets the log (again) and then checks
      // for two syncs to have completed.
      system.installation.ideaLog.reset()
      studio.waitForSync()
      studio.waitForSync()

      // Open file in second project
      val mainActivityPath2 =
        project2.targetProject.resolve("app/src/main/java/com/example/liveedittest/MainActivity.kt")
      studio.openFile(project2.getTargetProject().getFileName().toString(), mainActivityPath2.toString())
      Thread.sleep(3000)

      // Edit file
      studio.editFile(
        project2.getTargetProject().getFileName().toString(),
        mainActivityPath2.toString(),
        "Before editing",
        "After editing",
      )

      // Verify edit - this is a verification since editing will fail if the search regex can't be
      // found.
      studio.editFile(
        project2.getTargetProject().getFileName().toString(),
        mainActivityPath2.toString(),
        "After editing",
        "After second editing",
      )
    }
  }
}
