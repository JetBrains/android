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
import com.android.tools.idea.testing.BuildEnvironment
import com.android.tools.idea.testing.TestProjectPaths.EMPTY_APPLICATION_DECLARATIVE
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.getTextForFile
import com.android.tools.idea.testing.withDeclarative
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DependenciesProcessorTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule().withDeclarative()
  private val project get() = projectRule.project

  @Test
  fun simpleAddPluginAndDependency() {
    projectRule.loadProject(SIMPLE_APPLICATION)
    val projectBuildModel = ProjectBuildModel.get(project)
    val appModule = project.findModule("app")
    val config = DependenciesConfig.defaultConfig().withDependency(
      DependencyDescription("api", "my.not.existing.dependency:gradle:1.2.3-dev")
    ).withPlugin(
      PluginDescription("org.example", "1.0", "org.example.gradle.plugin:gradle")
    )
    WriteCommandAction.runWriteCommandAction(project) {
      DependenciesProcessor(projectBuildModel).apply(config, appModule)
      projectBuildModel.applyChanges()
    }
    runReadAction {

      assertThat(project.getTextForFile("build.gradle"))
        .contains("classpath 'org.example.gradle.plugin:gradle:1.0'")

      assertThat(project.getTextForFile("app/build.gradle"))
        .contains("api 'my.not.existing.dependency:gradle:1.2.3-dev'")

      assertThat(project.getTextForFile("app/build.gradle"))
        .contains("apply plugin: 'org.example'")
    }
  }

  @Test
  fun simpleAddEcosystemPluginAndDependency() {
    projectRule.loadProject(EMPTY_APPLICATION_DECLARATIVE)
    val projectBuildModel = ProjectBuildModel.get(project)
    val appModule = project.findModule("app")
    val env = BuildEnvironment.getInstance()
    val config = DependenciesConfig.defaultConfig().withDependency(
      DependencyDescription("api", "my.not.existing.dependency:gradle:1.2.3-dev")
    ).withPlugin(
      PluginDescription("com.android.application",
                        env.gradlePluginVersion,
                        "com.android.tools.build:gradle:${env.gradlePluginVersion}",
                        "com.android.ecosystem",
                        env.gradlePluginVersion)
    )
    WriteCommandAction.runWriteCommandAction(project) {
      DependenciesProcessor(projectBuildModel).apply(config, appModule)
      projectBuildModel.applyChanges()
    }
    runReadAction {
      assertThat(project.getTextForFile("settings.gradle.dcl"))
        .contains("id(\"com.android.ecosystem\").version(\"${env.gradlePluginVersion}\")")

      assertThat(project.getTextForFile("app/build.gradle.dcl"))
        .contains("androidApp {")
    }
  }

}