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

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.util.EmbeddedDistributionPaths
import org.junit.Rule
import org.junit.Test

class OldProjectAppTest {

  @JvmField @Rule
  var system: AndroidSystem = AndroidSystem.standard()

  @Test
  fun `Given old app configured with JDK 11 and compatible gradle version When open project with recent studio Then gradle sync succeed`() {
    system.installation.apply {
      addVmOption("-Didea.log.debug.categories=#com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver")
      addVmOption("-Dstudio.project.sync.debug.mode=true")
    }
    val project = AndroidProject("tools/adt/idea/project-system-integration-tests/testData/oldprojectapp")
    project.setDistribution("tools/external/gradle/gradle-6.7.1-bin.zip")

    val embeddedJdk11Path = EmbeddedDistributionPaths.getJdkRootPathFromSourcesRoot("prebuilts/studio/jdk/jdk11")
    system.setEnv(IdeSdks.JDK_LOCATION_ENV_VARIABLE_NAME, embeddedJdk11Path.toString())
    system.installRepo(MavenRepo("tools/adt/idea/project-system-integration-tests/oldprojectapp_deps.manifest"))
    system.runStudio(project).use { studio ->
      studio.waitForSync()
    }
  }
}
