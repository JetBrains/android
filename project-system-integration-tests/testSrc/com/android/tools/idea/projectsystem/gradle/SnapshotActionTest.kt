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
package com.android.tools.idea.projectsystem.gradle

import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.util.EmbeddedDistributionPaths
import org.junit.Rule
import org.junit.Test
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.toPath

class SnapshotActionTest {

  @JvmField @Rule
  var system: AndroidSystem = AndroidSystem.standard()

  @Test
  fun `Check project dump action`() {
    system.installation.apply {
      addVmOption("-Dstudio.project.sync.debug.mode=true")
      addVmOption("-Didea.is.internal=true")

    }
    val project = AndroidProject("tools/adt/idea/project-system-integration-tests/testData/snapshots/sample-project")

    system.installRepo(MavenRepo("tools/adt/idea/project-system-integration-tests/testData/snapshots/sample-project_repo.manifest"))

    system.runStudio(project).use { studio ->
      studio.waitForSync()

      studio.executeAction("Android.DumpProject")
      val projectDumpFile =
        system.installation.ideaLog.waitForMatchingLine(".*Project structure dumped to file: (?<filepath>file:.*)", 1, TimeUnit.MINUTES)
          .group("filepath")
          .let { URI(it).toPath() }
      assertThat(projectDumpFile).exists()
      Files.copy(projectDumpFile, TestUtils.getTestOutputDir().resolve(projectDumpFile.fileName))
      assertThat(projectDumpFile).contains("com.google.android.material:material:1.10.0")

      studio.executeAction("Android.DumpProjectIdeModels")
      val projectModelsDumpFile =
        system.installation.ideaLog.waitForMatchingLine(".*Android IDE models dumped to file: (?<filepath>file:.*)", 1, TimeUnit.MINUTES)
          .group("filepath")
          .let { URI(it).toPath() }
      assertThat(projectModelsDumpFile).exists()
      Files.copy(projectModelsDumpFile, TestUtils.getTestOutputDir().resolve(projectModelsDumpFile.fileName))
      assertThat(projectModelsDumpFile).contains("com.google.android.material:material:1.10.0")
    }
  }
}
