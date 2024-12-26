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
package com.android.tools.idea.gradle.dependencies.runsGradleDependencies

import com.android.tools.idea.gradle.dependencies.DependenciesConfig
import com.android.tools.idea.gradle.dependencies.DependenciesProcessor
import com.android.tools.idea.gradle.dependencies.DependencyDescription
import com.android.tools.idea.gradle.dependencies.PluginDescription
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.getTextForFile
import com.google.common.truth.Truth
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import org.junit.Rule
import org.junit.Test

class DependenciesProcessorTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Test
  fun simpleAddPluginAndDependency() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    val projectBuildModel = ProjectBuildModel.get(projectRule.project)
    val appModule = projectRule.project.findModule("app")
    val config = DependenciesConfig.defaultConfig().withDependency(
      DependencyDescription("api", "my.not.existing.dependency:gradle:1.2.3-dev")
    ).withPlugin(
      PluginDescription("org.example", "1.0", "org.example.gradle.plugin:gradle")
    )
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      DependenciesProcessor(projectBuildModel).apply(config, appModule)
      projectBuildModel.applyChanges()
    }
    runReadAction {
      Truth.assertThat(projectRule.project.getTextForFile("build.gradle"))
        .contains("classpath 'org.example.gradle.plugin:gradle:1.0'")

      Truth.assertThat(projectRule.project.getTextForFile("app/build.gradle"))
        .contains("api 'my.not.existing.dependency:gradle:1.2.3-dev'")

      Truth.assertThat(projectRule.project.getTextForFile("app/build.gradle"))
        .contains("apply plugin: 'org.example'")
    }
  }

}