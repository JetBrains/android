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
package com.android.tools.idea.gradle.dependencies.runsGradleDependencies

import com.android.tools.idea.gradle.dependencies.DeclarativeDependenciesInserter
import com.android.tools.idea.gradle.dependencies.DeclarativePluginsInserter
import com.android.tools.idea.gradle.dependencies.GroupNameDependencyMatcher
import com.android.tools.idea.gradle.dependencies.IdPluginMatcher
import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig
import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig.MatchedStrategy
import com.android.tools.idea.gradle.dependencies.PluginMatcher
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.BuildEnvironment
import com.android.tools.idea.testing.TestProjectPaths.EMPTY_APPLICATION_DECLARATIVE
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_DECLARATIVE
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.getTextForFile
import com.android.tools.idea.testing.withDeclarative
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DeclarativeDependenciesHelperTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule().withDeclarative()
  private val fixture get() = projectRule.fixture
  private val project get() = projectRule.project

  val pluginFactory: (String, String) -> PluginMatcher = { id, _ -> IdPluginMatcher(id) }
  val fakeDependencyMatcher = GroupNameDependencyMatcher("", "")

  @Test
  fun testSimpleAddDeclarative() {
    doDependenciesTest(SIMPLE_APPLICATION_DECLARATIVE,
                       { _, moduleModel, helper ->
                         val updates = helper.addDependency("api", "com.example.libs:lib2:1.0", moduleModel)
                         assertThat(updates.size).isEqualTo(1)
                       },
                       {
                         val buildFile = project.getTextForFile("app/build.gradle.dcl")
                         val dependencies = getBlockContent(buildFile, "androidApp.dependenciesDcl")
                         assertThat(dependencies).contains("api(\"com.example.libs:lib2:1.0\")")
                         // check that it's existing dependenciesDcl block - not new one
                         assertThat(dependencies).contains("api(\"com.google.guava:guava:19.0\")")
                       })
  }

  @Test
  fun testAddPluginToDeclarativeSettings() {
    doPluginTest(SIMPLE_APPLICATION_DECLARATIVE,
                 { _, _, helper ->
                   val pluginId = "com.example.foo"
                   val changed = helper.applySettingsPlugin(pluginId, "10.0")
                   assertThat(changed.size).isEqualTo(1)
                 },
                 {
                   val projectBuildContent = project.getTextForFile("app/build.gradle.dcl")
                   assertThat(projectBuildContent).doesNotContain("example")

                   val settingsBuildContent = project.getTextForFile("settings.gradle.dcl")
                   val pluginsBlockContent = getBlockContent(settingsBuildContent, "plugins")
                   assertThat(pluginsBlockContent).contains("id(\"com.example.foo\").version(\"10.0\")")
                   assertThat(settingsBuildContent).doesNotContain("libs.plugins.example.foo")
                 })
  }

  @Test
  fun testAddEcosystemPluginToDeclarative() {
    doPluginTest(EMPTY_APPLICATION_DECLARATIVE,
                 { _, model, helper ->
                   val env = BuildEnvironment.getInstance()
                   val changed = helper.addPluginOrClasspath(
                     "com.android.application",
                     "fakeClasspathModule",
                     env.gradlePluginVersion,
                     listOf(model),
                     pluginFactory,
                     fakeDependencyMatcher,
                     PluginInsertionConfig.defaultInsertionConfig()
                   )
                   assertThat(changed.size).isEqualTo(2)
                 },
                 {
                   val projectBuildContent = project.getTextForFile("app/build.gradle.dcl")
                   assertThat(projectBuildContent).doesNotContain("com.android.application")

                   val settingsBuildContent = project.getTextForFile("settings.gradle.dcl")
                   val pluginsBlockContent = getBlockContent(settingsBuildContent, "plugins")
                   assertThat(pluginsBlockContent).contains("id(\"com.android.ecosystem\").version(\"")
                 })
  }

  @Test
  fun testAddPluginToDeclarative() {
    doPluginTest(SIMPLE_APPLICATION_DECLARATIVE,
                 { _, model, helper ->
                   val changed = helper.addPluginOrClasspath(
                     "org.example",
                     "fakeClasspathModule",
                     "1.0",
                     listOf(model),
                     pluginFactory,
                     fakeDependencyMatcher,
                     PluginInsertionConfig.defaultInsertionConfig()
                   )
                   assertThat(changed.size).isEqualTo(1)
                 },
                 {
                   // TODO - not clear if/how we need to update module build file

                   val settingsBuildContent = project.getTextForFile("settings.gradle.dcl")
                   val pluginsBlockContent = getBlockContent(settingsBuildContent, "plugins")
                   assertThat(pluginsBlockContent).contains("id(\"org.example\").version(\"1.0\")")
                 })
  }

  @Test
  fun testUpdateEcosystemPluginToDeclarative() {
    doPluginTest(SIMPLE_APPLICATION_DECLARATIVE,
                 { _, model, helper ->
                   val changed = helper.addPluginOrClasspath(
                     "com.android.application",
                     "fakeClasspathModule",
                     "9999.99",
                     listOf(model),
                     pluginFactory,
                     fakeDependencyMatcher,
                     PluginInsertionConfig.defaultInsertionConfig().copy(whenFoundSame = MatchedStrategy.UPDATE_VERSION)
                   )
                   assertThat(changed.size).isEqualTo(1)
                 },
                 {
                   val settingsBuildContent = project.getTextForFile("settings.gradle.dcl")
                   val pluginsBlockContent = getBlockContent(settingsBuildContent, "plugins")
                   assertThat(pluginsBlockContent).contains("id(\"com.android.ecosystem\").version(\"9999.99\")")
                 })
  }

  private fun doDependenciesTest(
    projectPath: String,
    change: (projectBuildModel: ProjectBuildModel, model: GradleBuildModel, helper: DeclarativeDependenciesInserter) -> Unit,
    assert: () -> Unit
  ) {
    doTest(projectPath, { projectBuildModel, model ->
      val helper = DeclarativeDependenciesInserter()
      change(projectBuildModel, model, helper)
    }, assert)
  }

  private fun doPluginTest(
    projectPath: String,
    change: (projectBuildModel: ProjectBuildModel, model: GradleBuildModel, helper: DeclarativePluginsInserter) -> Unit,
    assert: () -> Unit
  ) {
    doTest(projectPath, { projectBuildModel, model ->
      val helper = DeclarativePluginsInserter(projectBuildModel)
      change(projectBuildModel, model, helper)
    }, assert)
  }

  private fun doTest(
    projectPath: String,
    change: (projectBuildModel: ProjectBuildModel, model: GradleBuildModel) -> Unit,
    assert: () -> Unit
  ) {
    projectRule.loadProject(projectPath)
    fixture.allowTreeAccessForAllFiles()
    val projectBuildModel = ProjectBuildModel.get(project)
    val moduleModel: GradleBuildModel? = projectBuildModel.getModuleBuildModel(project.findModule("app"))
    assertThat(moduleModel).isNotNull()
    WriteCommandAction.runWriteCommandAction(project) {
      change.invoke(projectBuildModel, moduleModel!!)

      projectBuildModel.applyChanges()
      moduleModel.applyChanges()
    }
    assert.invoke()
  }
}