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
package com.android.tools.idea.projectsystem.runsIndexingWithGradle

import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.replaceContent
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.findAppModule
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class FirebaseTestLabIntegrationTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.Companion.withIntegrationTestEnvironment()

  @Test
  fun ftlEnabled() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION).applyFirebaseTestLabPlugin()

    preparedProject.open { project ->
      val projectSystem = project.getProjectSystem() as GradleProjectSystem
      Truth.assertThat(projectSystem.isManagedDevicesEnabled(project, currentModule = null)).isTrue()
      Truth.assertThat(projectSystem.isManagedDevicesEnabled(project, project.findAppModule())).isTrue()
    }
  }

  @Test
  fun ftlDisabled() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      val projectSystem = project.getProjectSystem() as GradleProjectSystem
      Truth.assertThat(projectSystem.isManagedDevicesEnabled(project, currentModule = null)).isFalse()
      Truth.assertThat(projectSystem.isManagedDevicesEnabled(project, project.findAppModule())).isFalse()
    }
  }

  private fun PreparedTestProject.applyFirebaseTestLabPlugin(): PreparedTestProject {
    root.resolve("build.gradle").replaceContent { content ->
      content.replace(
        "classpath 'com.android.tools.build:gradle:",
        "classpath 'com.google.firebase.testlab:testlab-gradle-plugin:0.0.1-alpha11'\nclasspath 'com.android.tools.build:gradle:"
      )
    }
    root.resolve("app/build.gradle").replaceContent {
      it + """

      apply plugin: 'com.google.firebase.testlab'
      """.trimIndent()
    }

    return this
  }
}