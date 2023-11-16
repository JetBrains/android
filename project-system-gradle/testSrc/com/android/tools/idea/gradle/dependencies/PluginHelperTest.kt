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
package com.android.tools.idea.gradle.dependencies

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.getTextForFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PluginHelperTest: AndroidGradleTestCase() {

  @Test
  fun testSimpleAddWithCatalog() {
    doTest(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG,
           { moduleModel, helper, _ ->
             helper.addPlugin("com.example.libs", "1.0", moduleModel)
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .contains("{ id = \"com.example.libs\", version.ref = \"")
             assertThat(project.getTextForFile("app/build.gradle"))
               .contains(" alias(libs.plugins.exampleLibs) apply true")

             assertThat(project.getTextForFile("build.gradle"))
               .contains(" alias(libs.plugins.exampleLibs) apply false")
           })
  }

  @Test
  fun testSimpleAddWithCatalogPickExistingDeclaration() {
    doTest(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG,
           { moduleModel, helper, projectBuildModel ->

             val catalogModel = projectBuildModel.versionCatalogsModel.getVersionCatalogModel("libs")!!
             catalogModel.pluginDeclarations().addDeclaration("libsPlugin", "com.example.libs:1.0")

             helper.addPlugin("com.example.libs", "1.0", moduleModel)
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml")).doesNotContain("exampleLibs")
             assertThat(project.getTextForFile("app/build.gradle"))
               .contains("alias(libs.plugins.libsPlugin) apply true")

             assertThat(project.getTextForFile("build.gradle"))
               .contains("alias(libs.plugins.libsPlugin) apply false")
           })
  }

  @Test
  fun testSimpleAddWithCatalogIgnoreExistingDeclaration() {
    doTest(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG,
           { moduleModel, helper, projectBuildModel ->

             val catalogModel = projectBuildModel.versionCatalogsModel.getVersionCatalogModel("libs")!!
             catalogModel.pluginDeclarations().addDeclaration("libsPlugin", "com.example.libs:0.5")

             helper.addPlugin("com.example.libs", "1.0", moduleModel)
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .contains("exampleLibs = { id = \"com.example.libs\", version.ref = \"")
             assertThat(project.getTextForFile("app/build.gradle"))
               .contains("alias(libs.plugins.exampleLibs) apply true")
             assertThat(project.getTextForFile("build.gradle"))
               .contains("alias(libs.plugins.exampleLibs) apply false")
           })
  }

  @Test
  fun testAddCorePluginWithCatalog() {
    doTest(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG,
           { moduleModel, helper, _ ->
             helper.addCorePlugin("application", moduleModel)
           },
           {
             assertThat(project.getTextForFile("gradle/libs.versions.toml"))
               .doesNotContain("\"application\"")
             assertThat(project.getTextForFile("app/build.gradle"))
               .contains("id 'application'")
           })
  }

  @Test
  fun testSimpleAddNoCatalog() {
    doTest(TestProjectPaths.SIMPLE_APPLICATION,
           { moduleModel, helper, _ ->
             helper.addPlugin("com.example.libs", "1.0", moduleModel)
           },
           {
             assertThat(project.getTextForFile("app/build.gradle"))
               .contains("id 'com.example.libs' apply true")
             assertThat(project.getTextForFile("build.gradle"))
               .contains("id 'com.example.libs' version '1.0' apply false")
           })
  }

  private fun doTest(projectPath: String,
                     change: (model: GradleBuildModel, helper: PluginsHelper, projectBuildModel: ProjectBuildModel) -> Unit,
                     assert: () -> Unit) {
    loadProject(projectPath)
    val projectBuildModel = ProjectBuildModel.get(project)
    val moduleModel: GradleBuildModel? = projectBuildModel.getModuleBuildModel(project.findModule("app"))
    assertThat(moduleModel).isNotNull()
    val helper = PluginsHelper(projectBuildModel)
    change.invoke(moduleModel!!, helper, projectBuildModel)
    WriteCommandAction.runWriteCommandAction(project) {
      projectBuildModel.applyChanges()
      moduleModel.applyChanges()
    }
    assert.invoke()
  }

}